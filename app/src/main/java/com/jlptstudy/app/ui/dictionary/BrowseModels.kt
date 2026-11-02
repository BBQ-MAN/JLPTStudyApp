package com.jlptstudy.app.ui.dictionary

import com.jlptstudy.app.JlptLevel

// architecture.md 7절 S05.
enum class BrowseTab { VOCAB, KANJI }
enum class BrowseStatus { LOADING, ERROR, RESULTS, NO_RESULT, NO_CONTENT_FOR_FILTER }

/** 목록 한 줄. id는 LazyColumn/LazyVerticalGrid의 안정 key(콘텐츠 안정 ID). */
sealed interface BrowseItem {
    val id: String
    val level: JlptLevel

    data class Word(override val id: String, val written: String?, val reading: String, val meaningKo: String?, override val level: JlptLevel) : BrowseItem
    data class Kanji(val literal: String, val koreanReading: String?, override val level: JlptLevel) : BrowseItem {
        override val id: String get() = literal
    }
}

data class BrowseUiState(
    val status: BrowseStatus = BrowseStatus.LOADING,
    val tab: BrowseTab = BrowseTab.VOCAB,
    val levels: Set<JlptLevel> = emptySet(), // 빈 집합 = 번들된 급수 전부
    val query: String = "",
    val items: List<BrowseItem> = emptyList(),
    val availableLevels: Set<JlptLevel> = emptySet(), // 칩으로 보일 번들 급수(계약 밖 필드)
)
