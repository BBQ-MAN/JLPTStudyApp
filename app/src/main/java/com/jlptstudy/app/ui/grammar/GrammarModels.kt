package com.jlptstudy.app.ui.grammar

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.GrammarDetailUi
import com.jlptstudy.app.data.content.GrammarRow
import com.jlptstudy.app.ui.detail.DetailStatus

// architecture.md 9.1 S10·S11. ERROR와 availableLevels·showRuby는 계약 밖 필드(Browse·Vocab 상세와 같은 이유).
enum class GrammarListStatus { LOADING, ERROR, LOADED, EMPTY }

data class GrammarListUiState(
    val status: GrammarListStatus = GrammarListStatus.LOADING,
    val levels: Set<JlptLevel> = emptySet(), // 빈 집합 = 문법이 있는 급수 전부
    val rows: List<GrammarRow> = emptyList(),
    val availableLevels: Set<JlptLevel> = emptySet(), // 칩으로 보일 급수
)

data class GrammarDetailUiState(
    val status: DetailStatus = DetailStatus.LOADING,
    val detail: GrammarDetailUi? = null,
    val showRuby: Boolean = true, // 설정의 본문 후리가나(상단 토글로 바꾼다)
)
