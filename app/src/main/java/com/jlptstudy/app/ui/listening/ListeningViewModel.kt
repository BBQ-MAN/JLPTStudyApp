package com.jlptstudy.app.ui.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.data.LoadedAttempt
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.speech.Speaker
import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.ui.practice.toUi
import com.jlptstudy.app.ui.theme.InputGuardMs
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.ShuffledQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/** TalkBack이 켜져 있을 때 재생 버튼 뒤 음성을 늦추는 시간("재생 중" 알림이 음성 첫머리를 덮지 않게, ui-design 5.8). */
const val TalkBackSpeakDelayMs = 1_000L
const val SlowRate = 0.8f

/**
 * S16 청해(architecture.md 9.3). 시도 읽기·저장은 S13과 같은 규칙(연습: 고르면 저장 뒤 FEEDBACK, 재개 없음).
 * Speaker는 앱 범위 객체가 아니다: 생성 때 하나 만들고, ON_START에서 MissingJapaneseData·NoEngine·Error면 shutdown 후 새로 만든다(9.3.2).
 * 자동 재생하지 않는다. 화면을 떠나거나 ON_STOP이면 stop.
 */
class ListeningViewModel(
    private val attemptId: Long,
    private val loader: QuizSessionLoader,
    private val quiz: QuizRepository,
    private val study: StudyRepository,
    private val attribution: AttributionRepository,
    private val clock: Clock,
    private val speakerFactory: () -> Speaker,
    private val touchExploration: () -> Boolean = { false },
) : ViewModel() {
    private val _state = MutableStateFlow(ListeningUiState(attemptId = attemptId))
    val state: StateFlow<ListeningUiState> = _state.asStateFlow()

    private var speaker: Speaker = speakerFactory()
    private var lastSpeaker: SpeakerState = speaker.state.value
    private var speakerJob: Job? = null
    private var pendingId: String? = null // TalkBack 지연 중: 화면은 이미 "재생 중"
    private var pendingJob: Job? = null

    private var loaded: LoadedAttempt? = null
    private var shuffled: List<ShuffledQuestion> = emptyList()
    private var scriptFirst = false
    private var saveJob: Job? = null
    private var lastTransitionMs = 0L
    private var finishPending = false
    private var lastSave: Pair<Int, Boolean>? = null
    private var exiting = false

    init {
        bind()
        viewModelScope.launch { study.settings().collect { s -> _state.update { it.copy(showRuby = s.bodyFurigana) } } }
        viewModelScope.launch {
            val n = safe { attribution.load().notices.firstOrNull { it.id == "N-12" } } // 없거나 읽지 못하면 줄째 숨긴다
            _state.update { it.copy(audioNotice = n) }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun <T> safe(f: suspend () -> T): T? =
        try { f() } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.e("Listening", "읽기·저장 실패", e); null }

    private suspend fun load() {
        val l = safe { loader.load(attemptId) }
        if (l == null || l.items.isEmpty() || l.meta?.mode != QuizMode.LISTENING) {
            _state.update { it.copy(status = ListeningStatus.EMPTY) }
            return
        }
        scriptFirst = safe { study.settings().first().listeningScriptFirst } == true
        loaded = l
        shuffled = l.items.map { Quiz.shuffle(it.toQuestion(), l.record.seed) }
        // 새로 열 때는 current_index에서 잇는다(프로세스 재시작 뒤 같은 화면). 이미 답한 문항이면 FEEDBACK.
        val idx = l.items.indices.firstOrNull { orderPos(it) >= l.record.currentIndex } ?: l.items.lastIndex
        val answered = l.answers[l.items[idx].id]?.let { shuffled[idx].toShown(it.chosenIndex) }
        lastTransitionMs = clock.millis()
        _state.update { it.copy(total = l.items.size) }
        show(idx, answered)
    }

    private fun orderPos(i: Int): Int = loaded!!.record.questionOrder.indexOf(loaded!!.items[i].id)

    /** 문항을 보인다. 대본은 listening_script_first·NoEngine이면 처음부터 펼친다(그 경우도 대본으로 푼 것, UI-U13 A). */
    private fun show(i: Int, chosen: Int?) {
        val open = scriptFirst || lastSpeaker == SpeakerState.NoEngine
        _state.update {
            it.copy(
                status = if (chosen == null) ListeningStatus.ANSWERING else ListeningStatus.FEEDBACK, index = i,
                question = loaded!!.items[i].toUi(shuffled[i]), chosenIndex = chosen, scriptOpen = open, scriptSeen = open && chosen == null,
                speaker = shown(), externalOpenFailed = false, saveFailed = false, finishFailed = false, saving = false,
            )
        }
    }

    // --- Speaker ---------------------------------------------------------------

    private fun bind() {
        speakerJob?.cancel()
        val s = speaker
        speakerJob = viewModelScope.launch { s.state.collect(::onSpeaker) }
    }

    private fun shown(): SpeakerState = pendingId?.let { SpeakerState.Playing(it) } ?: lastSpeaker

    private fun onSpeaker(s: SpeakerState) {
        lastSpeaker = s
        _state.update {
            val noEngine = s == SpeakerState.NoEngine
            it.copy(
                speaker = shown(), externalOpenFailed = false,
                scriptOpen = it.scriptOpen || noEngine && (it.status == ListeningStatus.ANSWERING || it.status == ListeningStatus.FEEDBACK),
                scriptSeen = it.scriptSeen || noEngine && it.status == ListeningStatus.ANSWERING,
            )
        }
    }

    private fun cancelPending() {
        pendingJob?.cancel(); pendingJob = null; pendingId = null
    }

    /** 주 버튼 "재생"과 "처음부터". Ready·Playing에서만 동작한다. TalkBack이 켜져 있으면 1,000 ms 뒤에 speak. */
    fun play() {
        val s = _state.value
        val q = s.question ?: return
        if (s.status != ListeningStatus.ANSWERING && s.status != ListeningStatus.FEEDBACK) return
        if (lastSpeaker != SpeakerState.Ready && lastSpeaker !is SpeakerState.Playing) return
        val script = loaded?.items?.getOrNull(s.index)?.lines ?: return
        val lines = ScriptSpeech.lines(script)
        val rate = s.rate
        cancelPending()
        if (!touchExploration()) { speaker.speak(q.id, lines, rate); return }
        pendingId = q.id
        _state.update { it.copy(speaker = shown()) }
        val target = speaker
        pendingJob = viewModelScope.launch {
            delay(TalkBackSpeakDelayMs)
            pendingId = null; pendingJob = null
            target.speak(q.id, lines, rate)
            _state.update { it.copy(speaker = shown()) }
        }
    }

    /** 주 버튼 "정지"(재생 중), 문항 이동, 화면 이탈, ON_STOP. */
    fun stopPlayback() {
        cancelPending()
        speaker.stop()
        _state.update { it.copy(speaker = shown()) }
    }

    fun toggleRate() = _state.update { it.copy(rate = if (it.rate == 1.0f) SlowRate else 1.0f) } // 재생 중이면 다음 재생부터

    /** ON_START: 설치·설정 화면에서 돌아온 경우 포함. 사용할 수 없는 상태였으면 새 Speaker로 다시 확인한다(ON_RESUME은 쓰지 않는다). */
    fun onStart() {
        if (lastSpeaker == SpeakerState.MissingJapaneseData || lastSpeaker == SpeakerState.NoEngine || lastSpeaker == SpeakerState.Error) recreateSpeaker()
    }

    fun onStop() = stopPlayback()

    /** Error의 "다시 시도". */
    fun retrySpeaker() = recreateSpeaker()

    private fun recreateSpeaker() {
        cancelPending()
        speakerJob?.cancel()
        speaker.shutdown()
        speaker = speakerFactory()
        lastSpeaker = speaker.state.value
        _state.update { it.copy(speaker = shown(), externalOpenFailed = false) }
        bind()
    }

    /** 설치·설정 인텐트를 열었는가(startExternal 결과). false면 그 버튼 아래 안내 문구. */
    fun externalOpened(ok: Boolean) = _state.update { it.copy(externalOpenFailed = !ok) }

    // --- 대본 ------------------------------------------------------------------

    fun toggleScript() {
        _state.update {
            val open = !it.scriptOpen
            it.copy(scriptOpen = open, scriptSeen = it.scriptSeen || open && it.status == ListeningStatus.ANSWERING)
        }
    }

    /** "대본 보기" 버튼(MissingJapaneseData·NoEngine): 펼친다(이미 펼쳐져 있으면 그대로). */
    fun openScript() = _state.update {
        it.copy(scriptOpen = true, scriptSeen = it.scriptSeen || it.status == ListeningStatus.ANSWERING)
    }

    // --- 풀이 ------------------------------------------------------------------

    /** 섞은 뒤 색인. 한 번 고르면 바꿀 수 없다. script_shown = 답하기 전에 대본을 한 번이라도 펼쳤는가. */
    fun choose(shownIndex: Int) {
        val s = _state.value
        if (s.status != ListeningStatus.ANSWERING || saveJob?.isActive == true || s.chosenIndex != null || shownIndex !in 0..3) return
        _state.update { it.copy(chosenIndex = shownIndex, saveFailed = false, finishFailed = false) }
        finishPending = false
        stopPlayback() // 정답 알림(TalkBack)과 대본 음성이 겹치지 않게
        save(shownIndex, s.scriptSeen)
    }

    private fun save(shownIndex: Int, seen: Boolean) {
        lastSave = shownIndex to seen
        val index = _state.value.index
        val q = loaded!!.items[index]
        val original = shuffled[index].toOriginal(shownIndex)
        saveJob = viewModelScope.launch {
            val slow = launch { delay(LoadingDelayMs); _state.update { it.copy(saving = true) } }
            try {
                quiz.saveAnswer(QuizAnswerRecord(attemptId, q.id, original, original == q.answerIndex, clock.millis(), seen))
                lastTransitionMs = clock.millis()
                _state.update { it.copy(status = ListeningStatus.FEEDBACK, saving = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Listening", "답 저장 실패", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            } finally { slow.cancel() }
        }
    }

    /** "다음" / 마지막이면 "결과 보기"(finish 뒤 SUBMITTED). 답 직후 300 ms는 무시. */
    fun next() {
        val s = _state.value
        if (s.status != ListeningStatus.FEEDBACK || saveJob?.isActive == true || clock.millis() - lastTransitionMs < InputGuardMs) return
        stopPlayback()
        if (s.index + 1 >= s.total) { submit(); return }
        lastTransitionMs = clock.millis()
        show(s.index + 1, null)
        val pos = orderPos(s.index + 1)
        viewModelScope.launch { safe { quiz.saveProgress(attemptId, pos, 0) } }
    }

    private fun submit() {
        finishPending = true
        saveJob = viewModelScope.launch {
            try {
                quiz.finish(attemptId, clock.millis())
                finishPending = false
                _state.update { it.copy(status = ListeningStatus.SUBMITTED, saveFailed = false, finishFailed = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Listening", "마무리 실패", e)
                _state.update { it.copy(saveFailed = true, finishFailed = true) }
            }
        }
    }

    /** 스낵바 "다시 시도": 같은 답(또는 같은 마무리)을 다시 저장한다. */
    fun retry() {
        if (!_state.value.saveFailed || saveJob?.isActive == true) return
        _state.update { it.copy(saveFailed = false, finishFailed = false) }
        if (finishPending) submit() else lastSave?.let { (i, seen) -> save(i, seen) }
    }

    /** 뒤로·닫기: 확인 없이 정지하고 finish 후 onDone(S12). finish가 실패해도 나간다(청해는 재개하지 않는다). */
    fun exit(onDone: () -> Unit) {
        if (exiting) return
        exiting = true
        stopPlayback()
        val s = _state.value
        viewModelScope.launch {
            if (s.status != ListeningStatus.SUBMITTED && s.status != ListeningStatus.LOADING) safe { quiz.finish(attemptId, clock.millis()) }
            onDone()
        }
    }

    fun toggleFurigana() { viewModelScope.launch { study.updateSettings { it.copy(bodyFurigana = !it.bodyFurigana) } } }

    override fun onCleared() {
        cancelPending()
        speaker.stop()
        speaker.shutdown()
    }
}
