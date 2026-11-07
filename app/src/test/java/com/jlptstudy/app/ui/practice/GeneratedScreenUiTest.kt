package com.jlptstudy.app.ui.practice

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan
import com.jlptstudy.quiz.Score
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.SectionScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 자동 생성 연습 화면(QG4): S12 영역, S13·S15 라벨·한국어 로캘·관련 단어 보기·칩. 합성 픽스처. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class GeneratedScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<String>()

    private fun text(tag: String): AnnotatedString = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single()
    private fun desc(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private fun locales(t: AnnotatedString, locale: androidx.compose.ui.text.SpanStyle) =
        t.spanStyles.filter { it.item.localeList == locale.localeList }.joinToString("|") { t.text.substring(it.start, it.end) }

    // --- S12 ---

    private val home = PracticeHomeUiState(
        PracticeHomeStatus.LOADED, JlptLevel.N4, listOf(JlptLevel.N5, JlptLevel.N4), emptyList(), null,
        generated = listOf(
            GeneratedTypeUi("kanji-reading", 7, false), GeneratedTypeUi("orthography", 245, true),
            GeneratedTypeUi("meaning-ja-ko", 63, true), GeneratedTypeUi("meaning-ko-ja", 6, false),
        ),
    )

    @Test fun `S12는 제목 자동 생성 연습과 설명과 유형 행 4개를 보인다`() {
        rule.setContent { JlptTheme { PracticeHomeScreen(home) } }
        rule.onNodeWithText("자동 생성 연습").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("자동 생성 연습").fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        rule.onNodeWithText("단어 데이터에서 규칙으로 만든 연습 문제입니다. 실제 시험 문제와 형식이 다릅니다.").assertIsDisplayed()
        val spoken = listOf(
            "kanji-reading" to "한자 읽기, 자동 생성, 단어 7개, 사용할 수 없음, 대상 단어가 부족합니다",
            "orthography" to "표기, 자동 생성, 단어 245개",
            "meaning-ja-ko" to "뜻 고르기, 자동 생성, 단어 63개",
            "meaning-ko-ja" to "단어 고르기, 자동 생성, 단어 6개, 사용할 수 없음, 검수된 뜻이 부족해 만들 수 없습니다",
        )
        spoken.forEach { (type, d) -> assertEquals(type, d, desc("practice_gen_$type")) }
    }

    @Test fun `대상 단어가 10개 미만인 행은 Disabled이고 이유 문구가 보이며 눌러도 시작하지 않는다`() {
        rule.setContent { JlptTheme { PracticeHomeScreen(home, actions = PracticeHomeActions(onStartGenerated = { events += "gen:$it" })) } }
        listOf("kanji-reading", "meaning-ko-ja").forEach { type ->
            val c = rule.onNodeWithTag("practice_gen_$type").performScrollTo().fetchSemanticsNode().config
            assertTrue(type, c.contains(SemanticsProperties.Disabled))
            rule.onNodeWithTag("practice_gen_$type").performClick()
        }
        listOf("orthography", "meaning-ja-ko").forEach { type ->
            assertFalse(type, rule.onNodeWithTag("practice_gen_$type").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
        }
        assertTrue("비활성 행을 눌러도 아무 일도 없다", events.isEmpty())
        // 이유는 글자로 보인다(낭독은 행 노드 하나로 합쳐 두므로 분리 트리에서 찾는다)
        rule.onNodeWithText("대상 단어가 부족합니다", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("검수된 뜻이 부족해 만들 수 없습니다", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("단어 7개", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("활성 행에는 이유가 없다", 1, rule.onAllNodesWithText("대상 단어가 부족합니다", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `활성 행을 누르면 그 유형으로 시작한다`() {
        rule.setContent { JlptTheme { PracticeHomeScreen(home, actions = PracticeHomeActions(onStartGenerated = { events += "gen:$it" })) } }
        rule.onNodeWithTag("practice_gen_orthography").performScrollTo().performClick()
        rule.onNodeWithTag("practice_gen_meaning-ja-ko").performScrollTo().performClick()
        assertEquals(listOf("gen:orthography", "gen:meaning-ja-ko"), events)
        assertTrue(rule.onNodeWithTag("practice_gen_orthography").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test fun `generated가 비면 머리도 설명도 행도 없다`() {
        rule.setContent { JlptTheme { PracticeHomeScreen(home.copy(generated = emptyList(), types = listOf(PracticeTypeUi(Section.VOCAB, "kanji-reading", 12)))) } }
        assertEquals(0, rule.onAllNodesWithText("자동 생성 연습").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("practice_gen_orthography").fetchSemanticsNodes().size)
        rule.onNodeWithTag("practice_type_vocab_kanji-reading").assertIsDisplayed()
    }

    // --- S13 ---

    private val kanjiQ = genKanjiQ
    private val jaKoQ = genJaKoQ
    private val koJaQ = genKoJaQ

    private fun quizScreen(q: QuizQuestionUi, status: QuizStatus = QuizStatus.ANSWERING, chosen: Int? = null, actions: QuizActions = QuizActions()) =
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(status, index = 2, total = 10, question = q, chosenIndex = chosen), actions = actions) } }

    @Test fun `자동 생성 문항은 라벨이 지시문보다 앞에 있고 라벨은 누를 수 없고 포커스를 받는다`() {
        quizScreen(kanjiQ)
        rule.onNodeWithTag("quiz_generated_label").assertIsDisplayed()
        assertEquals("자동 생성 연습 문제", text("quiz_generated_label").text)
        val label = rule.onNodeWithTag("quiz_generated_label").getUnclippedBoundsInRoot()
        val instruction = rule.onNodeWithTag("quiz_instruction").getUnclippedBoundsInRoot()
        assertTrue("라벨이 지시문 위에 있다", label.bottom <= instruction.top)
        // 접근성 트리(읽기) 순서도 라벨이 앞이다: 부모 아래 자식 순서
        val ids = rule.onRoot().fetchSemanticsNode().let { root ->
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            walk(root).map { it.id }
        }
        assertTrue(ids.indexOf(rule.onNodeWithTag("quiz_generated_label").fetchSemanticsNode().id) < ids.indexOf(rule.onNodeWithTag("quiz_instruction").fetchSemanticsNode().id))
        val c = rule.onNodeWithTag("quiz_generated_label").fetchSemanticsNode().config
        assertFalse("동작이 없다", c.contains(SemanticsActions.OnClick))
        assertEquals("역할이 없다", null, c.getOrNull(SemanticsProperties.Role))
        assertEquals(null, c.getOrNull(SemanticsProperties.LiveRegion))
        rule.onNodeWithTag("quiz_generated_label").assertIsFocused()
    }

    @Test fun `고정 문항 S13에는 라벨이 없다`() {
        quizScreen(quizQuestion(qc("q-n5-vocab-0001")))
        assertEquals(0, rule.onAllNodesWithTag("quiz_generated_label").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("자동 생성 연습 문제").fetchSemanticsNodes().size)
        rule.onNodeWithTag("quiz_progress").assertIsFocused()
    }

    @Test fun `뜻 고르기의 선택지는 ko-KR이고 뜻 안 일본어 구간만 ja-JP이다`() {
        quizScreen(jaKoQ)
        val t = text("quiz_choice_2")
        assertTrue(t.text, t.text.endsWith("〜さん보다 정중한 호칭"))
        assertEquals("〜さん", locales(t, JpSpan))
        assertTrue(locales(t, KoSpan), locales(t, KoSpan).contains("보다 정중한 호칭"))
        // 일본어 구간이 없는 선택지는 일본어 로캘이 전혀 없다
        assertEquals("", locales(text("quiz_choice_1"), JpSpan))
        assertTrue(locales(text("quiz_choice_1"), KoSpan).contains("선생님"))
        // 지문(일본어 단어)은 기존대로 루비 + 일본어
        rule.onNodeWithTag("quiz_stem").assertIsDisplayed()
        assertEquals("밑줄 친 先生의 뜻으로 알맞은 것을 고르세요.", text("quiz_instruction").text)
    }

    @Test fun `단어 고르기의 지문은 ko-KR이고 지시문에 일본어 구간이 없고 선택지는 일본어이다`() {
        quizScreen(koJaQ)
        val stem = text("quiz_stem")
        assertEquals("선생님", stem.text)
        assertEquals("", locales(stem, JpSpan))
        assertTrue(locales(stem, KoSpan).contains("선생님"))
        assertEquals("이 뜻을 가진 일본어를 고르세요.", text("quiz_instruction").text)
        assertEquals("", locales(text("quiz_instruction"), JpSpan))
        assertEquals("표기와 읽기(후리가나 낭독) 두 구간이 일본어", "先生|せんせい", locales(text("quiz_choice_1"), JpSpan))
        assertEquals(0, rule.onAllNodesWithTag("ruby_underline", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `피드백의 관련 단어 보기는 단어 상세로 가고 칩은 provenance가 있을 때만이다`() {
        quizScreen(jaKoQ, QuizStatus.FEEDBACK, chosen = 1, actions = QuizActions(onWord = { events += "word:$it" }))
        rule.onNodeWithText("관련 단어 보기").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("word:jm:1"), events)
        rule.onNodeWithText(ProvenanceLabel.of(genProvenance)).performScrollTo().assertIsDisplayed()
        assertEquals("생성 해설은 검토 중 표시가 없다", 0, rule.onAllNodesWithText("해설 검토 중").fetchSemanticsNodes().size)
        // 오답을 고른 뒤: 고른 오답의 이유가 해설에 있다
        rule.onNodeWithText("「〜さん」보다 정중한 호칭은 다른 단어의 뜻입니다.").performScrollTo().assertIsDisplayed()
    }

    @Test fun `provenance가 null인 자동 생성 문항에는 칩이 없고 관련 단어 보기는 있다`() {
        quizScreen(kanjiQ, QuizStatus.FEEDBACK, chosen = 1, actions = QuizActions(onWord = { events += "word:$it" }))
        rule.onNodeWithText("관련 단어 보기").performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText(ProvenanceLabel.of(genProvenance)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("해설 검토 중").fetchSemanticsNodes().size)
        // 같은 화면에서 칩 자리에 provenance가 있는 고정 문항은 칩이 있다
        assertTrue(kanjiQ.provenance == null)
    }

    @Test fun `고정 문항에는 관련 단어 보기 줄이 없다`() {
        quizScreen(quizQuestion(qc("q-n5-vocab-0001", targets = listOf("jm:1"))), QuizStatus.FEEDBACK, chosen = 1)
        assertEquals(0, rule.onAllNodesWithText("관련 단어 보기").fetchSemanticsNodes().size)
    }

    // --- S15 ---

    private fun resultState(items: List<QuizReviewItemUi>, type: String = "kanji-reading", reviewIndex: Int? = null, links: Map<String, List<TargetLink>> = emptyMap()) =
        QuizResultUiState(
            QuizResultStatus.LOADED, QuizMode.PRACTICE, JlptLevel.N5, type, Score(listOf(SectionScore(Section.VOCAB, 1, items.size)), emptyList(), 1, items.size, 0),
            filter = ResultFilter.ALL, items = items, reviewIndex = reviewIndex, targetLinks = links,
        )

    private fun genItem(q: QuizQuestionUi, i: Int = 0, chosen: Int? = 1) = QuizReviewItemUi(i, q, chosen, chosen == q.answerIndex, false)

    @Test fun `생성 시도의 결과 제목은 자동 생성 접두이고 낭독은 쉼표이다`() {
        rule.setContent { JlptTheme { QuizResultScreen(resultState(listOf(genItem(kanjiQ)))) } }
        assertEquals("자동 생성 · N5 한자 읽기 연습 결과", text("result_title").text)
        assertEquals("자동 생성, N5 한자 읽기 연습 결과", desc("result_title"))
    }

    @Test fun `뜻 고르기 시도의 제목은 뜻 고르기 이름이고 고정 문항 시도에는 접두가 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(resultState(listOf(genItem(jaKoQ)), type = "meaning-ja-ko")) } }
        assertEquals("자동 생성 · N5 뜻 고르기 연습 결과", text("result_title").text)
    }

    @Test fun `고정 문항 시도의 제목에는 접두가 없고 같은 코드여도 구분된다`() {
        rule.setContent { JlptTheme { QuizResultScreen(resultState(listOf(genItem(quizQuestion(qc("q-n5-vocab-0001")))))) } }
        assertEquals("N5 한자 읽기 연습 결과", text("result_title").text)
        assertEquals(null, desc("result_title"))
    }

    @Test fun `해설 보기에도 라벨이 맨 위에 있고 고정 문항에는 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(resultState(listOf(genItem(kanjiQ)), reviewIndex = 0)) } }
        rule.onNodeWithTag("quiz_generated_label").assertIsDisplayed()
        val label = rule.onNodeWithTag("quiz_generated_label").getUnclippedBoundsInRoot()
        assertTrue(label.bottom <= rule.onNodeWithTag("quiz_instruction").getUnclippedBoundsInRoot().top)
    }

    @Test fun `해설 보기에 라벨이 없는 고정 문항`() {
        rule.setContent { JlptTheme { QuizResultScreen(resultState(listOf(genItem(quizQuestion(qc("q-n5-vocab-0001")))), reviewIndex = 0)) } }
        assertEquals(0, rule.onAllNodesWithTag("quiz_generated_label").fetchSemanticsNodes().size)
    }

    @Test fun `해설 보기의 관련 단어 보기는 VOCAB 링크를 연다`() {
        val links = mapOf(jaKoQ.id to listOf(TargetLink(TargetKind.VOCAB, "jm:1", "先生")))
        rule.setContent {
            JlptTheme { QuizResultScreen(resultState(listOf(genItem(jaKoQ)), "meaning-ja-ko", 0, links), QuizResultActions(onTarget = { events += "${it.kind}:${it.id}" })) }
        }
        rule.onNodeWithText("관련 단어 보기").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("VOCAB:jm:1"), events)
        assertEquals("기존 링크 블록은 이 줄로 대신한다", 0, rule.onAllNodesWithTag("result_links").fetchSemanticsNodes().size)
        rule.onNodeWithText(ProvenanceLabel.of(genProvenance)).performScrollTo().assertIsDisplayed()
    }

    @Test fun `해설 보기에서 단어가 콘텐츠에서 빠졌으면 관련 단어 보기 줄이 없고 provenance null이면 칩도 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(resultState(listOf(genItem(kanjiQ)), reviewIndex = 0, links = mapOf(kanjiQ.id to emptyList()))) } }
        assertEquals(0, rule.onAllNodesWithText("관련 단어 보기").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("해설 검토 중").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(ProvenanceLabel.of(genProvenance)).fetchSemanticsNodes().size)
    }
}
