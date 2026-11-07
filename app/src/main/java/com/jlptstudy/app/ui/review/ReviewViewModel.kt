package com.jlptstudy.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.SessionCards
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.ui.home.HomeStatus
import com.jlptstudy.app.ui.theme.InputGuardMs
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.IntervalPreview
import com.jlptstudy.srs.Phase
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.ScheduleResult
import com.jlptstudy.srs.Scheduler
import com.jlptstudy.srs.SessionPlanner
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

enum class ReviewStatus { LOADING, READY, EMPTY }

data class ReviewUiState(
    val status: ReviewStatus = ReviewStatus.LOADING,
    val emptyReason: HomeStatus? = null,
    val card: ReviewCardUi? = null,
    val revealed: Boolean = false,
    val previews: Map<Rating, IntervalPreview> = emptyMap(),
    val saving: Boolean = false,
    val saveFailed: Rating? = null,
    /** 저장 실패가 날 때마다 늘어난다: 같은 평가로 연속 실패해도 화면이 알림을 다시 띄우는 키. */
    val saveFailCount: Int = 0,
    val remainingReview: Int = 0,
    val remainingNew: Int = 0,
    val reviewedInSession: Int = 0,
    val newInSession: Int = 0,
    val canUndo: Boolean = false,
    val finished: Boolean = false,
    val newCardsLeftAfterSession: Int = 0,
    /** EMPTY일 때 S02와 같은 문구용: 다음 복습 시각·장수(없으면 null)와 오늘 새 카드 한도. */
    val nextDueAtEpochMs: Long? = null,
    val nextDueCount: Int = 0,
    val newLimit: Int = 0,
)

/** 세션 화면의 후리가나 설정(앞면은 설정에서만, 뒷면은 상단 토글로 바꾼다). */
data class ReviewPrefs(val frontFurigana: Boolean = false, val bodyFurigana: Boolean = true)

internal const val EXTRA_NEW_STEP = 5

/**
 * S03·S04 상태(architecture.md 7·8.1절). 순서는 SessionPlanner가 정하고, 평가·되돌리기마다 now로 plan을 다시 부른다.
 * 300 ms 입력 무시(ui-design 3.6)의 기준 시각은 이 클래스의 private 필드다.
 */
