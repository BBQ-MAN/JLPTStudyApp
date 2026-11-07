package com.jlptstudy.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.LoadedAttempt
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.listening.ScriptSpeech
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * S13 연습 풀이와 S14 모의고사 진행(mode로 나눔).
 * 연습: 고르면 saveAnswer가 끝난 뒤에만 FEEDBACK이 된다. 모의고사: 피드백 없이 선택만 저장하고 제출 전까지 바꿀 수 있다.
 * current_index는 question_order 안 위치다(빠진 문항을 가리키면 그 다음 남은 문항에서 잇는다).
 * 경과 시간은 onStart~onStop(화면이 보이는 동안)만 주입한 Clock으로 더한다(UI-U05 A).
 */
class QuizViewModel(
    private val attemptId: Long,
    private val loader: QuizSessionLoader,
    private val quiz: QuizRepository,
    private val study: StudyRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(QuizUiState(attemptId = attemptId))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var loaded: LoadedAttempt? = null
    private var shuffled: List<ShuffledQuestion> = emptyList()
    private val picks = mutableMapOf<Int, Int>() // EXAM: 문항 순번 → 저장된 섞은 뒤 색인
    private var lastTransitionMs = 0L
    private var saveJob: Job? = null
    private var finishPending = false
    private var exiting = false
    private var elapsedBase = 0L
    private var visibleSince: Long? = null

    init {
        viewModelScope.launch { study.settings().map { it.bodyFurigana }.collect { v -> _state.update { it.copy(showRuby = v) } } }
        viewModelScope.launch { load() }
    }

    private val exam get() = loaded?.meta?.mode == QuizMode.EXAM

    private suspend fun load() {
        val l = try { loader.load(attemptId) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("Quiz", "시도 읽기 실패", e); null
        }
        val mode = l?.meta?.mode
        if (l == null || l.items.isEmpty() || mode == null) {
            // 재개할 문항이 하나도 없는 모의고사는 닫는다: S12 이어서 풀기에 다시 잡히지 않게(ui-design 7절)
            if (mode == QuizMode.EXAM && l?.record?.finishedAtEpochMs == null) safe { quiz.finish(attemptId, clock.millis()) }
            _state.update { it.copy(status = QuizStatus.EMPTY, mode = mode ?: it.mode) }
            return
        }
        // 청해 시도는 M23(S16): 그때까지는 닫기 동선이 있는 EMPTY로 보낸다.
        if (mode == QuizMode.LISTENING) { _state.update { it.copy(mode = mode, status = QuizStatus.EMPTY) }; return }
        loaded = l
        shuffled = l.items.map { Quiz.shuffle(it.toQuestion(), l.record.seed) }
        l.items.forEachIndexed { i, q -> l.answers[q.id]?.let { a -> picks[i] = shuffled[i].toShown(a.chosenIndex) } }
        val idx = l.items.indices.firstOrNull { orderPos(it) >= l.record.currentIndex } ?: l.items.lastIndex
        var starts = emptyList<Pair<Int, String>>()
        var limit: Long? = null
        if (mode == QuizMode.EXAM) {
            elapsedBase = l.record.elapsedMs
            val level = l.meta!!.level
            val template = safe { loader.content.examTemplate(level) } // 템플릿을 읽지 못해도 풀이는 잇는다(기준 시간·영역 구분만 빠짐)
            if (template != null) {
                val sections = template.examSections()
                val secOf = l.items.map { sections.indexOfKey(it.section, it.type) }
                starts = sections.indices.mapNotNull { k -> secOf.indexOf(k).takeIf { it >= 0 }?.let { it to sections[k].titleKo } }.sortedBy { it.first }
                limit = template.limitMs()
            }
        }
        lastTransitionMs = clock.millis()
        _state.update {
            it.copy(
                status = if (mode == QuizMode.PRACTICE && idx in picks) QuizStatus.FEEDBACK else QuizStatus.ANSWERING, mode = mode,
                index = idx, total = l.items.size, question = questionUi(idx), chosenIndex = picks[idx], answeredIndices = picks.keys.toSet(),
                unansweredCount = l.items.size - picks.size, sectionStarts = starts.map { p -> p.first }, sectionTitles = starts.map { p -> p.second },
                limitMs = limit, elapsedMs = if (mode == QuizMode.EXAM) elapsedNow() else 0,
            )
        }
    }

    private suspend fun <T> safe(f: suspend () -> T): T? =
        try { f() } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.e("Quiz", "읽기·저장 실패", e); null }

    /** 화면 순번 → question_order 위치(current_index 저장 값). */
    private fun orderPos(i: Int): Int = loaded!!.record.questionOrder.indexOf(loaded!!.items[i].id)

    private fun questionUi(i: Int): QuizQuestionUi = loaded!!.items[i].toUi(shuffled[i])

    private fun elapsedNow(): Long = if (!exam) 0L else elapsedBase + (visibleSince?.let { clock.millis() - it } ?: 0L) // 연습은 시간을 재지 않는다

    /** 화면이 보이기 시작함(ON_START): 시간이 흐른다. */
    fun onStart() {
        if (visibleSince == null) visibleSince = clock.millis()
        tick()
    }

    /** 화면이 가려짐(ON_STOP, 홈 키·전화·화면 꺼짐): 확인 없이 그 시점까지 저장하고 시간을 멈춘다. */
    fun onStop() {
        elapsedBase = elapsedNow()
        visibleSince = null
        tick()
        if (exam && _state.value.status != QuizStatus.SUBMITTED) saveProgress()
    }

    /** 화면이 1초마다 부른다(타이머 표시). */
    fun tick() { if (exam) _state.update { it.copy(elapsedMs = elapsedNow()) } }

    private fun saveProgress(): Job {
        val pos = orderPos(_state.value.index)
        val ms = elapsedNow()
        return viewModelScope.launch { safe { quiz.saveProgress(attemptId, pos, ms) } }
    }

    /** 섞은 뒤 색인. 연습은 한 번 고르면 바꿀 수 없고, 모의고사는 제출 전까지 바꿀 수 있다(바꿀 때마다 저장). */
    fun choose(shownIndex: Int) {
        val s = _state.value
        if (s.status != QuizStatus.ANSWERING || saveJob?.isActive == true || shownIndex !in 0..3) return
        if (if (exam) shownIndex == s.chosenIndex && !s.saveFailed else s.chosenIndex != null) return
        _state.update { it.copy(chosenIndex = shownIndex, saveFailed = false, finishFailed = false) }
        finishPending = false
        save(shownIndex)
    }

    private fun save(shownIndex: Int) {
        val index = _state.value.index
        val q = loaded!!.items[index]
        val original = shuffled[index].toOriginal(shownIndex)
        saveJob = viewModelScope.launch {
            val slow = launch { delay(LoadingDelayMs); _state.update { it.copy(saving = true) } }
            try {
                quiz.saveAnswer(QuizAnswerRecord(attemptId, q.id, original, original == q.answerIndex, clock.millis()))
                lastTransitionMs = clock.millis()
                picks[index] = shownIndex
                _state.update {
                    it.copy(
                        status = if (exam) it.status else QuizStatus.FEEDBACK, saving = false,
                        answeredIndices = picks.keys.toSet(), unansweredCount = it.total - picks.size,
                    )
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Quiz", "답 저장 실패", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            } finally { slow.cancel() }
        }
    }

    /** 연습: 피드백 뒤 "다음" / 마지막이면 "결과 보기"(finish 뒤 SUBMITTED), 답 직후 300 ms는 무시. 모의고사: 다음 문항. */
    fun next() {
        val s = _state.value
        if (exam) { goTo(s.index + 1); return }
        if (s.status != QuizStatus.FEEDBACK || saveJob?.isActive == true || clock.millis() - lastTransitionMs < InputGuardMs) return
        if (s.index + 1 >= s.total) { submit(); return }
        val i = s.index + 1
        lastTransitionMs = clock.millis()
        _state.update { it.copy(index = i, question = questionUi(i), chosenIndex = null, status = QuizStatus.ANSWERING, saveFailed = false) }
        saveProgress()
    }

    fun prev() { if (exam) goTo(_state.value.index - 1) }

    /** 모의고사 번호 칩·이전·다음. 답 저장이 끝나기 전(실패 포함)에는 다른 문항으로 가지 않는다. */
    fun goTo(i: Int) {
        val s = _state.value
        if (!exam || s.status != QuizStatus.ANSWERING || saveJob?.isActive == true || (s.saveFailed && !s.finishFailed) || i !in 0 until s.total || i == s.index) return
        show(i)
        saveProgress()
    }

    private fun show(i: Int) {
        _state.update { it.copy(index = i, question = questionUi(i), chosenIndex = picks[i], elapsedMs = elapsedNow()) }
    }

    /** 모의고사 "제출": 미응답이 있으면 CONFIRM_SUBMIT, 없으면 바로 제출. */
    fun requestSubmit() {
        val s = _state.value
        if (!exam || s.status != QuizStatus.ANSWERING || saveJob?.isActive == true || (s.saveFailed && !s.finishFailed)) return
        if (s.unansweredCount > 0) _state.update { it.copy(status = QuizStatus.CONFIRM_SUBMIT) } else submit()
    }

    /** "돌아가서 풀기": 제출하지 않고 첫 미응답 문항으로. */
    fun cancelSubmit() {
        if (_state.value.status != QuizStatus.CONFIRM_SUBMIT) return
        _state.update { it.copy(status = QuizStatus.ANSWERING) }
        loaded!!.items.indices.firstOrNull { it !in picks }?.let { if (it != _state.value.index) { show(it); saveProgress() } }
    }

    /** "그대로 제출". */
    fun confirmSubmit() {
        if (_state.value.status != QuizStatus.CONFIRM_SUBMIT) return
        _state.update { it.copy(status = QuizStatus.ANSWERING) }
        submit()
    }

    private fun submit() {
        finishPending = true
        saveJob = viewModelScope.launch {
            try {
                // 결과 화면의 걸린 시간: finish 뒤에는 saveProgress가 무시되므로 먼저 저장한다
                if (exam) quiz.saveProgress(attemptId, orderPos(_state.value.index), elapsedNow())
                quiz.finish(attemptId, clock.millis())
                finishPending = false
                _state.update { it.copy(status = QuizStatus.SUBMITTED, saveFailed = false, finishFailed = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Quiz", "마무리 실패", e)
                _state.update { it.copy(saveFailed = true, finishFailed = true) }
            }
        }
    }

    /** 스낵바 "다시 시도": 같은 답(또는 같은 마무리)을 다시 저장한다. */
    fun retry() {
        val s = _state.value
        if (!s.saveFailed || saveJob?.isActive == true) return
        _state.update { it.copy(saveFailed = false, finishFailed = false) }
        if (finishPending) submit() else s.chosenIndex?.let(::save)
    }

    /**
     * 뒤로·닫기. 모의고사 진행 중이면 CONFIRM_EXIT만 띄운다.
     * 연습은 확인 없이 finish 후 onDone(S12). finish가 실패해도 나간다(연습은 재개하지 않으므로 잃는 것이 없다).
     */
    fun exit(onDone: () -> Unit) {
        val s = _state.value
        if (exam && s.status == QuizStatus.ANSWERING) { _state.update { it.copy(status = QuizStatus.CONFIRM_EXIT) }; return }
        if (exiting) return // 닫기와 뒤로가 연달아 와도 onDone은 한 번만(두 번이면 S12 탭까지 팝된다)
        exiting = true
        viewModelScope.launch {
            if (s.status != QuizStatus.SUBMITTED && s.status != QuizStatus.LOADING && s.mode == QuizMode.PRACTICE) {
                safe { quiz.finish(attemptId, clock.millis()) }
            }
            onDone()
        }
    }

    /** CONFIRM_EXIT "계속 풀기". */
    fun continueExam() { if (_state.value.status == QuizStatus.CONFIRM_EXIT) _state.update { it.copy(status = QuizStatus.ANSWERING) } }

    /** CONFIRM_EXIT "멈추고 나가기": 위치·시간을 저장하고 시간을 멈춘 뒤 S12로. 시도는 미완료로 남는다. */
    fun stopAndExit(onDone: () -> Unit) {
        if (_state.value.status != QuizStatus.CONFIRM_EXIT || exiting) return
        exiting = true
        elapsedBase = elapsedNow()
        visibleSince = null
        _state.update { it.copy(status = QuizStatus.ANSWERING, elapsedMs = elapsedBase) }
        viewModelScope.launch { saveProgress().join(); onDone() }
    }

    fun toggleFurigana() { viewModelScope.launch { study.updateSettings { it.copy(bodyFurigana = !it.bodyFurigana) } } }
}

/** 문항 행 + 섞은 결과 → 화면 문항(S13·S15가 같은 모양으로 쓴다). */
internal fun QuizContent.toUi(s: ShuffledQuestion): QuizQuestionUi = QuizQuestionUi(
    id, section, type, target, Furigana.parse(stem), passage?.let(Furigana::parse),
    s.choices.map(Furigana::parse), s.answerIndex, explanationKo,
    List(4) { shown -> whyWrongKo.getOrNull(s.toOriginal(shown)) }, targets, provenance, needsReview,
    lines?.let { ls ->
        val speech = ScriptSpeech.lines(ls)
        ls.mapIndexed { i, l -> ListeningLineUi(speech[i].voice, Furigana.parse(l.text), speech[i].text) }
    },
)
