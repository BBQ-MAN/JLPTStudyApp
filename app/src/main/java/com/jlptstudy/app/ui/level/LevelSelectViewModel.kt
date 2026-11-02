package com.jlptstudy.app.ui.level

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PREPARING = levelCounts() 계산 중(DB는 이미 열림), NO_CONTENT = 번들 콘텐츠 0행(빌드 결함). */
enum class LevelStatus { PREPARING, READY, NO_CONTENT }

data class LevelOption(val level: JlptLevel, val wordCount: Int, val kanjiCount: Int, val available: Boolean)

data class LevelUiState(
    val status: LevelStatus = LevelStatus.PREPARING,
    val options: List<LevelOption> = emptyList(),
    val selected: Set<JlptLevel> = setOf(JlptLevel.N5),
    val newCardsPerDay: Int = 10,
    /** "시작" 저장 실패(계약 밖 필드: 실패를 화면에 알리려고 더했다). */
    val saveFailed: Boolean = false,
)

internal suspend fun loadLevelOptions(content: ContentRepository): Pair<List<LevelOption>, Set<JlptLevel>> {
    val available = content.meta().levels
    val counts = content.levelCounts()
    return JlptLevel.entries.map { LevelOption(it, counts[it]?.words ?: 0, counts[it]?.kanji ?: 0, it in available) } to available
}

/** S01. 선택 가능한 급수는 content_meta.levels에 있는 것뿐이다(ui-design 5.9). */
class LevelSelectViewModel(private val study: StudyRepository, private val content: ContentRepository) : ViewModel() {
    private val _state = MutableStateFlow(LevelUiState())
    val state: StateFlow<LevelUiState> = _state.asStateFlow()
    private var starting = false

    init {
        viewModelScope.launch {
            try {
                val s = study.settings().first()
                val (options, available) = loadLevelOptions(content)
                _state.update {
                    it.copy(
                        status = if (available.isEmpty()) LevelStatus.NO_CONTENT else LevelStatus.READY, options = options,
                        selected = s.targetLevels intersect available, newCardsPerDay = s.newCardsPerDay,
                    )
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _state.update { it.copy(status = LevelStatus.NO_CONTENT) }
            }
        }
    }

    fun toggle(level: JlptLevel) = _state.update { st ->
        if (st.options.none { it.level == level && it.available }) st
        else st.copy(selected = if (level in st.selected) st.selected - level else st.selected + level)
    }

    fun setNewCards(n: Int) = _state.update { if (n in 5..50 && n % 5 == 0) it.copy(newCardsPerDay = n) else it }

    /** 급수·새 카드 수·onboarding_done = 1을 한 번에 저장하고 onSaved를 부른다. */
    fun start(onSaved: () -> Unit) {
        val st = _state.value
        if (starting || st.status != LevelStatus.READY || st.selected.isEmpty()) return
        starting = true
        viewModelScope.launch {
            try {
                study.updateSettings { it.copy(targetLevels = st.selected, newCardsPerDay = st.newCardsPerDay, onboardingDone = true) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _state.update { it.copy(saveFailed = true) }
                starting = false
                return@launch
            }
            starting = false
            onSaved()
        }
    }
}
