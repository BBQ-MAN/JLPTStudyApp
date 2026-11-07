package com.jlptstudy.app.ui.practice

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.GrammarRepository
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.quiz.AnswerInput
import com.jlptstudy.quiz.Quiz
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * S15 결과·해설(ui-design 6.10). 시도는 QuizSessionLoader로만 읽고(빠진 문항은 이미 제외), 점수는 남은 문항의 AnswerInput으로 :core:quiz가 낸다.
 * 해설 보기는 라우트 안 상태 reviewIndex이고 SavedStateHandle에 둬 프로세스 재시작 뒤에도 복원한다.
 */
class QuizResultViewModel(
    private val attemptId: Long,
    private val loader: QuizSessionLoader,
    private val content: ContentRepository,
    private val grammar: GrammarRepository,
    study: StudyRepository,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(QuizResultUiState())
    val state: StateFlow<QuizResultUiState> = _state.asStateFlow()

    private val linkCache = mutableMapOf<String, TargetLink?>()
    private val linksDone = mutableSetOf<String>() // 읽기 실패 없이 끝낸 문항만. 실패한 문항은 다시 열 때 재시도

    init {
        viewModelScope.launch { study.settings().map { it.bodyFurigana }.collect { v -> _state.update { it.copy(showRuby = v) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun <T> safe(f: suspend () -> T): T? =
        try { f() } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e("QuizResult", "읽기 실패", e); null }

    private suspend fun load() {
        val l = safe { loader.load(attemptId) }
        val meta = l?.meta
        if (l == null || meta == null || l.items.isEmpty()) {
            _state.update {
                it.copy(status = QuizResultStatus.EMPTY, mode = meta?.mode ?: it.mode, level = meta?.level, retiredCount = l?.retiredCount ?: 0)
            }
            return
        }
        val template = if (meta.mode == QuizMode.EXAM) safe { loader.content.examTemplate(meta.level) } else null
        val inputs = l.items.map { AnswerInput(it.id, it.section, it.type, it.answerIndex, l.answers[it.id]?.chosenIndex) }
        val shuffled = l.items.map { Quiz.shuffle(it.toQuestion(), l.record.seed) }
        val items = l.items.mapIndexed { i, q ->
            val a = l.answers[q.id]
            QuizReviewItemUi(i, q.toUi(shuffled[i]), a?.let { shuffled[i].toShown(it.chosenIndex) }, a?.correct ?: false, a?.scriptShown ?: false)
        }
        val exam = meta.mode == QuizMode.EXAM
        _state.update {
            it.copy(
                status = QuizResultStatus.LOADED, mode = meta.mode, level = meta.level, type = meta.type,
                score = Quiz.score(inputs, template),
                elapsedMs = if (exam) l.record.elapsedMs else null, limitMs = if (exam) template?.limitMs() else null,
                scriptShownCount = l.answers.values.count { a -> a.scriptShown }, retiredCount = l.retiredCount, items = items,
            )
        }
        saved.get<Int>(REVIEW_KEY)?.takeIf { it in items.indices }?.let(::openReview)
    }

    fun setFilter(filter: ResultFilter) = _state.update { it.copy(filter = filter) }

    /** 항목을 눌렀다(items 순번). 같은 라우트 안에서 해설을 보인다. */
    fun openReview(index: Int) {
        if (index !in _state.value.items.indices) return
        saved[REVIEW_KEY] = index
        _state.update { it.copy(reviewIndex = index) }
        viewModelScope.launch { ensureLinks(index) }
    }

    /** 시스템 뒤로·상단 뒤로: 목록으로. */
    fun closeReview() {
        saved.remove<Int>(REVIEW_KEY)
        _state.update { it.copy(reviewIndex = null) }
    }

    /** 이전·다음 문항: 현재 필터 안에서 움직인다. 끝이면 아무 일도 없다. */
    fun stepReview(delta: Int) {
        val s = _state.value
        val visible = s.visibleItems
        val pos = visible.indexOfFirst { it.index == s.reviewIndex }
        visible.getOrNull(pos + delta)?.takeIf { pos >= 0 }?.let { openReview(it.index) }
    }

    private suspend fun ensureLinks(index: Int) {
        val item = _state.value.items.getOrNull(index) ?: return
        if (item.question.id in linksDone) return
        var failed = false
        val links = item.question.targets.mapNotNull { id ->
            try { resolve(id) } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e("QuizResult", "읽기 실패", e); failed = true; null }
        }.distinct()
        if (!failed) linksDone += item.question.id
        _state.update { it.copy(targetLinks = it.targetLinks + (item.question.id to links)) }
    }

    /** jm:·av- = 어휘, gr- = 문법, 글자 하나 = 한자. 번들에 없거나 모르는 모양이면 null(링크만 숨김). 읽기 예외는 던져 캐시하지 않는다. */
    private suspend fun resolve(id: String): TargetLink? {
        if (linkCache.containsKey(id)) return linkCache[id]
        val link = when {
            id.startsWith("jm:") || id.startsWith("av-") ->
                content.vocabDetail(id)?.let { TargetLink(TargetKind.VOCAB, id, it.written ?: it.reading) }
            id.startsWith("gr-") -> grammar.detail(id)?.let { TargetLink(TargetKind.GRAMMAR, id, it.title) }
            id.codePointCount(0, id.length) == 1 -> content.kanjiDetail(id)?.let { TargetLink(TargetKind.KANJI, id, it.literal) }
            else -> null
        }
        linkCache[id] = link
        return link
    }

    private companion object { const val REVIEW_KEY = "reviewIndex" }
}