class ReviewViewModel(
    private val study: StudyRepository,
    private val content: ContentRepository,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private val sessionCards = SessionCards(study, content)
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    val prefs: StateFlow<ReviewPrefs> = study.settings()
        .map { ReviewPrefs(it.frontFurigana, it.bodyFurigana) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewPrefs())

    // 세션 값(begin이 정한다)
    private var startLogId = 0L
    private var kinds: Set<CardKind> = emptySet()
    private var levels: Set<JlptLevel> = emptySet()
    private var candidates: List<String> = emptyList()
    private val history = ArrayDeque<Boolean>() // 이 세션의 평가마다 "새 카드였는가"(되돌리기 때 세션 카운트를 되돌린다)
    private var reviewed = 0
    private var introduced = 0

    // 지금 카드
    private var current: CardState? = null
    private var config: SrsConfig? = null
    private var pending: ScheduleResult? = null // 저장 실패한 평가의 처음 계산 결과
    private var lastTransitionMs = 0L
    private var busy = false

    init { viewModelScope.launch { safely { begin() } } }

    fun reveal() {
        val st = _state.value
        val card = current ?: return
        if (st.card == null || st.revealed || busy || sinceTransition() < InputGuardMs) return
        lastTransitionMs = clock.millis()
        _state.update { it.copy(revealed = true, previews = previewsOf(card)) }
    }

    fun rate(rating: Rating) {
        val st = _state.value
        val card = current ?: return
        if (st.card == null || !st.revealed || busy) return
        // 저장 실패 뒤의 재평가는 기다린 뒤의 입력이므로 막지 않는다.
        if (st.saveFailed == null && sinceTransition() < InputGuardMs) return
        val result = pending?.takeIf { st.saveFailed == rating }
            ?: scheduler.schedule(card, rating, clock.instant(), config ?: return)
        persist(result, rating)
    }

    fun retry() { _state.value.saveFailed?.let(::rate) }

    fun undo() {
        if (busy || history.isEmpty() || _state.value.finished) return
        busy = true
        viewModelScope.launch {
            try {
                safely {
                    val undone = study.undoLast(startLogId)
                    if (undone != null) {
                        if (history.removeLast()) introduced-- else reviewed--
                        advance(pinned = undone.cardId, revealed = true)
                    }
                }
            } finally { busy = false }
        }
    }

    /** S04 "새 카드 5장 더 학습": 오늘 한도 +5(설정값은 그대로)를 저장하고 새 세션을 시작한다. */
    fun studyMore() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                safely {
                    val s = study.settings().first()
                    val today = StudyDayRule(zone(), s.dayBoundaryMinutes).studyDayOf(clock.instant())
                    study.updateSettings {
                        it.copy(
                            extraNewCards = (if (it.extraNewCardsStudyDay == today) it.extraNewCards else 0) + EXTRA_NEW_STEP,
                            extraNewCardsStudyDay = today,
                        )
                    }
                    _state.value = ReviewUiState()
                    begin()
                }
            } finally { busy = false }
        }
    }

    /** 후리가나 토글(상단 바). */
    fun toggleFurigana() {
        // 지금 보이는 면의 설정을 바꾼다(앞면 = frontFurigana, 뒷면 = bodyFurigana).
        val front = !_state.value.revealed && _state.value.card !is ReviewCardUi.Grammar // 문법 예문은 앞면에서도 body_furigana
        viewModelScope.launch {
            runCatching {
                study.updateSettings { if (front) it.copy(frontFurigana = !it.frontFurigana) else it.copy(bodyFurigana = !it.bodyFurigana) }
            }
        }
    }

    private fun persist(result: ScheduleResult, rating: Rating) {
        busy = true
        _state.update { it.copy(saveFailed = null) } // 재시도가 또 실패해도 null→평가 변화로 스낵바가 다시 뜬다
        viewModelScope.launch {
            val slow = launch { delay(InputGuardMs); _state.update { it.copy(saving = true) } }
            try {
                try {
                    study.applyReview(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    pending = result
                    _state.update { it.copy(saving = false, saveFailed = rating, saveFailCount = it.saveFailCount + 1) }
                    return@launch
                } finally { slow.cancel() }
                pending = null
                val wasNew = result.record.phaseBefore == Phase.NEW
                history.addLast(wasNew)
                if (wasNew) introduced++ else reviewed++
                current = null // 다음 카드를 못 읽어도 낡은 상태로 다시 저장하지 않는다
                _state.update { it.copy(saving = false) }
                safely { advance(pinned = null, revealed = false) }
                if (current == null && _state.value.card != null) { // 다음 카드 읽기 실패: 세션을 마친 것으로 둔다
                    _state.value = ReviewUiState(status = ReviewStatus.READY, finished = true, reviewedInSession = reviewed, newInSession = introduced)
                }
            } finally { busy = false }
        }
    }

    private suspend fun begin() {
        val s = study.settings().first()
        levels = s.targetLevels intersect content.meta().levels
        kinds = s.enabledKinds
        startLogId = study.maxLogId()
        candidates = content.newCardOrder(levels, kinds).map { it.cardId }
        history.clear(); reviewed = 0; introduced = 0; pending = null
        advance(pinned = null, revealed = false, initial = true)
    }

    /** 대기열을 now로 다시 만들어 첫 카드를 보인다. 한도·오늘 도입 수도 이때 새로 구하므로 학습일 경계를 넘으면 새 학습일 기준이 된다. */
    private suspend fun advance(pinned: String?, revealed: Boolean, initial: Boolean = false) {
        val s = study.settings().first()
        val rule = StudyDayRule(zone(), s.dayBoundaryMinutes)
        val now = clock.instant()
        val cfg = SrsConfig(desiredRetention = s.desiredRetention, maximumIntervalDays = s.maxIntervalDays, studyDay = rule)
        val today = rule.studyDayOf(now)
        val existing = sessionCards.load(kinds, levels)
        val started = study.startedCardIds()
        val introducedToday = study.introducedToday(
            rule.startOfStudyDay(today).toEpochMilli(), rule.startOfStudyDay(today + 1).toEpochMilli(),
        )
        val newIds = SessionPlanner.selectNew(candidates, started, introducedToday, s.newLimit(today))
        val plan = SessionPlanner.plan(existing, newIds, now, s.learnAheadMinutes, rule, pinned)

        var ids = plan
        var card: ReviewCardUi? = null
        while (card == null && ids.isNotEmpty()) {
            card = content.cardContents(listOf(ids.first()))[ids.first()]
            if (card == null) ids = ids.drop(1) // 콘텐츠에서 사라진 카드는 건너뛴다(기록은 지우지 않는다)
        }
        config = cfg
        if (card == null) {
            // 오늘 한도와 무관하게 아직 시작하지 않은 새 카드 수(같은 콘텐츠의 vr·vp는 한 장으로 센다)
            val left = SessionPlanner.selectNew(candidates, started, introducedToday, Int.MAX_VALUE).size
            current = null
            lastTransitionMs = clock.millis()
            _state.value = if (initial) {
                val next = existing.minOfOrNull { it.dueAtEpochMs }
                val nextDayEnd = next?.let { rule.startOfStudyDay(rule.studyDayOf(Instant.ofEpochMilli(it)) + 1).toEpochMilli() }
                ReviewUiState(
                    status = ReviewStatus.EMPTY, emptyReason = if (left > 0) HomeStatus.NEW_LIMIT_REACHED else HomeStatus.ALL_DONE,
                    nextDueAtEpochMs = next, nextDueCount = nextDayEnd?.let { end -> existing.count { it.dueAtEpochMs < end } } ?: 0,
                    newLimit = s.newLimit(today),
                )
            } else {
                ReviewUiState(
                    status = ReviewStatus.READY, finished = true, newCardsLeftAfterSession = left,
                    reviewedInSession = reviewed, newInSession = introduced,
                )
            }
            return
        }
        val newSet = newIds.toHashSet()
        val remainingNew = ids.count { it in newSet }
        val state = existing.firstOrNull { it.cardId == card.cardId }
            ?: CardState.new(card.cardId, now, scheduler.version)
        current = state
        lastTransitionMs = clock.millis()
        _state.value = ReviewUiState(
            status = ReviewStatus.READY, card = card, revealed = revealed,
            previews = previewsOf(state, cfg, now),
            remainingReview = ids.size - remainingNew, remainingNew = remainingNew,
            reviewedInSession = reviewed, newInSession = introduced, canUndo = history.isNotEmpty(),
        )
    }

    private fun previewsOf(card: CardState, cfg: SrsConfig? = config, now: java.time.Instant = clock.instant()): Map<Rating, IntervalPreview> =
        if (cfg == null) emptyMap() else scheduler.preview(card, now, cfg)

    private fun sinceTransition() = clock.millis() - lastTransitionMs

    /** 읽기 실패로 앱이 죽지 않게 한다. DB 열기 실패는 S90이 맡으므로 여기서는 빈 세션으로 둔다(ERROR 상태는 명세에 없다). */
    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            if (_state.value.card == null && !_state.value.finished) {
                _state.value = ReviewUiState(status = ReviewStatus.EMPTY, emptyReason = HomeStatus.ALL_DONE)
            }
        }
    }
}

