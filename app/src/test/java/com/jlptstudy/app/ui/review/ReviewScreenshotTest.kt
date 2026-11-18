package com.jlptstudy.app.ui.review

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.ui.common.LocalFrozenProgress
import com.jlptstudy.app.ui.home.HomeStatus
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/** S03·S04 스크린샷(ui-design 12절 이름). 글꼴 200%와 낮은 높이는 이 파일 아래 클래스. */
abstract class ReviewShots {
    @get:Rule val rule = createComposeRule()

    // 기준 이미지는 KST로 기록했다. 같은 JVM의 다른 테스트가 기본 시간대를 UTC로 바꿔 두면 "다음 복습" 시각이 달라진다
    @org.junit.Before fun fixZone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"))
    private val dir = System.getProperty("screenshots.dir")!!
    private val t = Instant.parse("2026-11-02T10:00:00Z")
    private val scheduler = Fsrs6Scheduler()
    protected val previews = scheduler.preview(CardState.new("x", t, scheduler.version), t, SrsConfig(studyDay = StudyDayRule(ZoneOffset.UTC)))

    // 검수 칩 조합이 화면마다 6가지 이상 보이도록 섞는다(12절).
    protected val manyMeanings = listOf(
        meaning("먹다", "jmdict-translated", "llm:model@2026-11-01", "unreviewed", null),
        meaning("〜さん보다 정중한 호칭", "authored", "human", "reviewed", "human"),
        meaning("생활하다, 살아가다", "authored", "llm:model@2026-11-01", "reviewed", "agent"),
        meaning("네 번째 뜻은 접혀서 보이지 않는다", "jmdict-translated", "human", "sampled", null),
    )
    protected val word = vocab("a", "食べる", "たべる", manyMeanings, pos = listOf("v1", "vt"))
    protected val today = vocab("b", "今日", "きょう", listOf(meaning("오늘", "jmdict-translated", "llm:model@2026-11-01", "unreviewed", null)), pos = listOf("n-adv", "n"))
    protected val katakana = vocab(
        "c", "コミュニケーション", "コミュニケーション",
        listOf(meaning("의사소통", "jmdict-translated", "llm:model@2026-11-01", "reviewed", "human"), meaning("소통", "authored", "", "", null)),
    )
    protected val recall = vocab("d", "食べる", "たべる", manyMeanings.take(2), direction = VocabDirection.KO_TO_JA)
    protected val kanjiCard = kanji("直").copy(
        words = listOf(WordRef("1", "直接", "ちょくせつ", "직접"), WordRef("2", "正直", "しょうじき", "정직"), WordRef("3", "直す", "なおす", "고치다")),
        kun = listOf("ただ(ちに)", "なお(す)", "なお(る)"),
    )

    protected val grammar = grammarCard(
        "n5-1", example = "これを[[{見|み}てください]]。", exampleKo = "시험 예문 번역", meaningKo = "시험용 대응 표현",
        summaryKo = "시험용 설명 요약이 네 줄을 넘도록 길게 이어지는 문장입니다. ".repeat(6), needsReview = true,
    )
    protected val grammarLong = grammarCard("n5-2", title = "〜なければならない", example = "{毎日|まいにち}[[{勉強|べんきょう}しなければならない]]。")

    protected fun state(card: ReviewCardUi, revealed: Boolean, saving: Boolean = false, failed: Rating? = null) = ReviewUiState(
        status = ReviewStatus.READY, card = card, revealed = revealed, previews = previews, saving = saving, saveFailed = failed,
        remainingReview = 4, remainingNew = 6, reviewedInSession = 3, newInSession = 2, canUndo = true,
    )

