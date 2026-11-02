package com.jlptstudy.app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 검색어는 입력이 300 ms 멈춘 뒤 적용한다(ui-design 6.5). 검색어를 지우면 바로 적용한다. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowseViewModel(
    private val content: ContentRepository,
    private val study: StudyRepository,
    debounceMs: Long = 300,
) : ViewModel() {
    private val tab = MutableStateFlow(BrowseTab.VOCAB)
    private val query = MutableStateFlow("")
    private val levels = MutableStateFlow<Set<JlptLevel>?>(null) // null = 처음 값을 아직 못 정함
    private val available = MutableStateFlow<Set<JlptLevel>>(emptySet())

    private data class Result(val tab: BrowseTab, val levels: Set<JlptLevel>, val applied: String, val items: List<BrowseItem>?)

    init {
        viewModelScope.launch {
            try {
                val meta = content.meta()
                available.value = meta.levels
                levels.value = study.settings().first().targetLevels intersect meta.levels // 비면 번들 급수 전부
            } catch (e: CancellationException) { throw e } catch (_: Exception) { levels.value = emptySet() }
        }
    }

    private val results = combine(tab, levels.filterNotNull(), query.debounce { if (it.isBlank()) 0 else debounceMs }) { t, l, q -> Triple(t, l, q.trim()) }
        .mapLatest { (t, l, q) ->
            Result(t, l, q, try { content.browse(t, l, q) } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.e("Browse", "목록 읽기 실패", e); null })
        }

    val state: StateFlow<BrowseUiState> = combine(results, query, available) { r, typed, avail ->
        val items = r.items
        BrowseUiState(
            status = when {
                items == null -> BrowseStatus.ERROR // 읽기 실패: 화면이 S90으로 보낸다
                items.isNotEmpty() -> BrowseStatus.RESULTS
                r.applied.isNotBlank() -> BrowseStatus.NO_RESULT
                else -> BrowseStatus.NO_CONTENT_FOR_FILTER
            },
            tab = r.tab, levels = r.levels, query = typed, items = items.orEmpty(), availableLevels = avail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    fun setTab(t: BrowseTab) { tab.value = t }
    fun setQuery(q: String) { query.value = q }
    fun toggleLevel(l: JlptLevel) {
        val cur = levels.value ?: return
        levels.value = if (l in cur) cur - l else cur + l
    }
    fun clearLevels() { levels.update { if (it == null) null else emptySet() } }
}
