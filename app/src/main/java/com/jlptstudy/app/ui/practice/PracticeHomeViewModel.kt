package com.jlptstudy.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.quiz.ExamPart
import com.jlptstudy.quiz.ExamSection
import com.jlptstudy.quiz.ExamTemplate
import com.jlptstudy.quiz.PoolItem
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.time.Clock

/** S12 연습 홈. 고른 급수는 화면 상태로만 두고 저장하지 않는다. 화면이 다시 보일 때마다 reload()로 미완료 시도를 다시 읽는다. */
class PracticeHomeViewModel(
    private val content: QuizContentRepository,
    private val quiz: QuizRepository,
    private val study: StudyRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(PracticeHomeUiState())
    val state: StateFlow<PracticeHomeUiState> = _state.asStateFlow()

    private val loader = QuizSessionLoader(quiz, content)
    private var pools: Map<JlptLevel, List<PoolItem>>? = null
    private var chosen: JlptLevel? = null
    private var lastStart: (() -> Unit)? = null
    private val refreshLock = Mutex() // init과 ON_START의 reload가 겹쳐도 풀은 한 번만 읽는다
    private var starting = false

    /** 읽기 예외는 화면을 S90으로 보내지 않고 EMPTY로 둔다(연습은 학습 흐름의 필수 경로가 아니다). */
    init { reload() }

    fun reload() {
        starting = false // 화면이 다시 보이면(Quiz에서 돌아옴) 새로 시작할 수 있다
        viewModelScope.launch { refresh() }
    }

    fun selectLevel(level: JlptLevel) {
        if (level == chosen || level !in _state.value.levels) return
        chosen = level
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() = refreshLock.withLock {
        val resume = loadResume()
        val all = try {
            pools ?: JlptLevel.entries.associateWith { content.pool(it) }.also { pools = it }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("Practice", "문항 풀 읽기 실패", e)
            _state.update { it.copy(status = PracticeHomeStatus.EMPTY, resume = resume) }
            return@withLock
        }
        val levels = JlptLevel.entries.filter { all[it].orEmpty().isNotEmpty() }
        val level = chosen?.takeIf { it in levels } ?: initialLevel(levels).also { chosen = it }
        val pool = all[level].orEmpty()
        val types = pool.groupBy { it.section to it.type }
            .map { (k, v) -> PracticeTypeUi(k.first, k.second, v.size) }
            .sortedWith(compareBy({ it.section.ordinal }, { QuizTypeLabels.order(it.section, it.type) }))
        val exam = if (level in safe { content.examLevels() }.orEmpty()) examCard(level, pool) else null
        _state.update {
            it.copy(
                status = if (types.isEmpty()) PracticeHomeStatus.EMPTY else PracticeHomeStatus.LOADED,
                level = level, levels = levels, types = types, exam = exam, resume = resume,
            )
        }
    }

    private suspend fun initialLevel(levels: List<JlptLevel>): JlptLevel {
        val target = safe { study.settings().first().targetLevels }.orEmpty()
        return levels.firstOrNull { it in target } ?: levels.firstOrNull() ?: JlptLevel.N5
    }

    private suspend fun examCard(level: JlptLevel, pool: List<PoolItem>): ExamCardUi? {
        val template = safe { content.examTemplate(level) } ?: return null // 깨진 템플릿은 카드를 숨긴다(S14 시작 화면이 FAILED로 보인다)
        val built = Quiz.buildSet(pool, template, clock.millis(), excluded = setOf(Section.LISTENING))
        return ExamCardUi(level, built.questionIds.size, template.limitMs(), built.shortfalls.isNotEmpty())
    }

    private suspend fun loadResume(): ResumeExamUi? = safe {
        val a = quiz.unfinishedExam() ?: return@safe null
        val loaded = loader.load(a.id) ?: return@safe null
        val level = loaded.meta?.level ?: QuizSessionLoader.meta(a.examId, null)?.level ?: return@safe null
        ResumeExamUi(a.id, level, loaded.answers.size, loaded.items.size, a.elapsedMs)
    }

    private suspend fun <T> safe(f: suspend () -> T): T? =
        try { f() } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.e("Practice", "읽기 실패", e); null }

    /** 유형 행: 최대 PRACTICE_SIZE문항 세트로 시도를 만들고 onStarted(attemptId)를 부른다. */
    fun startPractice(section: Section, type: String, onStarted: (Long) -> Unit) {
        if (starting) return // 더블탭: 시도가 두 개 생기고 Quiz가 두 번 쌓인다. 성공하면 화면이 돌아올 때(reload)까지 잠근다
        starting = true
        lastStart = { startPractice(section, type, onStarted) }
        viewModelScope.launch {
            try {
                val level = chosen ?: run { starting = false; return@launch }
                val template = ExamTemplate(level.n, listOf(ExamSection("practice", "", 0, listOf(ExamPart(section, type, PRACTICE_SIZE)))))
                val seed = clock.millis()
                val ids = Quiz.buildSet(pools?.get(level).orEmpty(), template, seed).questionIds
                check(ids.isNotEmpty()) { "빈 세트" }
                onStarted(quiz.startAttempt(null, seed, ids, clock.millis()))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Practice", "시작 실패", e)
                starting = false
                _state.update { it.copy(startFailed = true) }
            }
        }
    }

    /** 스낵바 "다시 시도". */
    fun retryStart() { _state.update { it.copy(startFailed = false) }; lastStart?.invoke() }
    fun startFailedShown() { _state.update { it.copy(startFailed = false) } }

    /** 모의고사 카드 "시작": 미완료 모의고사가 있으면 confirmReplace만 띄운다(AR-U14 A). 없으면 시작 화면으로. */
    fun requestStartExam(onExamStart: (JlptLevel) -> Unit) {
        val s = _state.value
        if (s.resume != null) _state.update { it.copy(confirmReplace = s.resume) } else onExamStart(s.level)
    }
    fun cancelReplace() { _state.update { it.copy(confirmReplace = null) } }

    /** confirmReplace "이어서 풀기": 풀던 시도로. */
    fun resumeFromReplace(onResume: (Long) -> Unit) {
        val r = _state.value.confirmReplace ?: return
        _state.update { it.copy(confirmReplace = null) }
        onResume(r.attemptId)
    }

    /** confirmReplace "새로 시작": finish·startAttempt를 부르지 않고 시작 화면으로. 이전 시도는 시작 화면의 "시작"이 저장소 트랜잭션에서 닫는다. */
    fun replaceWithNew(onExamStart: (JlptLevel) -> Unit) {
        if (_state.value.confirmReplace == null) return
        _state.update { it.copy(confirmReplace = null) }
        onExamStart(_state.value.level)
    }

    fun requestAbandon() { if (_state.value.resume != null) _state.update { it.copy(confirmAbandon = true) } }
    fun cancelAbandon() { _state.update { it.copy(confirmAbandon = false) } }
    fun abandonFailedShown() { _state.update { it.copy(abandonFailed = false) } }

    /** 포기 = finish(행은 지우지 않는다). 확인 대화상자의 "포기하기"에서만 부른다. */
    fun confirmAbandon() {
        val r = _state.value.resume ?: return
        viewModelScope.launch {
            try {
                quiz.finish(r.attemptId, clock.millis())
                _state.update { it.copy(confirmAbandon = false) }
                refresh()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Practice", "포기 실패", e)
                _state.update { it.copy(confirmAbandon = false, abandonFailed = true) }
            }
        }
    }
}
