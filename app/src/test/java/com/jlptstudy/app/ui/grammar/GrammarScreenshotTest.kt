package com.jlptstudy.app.ui.grammar

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.detail.DetailStatus
import com.jlptstudy.app.ui.dictionary.DictShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S10·S11 스크린샷(ui-design 12절 이름, 합성 픽스처). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GrammarScreenshotTest : DictShots() {
    @Test fun grammar_list_loading() = shots("grammar_list_loading") { GrammarListScreen(GrammarListUiState()) }
    @Test fun grammar_list_loaded() = shots("grammar_list_loaded") { GrammarListScreen(grammarListState) }
    @Test fun grammar_list_loaded_font200() = shots("grammar_list_loaded", fontScale = 2f) { GrammarListScreen(grammarListState) }
    @Test fun grammar_list_empty() = shots("grammar_list_empty") {
        GrammarListScreen(GrammarListUiState(GrammarListStatus.EMPTY, setOf(JlptLevel.N1), emptyList(), setOf(JlptLevel.N1)))
    }
    @Config(qualifiers = "w360dp-h1500dp-xhdpi")
    @Test fun grammar_detail_loaded() = shots("grammar_detail_loaded") { GrammarDetailScreen(grammarDetailState) }
    @Config(qualifiers = "w360dp-h3600dp-xhdpi")
    @Test fun grammar_detail_loaded_font200() = shots("grammar_detail_loaded", fontScale = 2f) { GrammarDetailScreen(grammarDetailState) }
    @Test fun grammar_detail_retired() = shots("grammar_detail_retired") { GrammarDetailScreen(GrammarDetailUiState(DetailStatus.RETIRED)) }
}
