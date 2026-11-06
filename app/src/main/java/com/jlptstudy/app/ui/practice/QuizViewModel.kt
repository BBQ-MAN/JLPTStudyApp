package com.jlptstudy.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.LoadedAttempt
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.ui.common.Furigana
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
 * S13 연습 풀이. 고르면 saveAnswer가 끝난 뒤에만 FEEDBACK이 된다. 시험(EXAM) 모드는 M19c: 지금은 LOADING에 머문다.
 * 연습은 재개하지 않지만 프로세스가 죽은 뒤 라우트 인자로 다시 열리면 current_index부터 잇는다.
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
    private var lastTransitionMs = 0L
    private var saveJob: Job? = null
    private var finishPending = false
    private var exiting = false

    init {
        viewModelScope.launch { study.settings().map { it.bodyFurigana }.collect { v -> _state.update { it.copy(showRuby = v) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val l = try { loader.load(attemptId) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("Quiz", "시도 읽기 실패", e); null
        }
        val mode = l?.meta?.mode
        if (l == null || l.items.isEmpty() || mode == null) { _state.update { it.copy(status = QuizStatus.EMPTY) }; return }
        // 시험 모드 재개는 M19c: 그때까지는 LOADING에 가두지 않고 닫기 동선이 있는 EMPTY로 보낸다.
        if (mode != QuizMode.PRACTICE) { _state.update { it.copy(mode = mode, status = QuizStatus.EMPTY) }; return }
        loaded = l
        shuffled = l.items.map { Quiz.shuffle(it.toQuestion(), l.record.seed) }
        val idx = l.record.currentIndex.coerceIn(0, l.items.size - 1)
        val answered = l.items.indices.filter { l.items[it].id in l.answers }.toSet()
        val prior = l.answers[l.items[idx].id]
        lastTransitionMs = clock.millis()
        _state.update {
            it.copy(
                status = if (prior != null) QuizStatus.FEEDBACK else QuizStatus.ANSWERING, mode = mode, index = idx, total = l.items.size,
                question = questionUi(idx), chosenIndex = prior?.let { a -> shuffled[idx].toShown(a.chosenIndex) }, answeredIndices = answered,
            )
        }
    }

    private fun questionUi(i: Int): QuizQuestionUi {
        val q = loaded!!.items[i]
        val s = shuffled[i]
        return QuizQuestionUi(
            q.id, q.section, q.type, q.target, Furigana.parse(q.stem), q.passage?.let(Furigana::parse),
            s.choices.map(Furigana::parse), s.answerIndex, q.explanationKo,
            List(4) { shown -> q.whyWrongKo.getOrNull(s.toOriginal(shown)) }, q.targets, q.provenance, q.needsReview,
        )
    }

    /** 섞은 뒤 색인. 한 번 고르면 바꿀 수 없다. */
    fun choose(shownIndex: Int) {
        val s = _state.value
        if (s.status != QuizStatus.ANSWERING || saveJob?.isActive == true || s.chosenIndex != null || shownIndex !in 0..3) return
        _state.update { it.copy(chosenIndex = shownIndex, saveFailed = false) }
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
                _state.update { it.copy(status = QuizStatus.FEEDBACK, saving = false, answeredIndices = it.answeredIndices + index) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Quiz", "답 저장 실패", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            } finally { slow.cancel() }
        }
    }

    /** 피드백 뒤 "다음" / 마지막이면 "결과 보기"(finish 뒤 SUBMITTED). 답 직후 300 ms는 무시한다. */
    fun next() {
        val s = _state.value
        if (s.status != QuizStatus.FEEDBACK || saveJob?.isActive == true || clock.millis() - lastTransitionMs < InputGuardMs) return
        if (s.index + 1 >= s.total) { submit(); return }
        val i = s.index + 1
        lastTransitionMs = clock.millis()
        _state.update { it.copy(index = i, question = questionUi(i), chosenIndex = null, status = QuizStatus.ANSWERING, saveFailed = false) }
        viewModelScope.launch { try { quiz.saveProgress(attemptId, i, 0) } catch (e: CancellationException) { throw e } catch (_: Exception) {} }
    }

    private fun submit() {
        finishPending = true
        saveJob = viewModelScope.launch {
            try {
                quiz.finish(attemptId, clock.millis())
                finishPending = false
                _state.update { it.copy(status = QuizStatus.SUBMITTED, saveFailed = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Quiz", "마무리 실패", e)
                _state.update { it.copy(saveFailed = true) }
            }
        }
    }

    /** 스낵바 "다시 시도": 같은 답(또는 같은 마무리)을 다시 저장한다. */
    fun retry() {
        val s = _state.value
        if (!s.saveFailed || saveJob?.isActive == true) return
        _state.update { it.copy(saveFailed = false) }
        if (finishPending) submit() else s.chosenIndex?.let(::save)
    }

    /** 뒤로·닫기: 확인 없이 finish 후 onDone(S12). finish가 실패해도 나간다(연습은 재개하지 않으므로 잃는 것이 없다). */
    fun exit(onDone: () -> Unit) {
        if (exiting) return // 닫기와 뒤로가 연달아 와도 onDone은 한 번만(두 번이면 S12 탭까지 팝된다)
        exiting = true
        viewModelScope.launch {
            if (_state.value.status != QuizStatus.SUBMITTED && _state.value.mode == QuizMode.PRACTICE) {
                try { quiz.finish(attemptId, clock.millis()) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }
            onDone()
        }
    }

    fun toggleFurigana() { viewModelScope.launch { study.updateSettings { it.copy(bodyFurigana = !it.bodyFurigana) } } }
}
