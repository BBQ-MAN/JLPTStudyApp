package com.jlptstudy.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.ui.review.PosLabels
import com.jlptstudy.srs.Phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/** 학습 기록 읽기 실패는 카드 요약만 비운다. 콘텐츠 읽기 실패는 ERROR로 알려 S90으로 보낸다(없는·retired ID는 RETIRED). */
private suspend inline fun <T> loadOrNull(block: () -> T?): T? =
    try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.e("Detail", "읽기 실패", e); null }

class VocabDetailViewModel(
    private val id: String,
    private val content: ContentRepository,
    private val study: StudyRepository,
) : ViewModel() {
    val state: StateFlow<VocabDetailUiState> = flow {
        val d = try { content.vocabDetail(id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("Detail", "읽기 실패", e); emit(VocabDetailUiState(DetailStatus.ERROR)); return@flow
        }
        if (d == null) { emit(VocabDetailUiState(DetailStatus.RETIRED)); return@flow }
        study.settings().collect { settings ->
        val enabled = settings.enabledKinds
        val states = loadOrNull { study.states(listOf("vr:$id", "vp:$id")) }.orEmpty()
        val cards = listOf(CardKind.VR, CardKind.VP).mapNotNull { kind ->
            val s = states["${kind.code}:$id"]
            when {
                s == null && kind !in enabled -> null // 꺼진 종류의 새 카드는 보이지 않는다
                s == null || s.phase == Phase.NEW -> CardStateSummary(kind, CardProgress.NEW)
                s.phase == Phase.REVIEW -> CardStateSummary(kind, CardProgress.REVIEW, s.dueAtEpochMs)
                else -> CardStateSummary(kind, CardProgress.LEARNING)
            }
        }
        emit(
            VocabDetailUiState(
                DetailStatus.LOADED, d.written, d.reading, d.ruby, d.level, d.pos.map(PosLabels::of), d.meanings, d.kanji, cards, settings.bodyFurigana,
            ),
        )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabDetailUiState())

    fun toggleFurigana() { viewModelScope.launch { study.updateSettings { it.copy(bodyFurigana = !it.bodyFurigana) } } }
}

class KanjiDetailViewModel(
    private val literal: String,
    private val content: ContentRepository,
) : ViewModel() {
    val state: StateFlow<KanjiDetailUiState> = flow {
        val d = try { content.kanjiDetail(literal) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("Detail", "읽기 실패", e); emit(KanjiDetailUiState(DetailStatus.ERROR, literal)); return@flow
        }
        emit(
            if (d == null) KanjiDetailUiState(DetailStatus.RETIRED, literal)
            else KanjiDetailUiState(DetailStatus.LOADED, d.literal, d.level, d.strokeCount, d.on, d.kun, d.koreanReadings, d.words, d.strokes),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KanjiDetailUiState(literal = literal))
}
