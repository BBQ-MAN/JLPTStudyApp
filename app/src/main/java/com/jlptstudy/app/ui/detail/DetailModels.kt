package com.jlptstudy.app.ui.detail

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.review.WordRef

// architecture.md 7절 S06·S07.
enum class DetailStatus { LOADING, LOADED, RETIRED, ERROR }
enum class CardProgress { NEW, LEARNING, REVIEW }

/** 카드 종류별 학습 상태 요약. dueAtEpochMs는 REVIEW일 때만 쓴다. */
data class CardStateSummary(val kind: CardKind, val progress: CardProgress, val dueAtEpochMs: Long? = null)

data class VocabDetailUiState(
    val status: DetailStatus = DetailStatus.LOADING, val written: String? = null,
    val reading: String = "", val ruby: List<RubySegment> = emptyList(), val level: JlptLevel? = null,
    val pos: List<String> = emptyList(), // 한국어 이름표(PosLabels)
    val meanings: List<MeaningUi> = emptyList(),
    val kanji: List<String> = emptyList(), val cardStates: List<CardStateSummary> = emptyList(),
    val showRuby: Boolean = true, // 설정의 본문 후리가나(상단 토글로 바꾼다)
)

data class KanjiDetailUiState(
    val status: DetailStatus = DetailStatus.LOADING, val literal: String = "", val level: JlptLevel? = null, // level: 계약 밖 필드(머리 칩)
    val strokeCount: Int = 0, val on: List<String> = emptyList(), val kun: List<String> = emptyList(),
    val koreanReadings: List<String> = emptyList(), val words: List<WordRef> = emptyList(),
    val strokes: List<String>? = null, // null = 필순 데이터 없음 → 절 숨김(S-03 확정 전)
    val strokeSourceName: String? = null, // attribution.json S-03 name. null이면 출처 줄 숨김
)
