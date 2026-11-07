package com.jlptstudy.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.quiz.BuiltSet
import com.jlptstudy.quiz.ExamSection
import com.jlptstudy.quiz.ExamTemplate
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/** 모의고사 영역: parts가 모두 청해인 영역은 뺀다(AR-U11 A, UI-U09 A). */
internal fun ExamTemplate.examSections(): List<ExamSection> = sections.filterNot { s -> s.parts.all { it.section == Section.LISTENING } }

/** 기준 시간 = 청해 제외 영역 minutes 합 × 60,000(architecture.md 9.2.6 "재개"). */
internal fun ExamTemplate.limitMs(): Long = examSections().sumOf { it.minutes } * 60_000L

/** 문항의 (영역, 유형)이 처음 들어 있는 영역 순번. 없으면 -1. */
internal fun List<ExamSection>.indexOfKey(section: Section, type: String): Int =
    indexOfFirst { s -> s.parts.any { it.section == section && it.type == type } }

/** S14 시작 화면. 열 때 seed 하나로 buildSet을 한 번 하고, "시작"은 그 BuiltSet으로만 startAttempt를 부른다. 시도 행은 "시작"에서만 생긴다. */
class ExamStartViewModel(
    private val level: JlptLevel,
    private val content: QuizContentRepository,
    private val quiz: QuizRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ExamStartUiState(level = level))
    val state: StateFlow<ExamStartUiState> = _state.asStateFlow()

    private var seed = 0L
    private var built: BuiltSet? = null

    init { load() }

    /** 열기와 FAILED의 "다시 시도": 템플릿·풀을 다시 읽어 새 seed로 buildSet. */
    fun load() {
        _state.update { it.copy(status = ExamStartStatus.LOADING) }
        viewModelScope.launch {
            try {
                val template = content.examTemplate(level)
                if (template == null) { _state.update { it.copy(status = ExamStartStatus.EMPTY) }; return@launch }
                val pool = content.pool(level)
                val s = clock.millis()
                val set = Quiz.buildSet(pool, template, s, excluded = setOf(Section.LISTENING))
                val sections = template.examSections()
                val byId = pool.associateBy { it.id }
                val counts = set.questionIds.mapNotNull { byId[it] }.groupingBy { sections.indexOfKey(it.section, it.type) }.eachCount()
                seed = s
                built = set
                _state.update {
                    it.copy(
                        status = if (set.questionIds.isEmpty()) ExamStartStatus.EMPTY else ExamStartStatus.LOADED,
                        sections = sections.mapIndexed { i, sec -> ExamSectionLine(sec.titleKo, counts[i] ?: 0, sec.minutes) },
                        total = set.questionIds.size, limitMs = template.limitMs(), hasShortfall = set.shortfalls.isNotEmpty(),
                    )
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("ExamStart", "모의고사 불러오기 실패", e)
                _state.update { it.copy(status = ExamStartStatus.FAILED, startFailed = false) }
            }
        }
    }

    /**
     * "시작": startAttempt 한 번 뒤 startedAttemptId에 싣는다. 화면 콜백은 보관하지 않는다 —
     * 회전으로 새로 만든 NavController가 Route의 LaunchedEffect에서 이동한다. 성공하면 화면을 떠나므로 starting을 풀지 않는다.
     */
    fun start() {
        val s = _state.value
        val set = built
        if (s.status != ExamStartStatus.LOADED || s.starting || s.hasShortfall || set == null) return
        _state.update { it.copy(starting = true, startFailed = false) }
        viewModelScope.launch {
            val id = try {
                quiz.startAttempt("exam-n${level.n}", seed, set.questionIds, clock.millis())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("ExamStart", "시작 실패", e)
                _state.update { it.copy(starting = false, startFailed = true) }
                return@launch
            }
            _state.update { it.copy(startedAttemptId = id) }
        }
    }

    /** 스낵바 "다시 시도": 같은 seed·questionIds로 startAttempt만 다시. */
    fun retryStart() = start()
    fun startedConsumed() { _state.update { it.copy(startedAttemptId = null) } }
    fun startFailedShown() { _state.update { it.copy(startFailed = false) } }
}
