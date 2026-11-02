package com.jlptstudy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.SessionCards
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.srs.SessionPlanner
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

data class LevelProgress(val level: JlptLevel, val learnedWords: Int, val totalWords: Int)

/** architecture.md 7절 S02. */
data class HomeUiState(
    val status: HomeStatus = HomeStatus.LOADING,
    val dueCount: Int = 0,
    val newTodayCount: Int = 0,
    val newLimit: Int = 10,
    val targetLevels: Set<JlptLevel> = emptySet(),
    val levelProgress: List<LevelProgress> = emptyList(),
    val nextDueAtEpochMs: Long? = null,
    val nextDueCount: Int = 0,
    val isSampleContent: Boolean = false,
    val showFirstNotice: Boolean = false,
    val firstNotice: NoticeUi? = null,
)

private const val EXTRA_NEW_STEP = 5

/**
 * 홈 수치는 SessionCards.load 결과 하나로만 센다(Review 대기열과 같은 집합, architecture.md 8.2).
 * 화면이 다시 보일 때(세션에서 돌아옴) refresh()로 다시 계산한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val study: StudyRepository,
    private val content: ContentRepository,
    private val attribution: AttributionRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private val sessionCards = SessionCards(study, content)
    private val refreshTick = MutableStateFlow(0)
    private var notice: NoticeUi? = null

    val state: StateFlow<HomeUiState> = combine(study.settings(), refreshTick) { s, _ -> s }
        .mapLatest { s ->
            try { compute(s) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        .filterNotNull() // 읽기 실패: 직전 값을 그대로 둔다(명세에 ERROR 상태 없음)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() = refreshTick.update { it + 1 }

    /** "새 카드 5장 더": 오늘 한도만 +5(new_cards_per_day는 그대로). */
    fun moreNewCards() {
        viewModelScope.launch {
            try {
                val s = study.settings().first()
                val today = StudyDayRule(zone(), s.dayBoundaryMinutes).studyDayOf(clock.instant())
                study.updateSettings {
                    it.copy(
                        extraNewCards = (if (it.extraNewCardsStudyDay == today) it.extraNewCards else 0) + EXTRA_NEW_STEP,
                        extraNewCardsStudyDay = today,
                    )
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* 저장 실패: 홈 수치가 그대로라 다시 누를 수 있다 */ }
        }
    }

    fun dismissFirstNotice() {
        viewModelScope.launch {
            try { study.updateSettings { it.copy(firstNoticeDismissed = true) } } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

    private suspend fun firstNotice(): NoticeUi? {
        notice?.let { return it }
        return try { attribution.load().firstNotice.also { notice = it } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    private suspend fun compute(s: com.jlptstudy.app.data.study.UserSettings): HomeUiState {
        val meta = content.meta()
        val rule = StudyDayRule(zone(), s.dayBoundaryMinutes)
        val now = clock.instant()
        val today = rule.studyDayOf(now)
        val base = HomeUiState(
            newLimit = s.newLimit(today), targetLevels = s.targetLevels, isSampleContent = meta.kind == "sample",
            showFirstNotice = !s.firstNoticeDismissed, firstNotice = firstNotice(),
        )
        if (s.targetLevels.isEmpty()) return base.copy(status = HomeStatus.NO_LEVEL_SELECTED)
        val levels = s.targetLevels intersect meta.levels
        if (levels.isEmpty()) return base.copy(status = HomeStatus.NO_CONTENT_FOR_LEVELS)

        val existing = sessionCards.load(s.enabledKinds, levels)
        val dueIds = SessionPlanner.plan(existing, emptyList(), now, s.learnAheadMinutes, rule).toHashSet()
        val candidates = content.newCardOrder(levels, s.enabledKinds).map { it.cardId }
        val started = study.startedCardIds()
        val introducedToday = study.introducedToday(rule.startOfStudyDay(today).toEpochMilli(), rule.startOfStudyDay(today + 1).toEpochMilli())
        val newToday = SessionPlanner.selectNew(candidates, started, introducedToday, base.newLimit).size
        val left = SessionPlanner.selectNew(candidates, started, introducedToday, Int.MAX_VALUE).size

        val status = when {
            dueIds.isNotEmpty() || newToday > 0 -> HomeStatus.READY
            left > 0 -> HomeStatus.NEW_LIMIT_REACHED
            else -> HomeStatus.ALL_DONE
        }
        var nextAt: Long? = null
        var nextCount = 0
        if (status != HomeStatus.READY) {
            val later = existing.filter { it.cardId !in dueIds }
            nextAt = later.minOfOrNull { it.dueAtEpochMs }
            nextAt?.let { at ->
                val dayEnd = rule.startOfStudyDay(rule.studyDayOf(Instant.ofEpochMilli(at)) + 1).toEpochMilli()
                nextCount = later.count { it.dueAtEpochMs < dayEnd }
            }
        }
        val counts = content.levelCounts()
        val learnedByLevel = content.cardLevels(started.filter { it.startsWith("vr:") }).values.groupingBy { it }.eachCount()
        return base.copy(
            status = status, dueCount = dueIds.size, newTodayCount = newToday,
            levelProgress = JlptLevel.entries.filter { it in levels }
                .map { LevelProgress(it, learnedByLevel[it] ?: 0, counts[it]?.words ?: 0) },
            nextDueAtEpochMs = nextAt, nextDueCount = nextCount,
        )
    }
}
