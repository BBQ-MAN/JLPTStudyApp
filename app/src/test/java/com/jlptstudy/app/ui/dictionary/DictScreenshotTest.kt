package com.jlptstudy.app.ui.dictionary

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.common.LocalFrozenProgress
import com.jlptstudy.app.ui.detail.CardProgress
import com.jlptstudy.app.ui.detail.CardStateSummary
import com.jlptstudy.app.ui.detail.DetailStatus
import com.jlptstudy.app.ui.detail.KanjiDetailScreen
import com.jlptstudy.app.ui.detail.KanjiDetailUiState
import com.jlptstudy.app.ui.detail.VocabDetailScreen
import com.jlptstudy.app.ui.detail.VocabDetailUiState
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** S05·S06·S07 스크린샷(ui-design 12절 이름). 라이트·다크 한 쌍씩 screenshots/에 쓴다. */
abstract class DictShots {
    @get:Rule val rule = createComposeRule()
    private val dir = System.getProperty("screenshots.dir")!!

    @Before fun fixZone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")) // 복습 날짜가 기기 시간대에 따라 달라지지 않게

    protected fun click(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
    }

    /** dialog = true: 대화상자 창까지 담는다(onRoot는 본 창만 찍는다). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    protected fun shots(name: String, fontScale: Float = 1f, setup: () -> Unit = {}, dialog: Boolean = false, content: @Composable () -> Unit) {
        val capture = { path: String -> if (dialog) com.github.takahirom.roborazzi.captureScreenRoboImage(path) else rule.onRoot().captureRoboImage(path) }
        var dark by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalFrozenProgress provides true) {
                JlptTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        setup()
        val suffix = if (fontScale > 1f) "_font200" else ""
        capture("$dir/${name}_light$suffix.png")
        dark = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(1_000)
        capture("$dir/${name}_dark$suffix.png")
    }

    private val avail = setOf(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3)
    protected fun browse(status: BrowseStatus, tab: BrowseTab = BrowseTab.VOCAB, items: List<BrowseItem> = emptyList(), query: String = "") =
        BrowseUiState(status, tab, setOf(JlptLevel.N5, JlptLevel.N4), query, items, avail)

    protected val vocabState = VocabDetailUiState(
        DetailStatus.LOADED, "今日", "きょう", Furigana.fromHeadword("今日", "きょう"), JlptLevel.N5, listOf("명사", "부사적 명사"), fiveMeanings, listOf("今", "日"),
        listOf(CardStateSummary(CardKind.VR, CardProgress.REVIEW, Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()), CardStateSummary(CardKind.VP, CardProgress.NEW)),
    )
    protected val kanjiState = KanjiDetailUiState(
        DetailStatus.LOADED, "直", JlptLevel.N5, 8, listOf("チョク", "ジキ"), listOf("ただ(ちに)", "なお(す)", "なお(る)"), listOf("직"), sampleWordRefs,
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class DictScreenshotTest : DictShots() {
    @Test fun dict_words() = shots("dict_words") { BrowseScreen(browse(BrowseStatus.RESULTS, items = sampleWords)) }
    @Test fun dict_words_3500() = shots("dict_words_3500") {
        // 3,500행 픽스처(시험용 합성 항목): 보이는 행만 그려지고 key(id)는 행마다 유일하다
        val rows = List(3_500) { BrowseItem.Word("w$it", "語$it", "ご$it", "시험용 뜻 $it", JlptLevel.N5) }
        BrowseScreen(browse(BrowseStatus.RESULTS, items = rows))
    }
    @Test fun dict_kanji() = shots("dict_kanji") { BrowseScreen(browse(BrowseStatus.RESULTS, BrowseTab.KANJI, sampleKanji)) }
    @Test fun dict_kanji_font200() = shots("dict_kanji", fontScale = 2f) { BrowseScreen(browse(BrowseStatus.RESULTS, BrowseTab.KANJI, sampleKanji)) }
    @Test fun dict_no_result() = shots("dict_no_result") { BrowseScreen(browse(BrowseStatus.NO_RESULT, query = "없는말")) }
    @Test fun dict_no_content_for_filter() = shots("dict_no_content_for_filter") { BrowseScreen(browse(BrowseStatus.NO_CONTENT_FOR_FILTER)) }

    @Test fun word_detail_loaded() = shots("word_detail_loaded") { VocabDetailScreen(vocabState) }
    @Test fun word_detail_loaded_font200() = shots("word_detail_loaded", fontScale = 2f) { VocabDetailScreen(vocabState) }
    // 첫 화면 밖의 쓰인 한자·학습 상태 절과 여섯 번째 검수 칩까지 보이는 긴 화면
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun word_detail_full() = shots("word_detail_full") {
        VocabDetailScreen(vocabState.copy(meanings = fiveMeanings + mean("직접 쓴 뜻, 검수 전", "authored", "human", "unreviewed", null)))
    }
    @Test fun kanji_detail_loaded() = shots("kanji_detail_loaded") { KanjiDetailScreen(kanjiState) }
    // 필순 절은 첫 화면 밖이라 긴 화면으로 찍는다. 합성 픽스처(source_id authored)이며 실제 필순이 아니다.
    private val strokesState get() = kanjiState.copy(strokes = com.jlptstudy.app.ui.detail.syntheticStrokes, strokeSourceName = "합성 필순 출처")
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes() = shots("kanji_detail_strokes") { KanjiDetailScreen(strokesState) }
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_font200() = shots("kanji_detail_strokes", fontScale = 2f) { KanjiDetailScreen(strokesState) }
    private fun numbersOn() { click("획 번호"); rule.mainClock.advanceTimeBy(600) }
    private val denseState get() = strokesState.copy(strokes = com.jlptstudy.app.ui.detail.denseSyntheticStrokes)
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_numbers() = shots("kanji_detail_strokes_numbers", setup = ::numbersOn) { KanjiDetailScreen(strokesState) }
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_numbers_font200() = shots("kanji_detail_strokes_numbers", fontScale = 2f, setup = ::numbersOn) { KanjiDetailScreen(strokesState) }
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_numbers_dense() = shots("kanji_detail_strokes_numbers_dense", setup = ::numbersOn) { KanjiDetailScreen(denseState) }
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_numbers_dense_font200() = shots("kanji_detail_strokes_numbers_dense", fontScale = 2f, setup = ::numbersOn) { KanjiDetailScreen(denseState) }
    // 끝난 상태: 상태 문구가 나와도 스위치와 출처 줄이 밀리지 않는지 본다
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_done() = shots("kanji_detail_strokes_done", setup = { click("획 순서대로 보기"); rule.mainClock.advanceTimeBy(3_000) }) { KanjiDetailScreen(strokesState) }
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun kanji_detail_strokes_reduced_motion() {
        val r = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try { shots("kanji_detail_strokes_reduced_motion") { KanjiDetailScreen(strokesState) } }
        finally { Settings.Global.putFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    }
    @Test fun kanji_detail_no_korean_reading() = shots("kanji_detail_no_korean_reading") { KanjiDetailScreen(kanjiState.copy(literal = "語", koreanReadings = emptyList())) }
}