    protected fun shots(
        name: String, fontScale: Float = 1f, landscape: String = "", prefs: ReviewPrefs = ReviewPrefs(), snackbarText: String? = null, scrollPx: Float = 0f,
        s: ReviewUiState,
    ) {
        var dark by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            // 늘어남(stretch) 끝 효과는 끝을 넘겨 끌 때 내용을 한쪽으로 밀어 그린다: 끝까지 끌어올린 상태를 정확히 찍도록 끈다
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalFrozenProgress provides true, LocalOverscrollFactory provides null) {
                JlptTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    val host = remember { SnackbarHostState() }
                    if (snackbarText != null) LaunchedEffect(Unit) { host.showSnackbar(snackbarText, "다시 시도", duration = SnackbarDuration.Indefinite) }
                    ReviewScreen(s, prefs, host, {}, {}, {}, {}, {}, {}, onChipClick = null, onGrammarDetail = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        if (scrollPx > 0f) { // 긴 뒷면은 카드 안에서 스크롤된다: 아래쪽(뜻·칩)이 보이게 끌어올린 상태를 찍는다
            rule.onNodeWithTag("review_card", useUnmergedTree = true).performTouchInput {
                repeat((scrollPx / 200f).toInt().coerceAtLeast(1)) { swipe(Offset(centerX, height - 8f), Offset(centerX, 8f), 150) } // 노드 안에서만 끌어야 눌림이 닿는다
            }
            rule.mainClock.advanceTimeBy(600)
        }
        val suffix = (if (landscape.isNotEmpty()) "_$landscape" else "") + if (fontScale > 1f) "_font200" else ""
        rule.onRoot().captureRoboImage("$dir/${name}_light$suffix.png")
        dark = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onRoot().captureRoboImage("$dir/${name}_dark$suffix.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ReviewScreenshotTest : ReviewShots() {
    @Test fun review_front_word() = shots("review_front_word", s = state(word, false))
    @Test fun review_back_word() = shots("review_back_word", s = state(word, true))
    @Test fun review_front_kanji() = shots("review_front_kanji", s = state(kanjiCard, false))
    @Test fun review_back_kanji() = shots("review_back_kanji", s = state(kanjiCard, true))
    @Test fun review_front_recall() = shots("review_front_recall", s = state(recall, false))
    @Test fun review_back_word_long_katakana() = shots("review_back_word_long_katakana", s = state(katakana, true))
    @Test fun review_front_grammar() = shots("review_front_grammar", s = state(grammar, false))
    @Test fun review_back_grammar() = shots("review_back_grammar", s = state(grammar, true))
    @Test fun review_back_grammar_scrolled() = shots("review_back_grammar_scrolled", scrollPx = 600f, s = state(grammar, true))
    @Test fun review_front_grammar_long_title() = shots("review_front_grammar_long_title", s = state(grammarLong, false))
    @Test fun review_back_grammar_font200() = shots("review_back_grammar", fontScale = 2f, s = state(grammar, true))
    @Test fun review_saving() = shots("review_saving", s = state(word, true, saving = true))
    @Test fun review_save_error() = shots("review_save_error", snackbarText = "평가를 저장하지 못했습니다", s = state(word, true, failed = Rating.EASY))
    @Test fun review_furigana_off() = shots("review_furigana_off", prefs = ReviewPrefs(bodyFurigana = false), s = state(today, true))
    @Test fun review_back_today_wide_ruby() = shots("review_back_word_wide_ruby", s = state(today, true))
    // 회귀: 품사 코드 adj-pn이 코드 그대로가 아니라 '연체사'로 보인다(PosLabels). 메인이 고친 low 지적의 스크린샷
    @Test fun review_back_word_adnominal() = shots(
        "review_back_word_adnominal",
        s = state(vocab("e", "大きな", "おおきな", listOf(meaning("큰", "jmdict-translated", "llm:model@2026-11-01", "reviewed", "human")), pos = listOf("adj-pn")), true),
    )

    // 글꼴 200% 뒷면: 스크롤 전(이어짐 페이드가 보이는 첫 화면)과 맨 아래까지 끌어올린 뒤(뜻·칩이 보이는 화면)를 둘 다 찍는다
    @Test fun review_back_word_font200() = shots("review_back_word", fontScale = 2f, s = state(word, true))
    @Test fun review_back_word_scrolled_font200() = shots("review_back_word_scrolled", fontScale = 2f, scrollPx = 600f, s = state(word, true))
    @Test fun review_back_kanji_scrolled_font200() = shots("review_back_kanji_scrolled", fontScale = 2f, scrollPx = 600f, s = state(kanjiCard, true))
    @Test fun review_back_grammar_scrolled_font200() = shots("review_back_grammar_scrolled", fontScale = 2f, scrollPx = 600f, s = state(grammar, true))
    @Test fun review_back_word_long_katakana_font200() = shots("review_back_word_long_katakana", fontScale = 2f, s = state(katakana, true))
    @Test fun review_back_kanji_font200() = shots("review_back_kanji", fontScale = 2f, s = state(kanjiCard, true))

    @Test fun review_save_error_font200() = shots("review_save_error", fontScale = 2f, snackbarText = "평가를 저장하지 못했습니다", s = state(word, true, failed = Rating.EASY))
    @Test fun review_empty_all_done() = shots("review_empty_all_done", s = ReviewUiState(status = ReviewStatus.EMPTY, emptyReason = HomeStatus.ALL_DONE, nextDueAtEpochMs = 1793700000000L, nextDueCount = 4))
    @Test fun review_empty_new_limit() = shots("review_empty_new_limit", s = ReviewUiState(status = ReviewStatus.EMPTY, emptyReason = HomeStatus.NEW_LIMIT_REACHED, nextDueAtEpochMs = 1793700000000L, nextDueCount = 4, newLimit = 10))

    @Test fun done_loaded() = doneShots("done_loaded", left = 3)
    @Test fun done_loaded_font200() = shots(
        "done_loaded", fontScale = 2f,
        s = ReviewUiState(status = ReviewStatus.READY, finished = true, reviewedInSession = 12, newInSession = 5, newCardsLeftAfterSession = 3),
    )
    @Test fun done_no_more() = doneShots("done_no_more", left = 0)

    private fun doneShots(name: String, left: Int) = shots(
        name, s = ReviewUiState(status = ReviewStatus.READY, finished = true, reviewedInSession = 12, newInSession = 5, newCardsLeftAfterSession = left),
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h360dp-xhdpi")
class ReviewLandscapeScreenshotTest : ReviewShots() {
    @Test fun review_front_word_land_font200() = shots("review_front_word", fontScale = 2f, landscape = "land", s = state(word, false))
    @Test fun review_back_word_land_font200() = shots("review_back_word", fontScale = 2f, landscape = "land", s = state(word, true))
    @Test fun review_back_word_scrolled_land_font200() = shots("review_back_word_scrolled", fontScale = 2f, landscape = "land", scrollPx = 600f, s = state(word, true))
}
