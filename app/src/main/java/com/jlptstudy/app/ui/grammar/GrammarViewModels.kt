package com.jlptstudy.app.ui.grammar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.GrammarRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.ui.detail.DetailStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GrammarListViewModel(private val grammar: GrammarRepository, private val study: StudyRepository) : ViewModel() {
    private val levels = MutableStateFlow<Set<JlptLevel>?>(null) // null = 처음 값을 아직 못 정함
    private val available = MutableStateFlow<Set<JlptLevel>>(emptySet())

    private data class Result(val levels: Set<JlptLevel>, val rows: List<com.jlptstudy.app.data.content.GrammarRow>?)

    init {
        viewModelScope.launch {
            try {
                val avail = grammar.levels()
                available.value = avail
                levels.value = study.settings().first().targetLevels intersect avail // 비면 문법이 있는 급수 전부
            } catch (e: CancellationException) { throw e } catch (_: Exception) { levels.value = emptySet() }
        }
    }

    val state: StateFlow<GrammarListUiState> = combine(
        levels.filterNotNull().mapLatest { l ->
            Result(l, try { grammar.list(l) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("Grammar", "목록 읽기 실패", e); null
            })
        },
        available,
    ) { r, avail ->
        val rows = r.rows
        GrammarListUiState(
            status = when {
                rows == null -> GrammarListStatus.ERROR // 화면이 S90으로 보낸다
                rows.isEmpty() -> GrammarListStatus.EMPTY
                else -> GrammarListStatus.LOADED
            },
            levels = r.levels, rows = rows.orEmpty(), availableLevels = avail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarListUiState())

    fun toggleLevel(l: JlptLevel) {
        val cur = levels.value ?: return
        levels.value = if (l in cur) cur - l else cur + l
    }
    fun clearLevels() { levels.update { if (it == null) null else emptySet() } }
}

class GrammarDetailViewModel(
    private val id: String, private val grammar: GrammarRepository, private val study: StudyRepository,
) : ViewModel() {
    val state: StateFlow<GrammarDetailUiState> = flow {
        val d = try { grammar.detail(id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("Grammar", "상세 읽기 실패", e); emit(GrammarDetailUiState(DetailStatus.ERROR)); return@flow
        }
        if (d == null) { emit(GrammarDetailUiState(DetailStatus.RETIRED)); return@flow }
        study.settings().collect { emit(GrammarDetailUiState(DetailStatus.LOADED, d, it.bodyFurigana)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDetailUiState())

    fun toggleFurigana() { viewModelScope.launch { study.updateSettings { it.copy(bodyFurigana = !it.bodyFurigana) } } }
}
