package com.jlptstudy.app.ui.detail

import android.provider.Settings
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.ui.dictionary.BrowseItem
import com.jlptstudy.app.ui.dictionary.BrowseTab
import com.jlptstudy.app.ui.dictionary.sampleWordRefs
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 합성 경로 픽스처(source_id authored). 실제 필순 데이터가 아니다. */
val syntheticStrokes = listOf("M20,25L89,25", "M54.5,10L54.5,99", "M25,60C40,70,70,70,85,60", "m30 85l50 0")

/** 획이 많은 한자(18획급)에서 번호 겹침을 보기 위한 합성 픽스처: 시작점이 서로 5~8 단위 안에 몰려 있다. */
val denseSyntheticStrokes = List(18) { i -> "M${20 + (i % 6) * 6},${20 + (i / 6) * 7}L${30 + (i % 6) * 10},${90 - (i / 6) * 5}" }

class StrokeGeometryTest {
    @Test fun `첫 M과 m의 좌표를 낸다`() {
        assertEquals(52.25f to 14.5f, StrokeGeometry.start("M52.25,14.5c1,2,3,4,5,6"))
        assertEquals(10f to 20f, StrokeGeometry.start("m10 20l5 5"))
        assertEquals(-1.5f to -0.5f, StrokeGeometry.start("M-1.5-.5L3,3"))
    }

    @Test fun `첫 명령이 M이나 m이 아니거나 좌표가 없으면 null이다`() {
        assertNull(StrokeGeometry.start("L10,20"))
        assertNull(StrokeGeometry.start("M"))
        assertNull(StrokeGeometry.start("M10"))
        assertNull(StrokeGeometry.start(""))
        assertNull(StrokeGeometry.start("Mx,y"))
    }

    @Test fun `화면 코드에 출처 이름 문자열이 없다`() {
        val dir = File("src/main/java/com/jlptstudy/app/ui")
        val hits = dir.walkTopDown().filter { it.extension == "kt" && "KanjiVG" in it.readText() }.map { it.name }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}

@RunWith(RobolectricTestRunner::class)
class StrokeRepositoryTest {
    private fun <T> withDb(body: suspend (RoomContentRepository, (String) -> Unit, String) -> T): T = runBlocking {
        val db = ContentDatabase.open(RuntimeEnvironment.getApplication())
        try {
            val repo = RoomContentRepository(db.contentDao())
            val lit = repo.browse(BrowseTab.KANJI, emptySet(), "").filterIsInstance<BrowseItem.Kanji>().first().literal
            val put = { json: String ->
                db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO kanji_stroke(literal, paths_json, source_id) VALUES (?, ?, 'authored')", arrayOf(lit, json))
            }
            body(repo, put, lit)
        } finally { db.close() }
    }

    @Test fun `정상 경로는 읽히고 행 없음 JSON 깨짐 경로 깨짐은 null이다`() = withDb { repo, put, lit ->
        assertNull(repo.kanjiDetail(lit)!!.strokes) // 행 없음
        put("""["M1,2L3,4","M5,6l7,8"]""")
        assertEquals(listOf("M1,2L3,4", "M5,6l7,8"), repo.kanjiDetail(lit)!!.strokes)
        put("not json")
        assertNull(repo.kanjiDetail(lit)!!.strokes)
        put("[]")
        assertNull(repo.kanjiDetail(lit)!!.strokes)
        put("""["M1,2L3,4","M1,2 l1,abc"]""") // 경로 하나라도 해석 실패면 전부 버린다
        assertNull(repo.kanjiDetail(lit)!!.strokes)
        assertNotNull(repo.kanjiDetail(lit)) // 나머지 상세는 그대로
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class StrokeOrderUiTest {
    @get:Rule val rule = createComposeRule()

    private val state = KanjiDetailUiState(
        DetailStatus.LOADED, "直", JlptLevel.N5, 8, listOf("チョク"), listOf("ただ(ちに)"), listOf("직"), sampleWordRefs,
        strokes = syntheticStrokes, strokeSourceName = "합성 필순 출처",
    )
    private val playMs = syntheticStrokes.size * 400L + (syntheticStrokes.size - 1) * 150L + 100

    private fun show(actions: DetailActions = DetailActions()) {
        rule.mainClock.autoAdvance = false
        rule.setContent { JlptTheme { KanjiDetailScreen(state, actions) } }
    }

    /** 시계를 멈춘 채로는 입력 처리와 재구성이 몇 프레임 뒤에 반영되므로 프레임을 보낸다. */
    private fun click(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun figure() = rule.onNodeWithTag("stroke_figure", useUnmergedTree = true)
    private fun number(n: Int) = rule.onNodeWithTag("stroke_number_$n", useUnmergedTree = true)

    @Test fun `처음에는 그리지 않고 완성 그림과 시작 버튼이 보인다`() {
        show()
        rule.mainClock.advanceTimeBy(5_000) // 자동 재생이면 여기서 DONE이 된다
        rule.onNodeWithText("획 순서대로 보기").assertIsDisplayed()
        rule.onNodeWithText("필순 보기를 마쳤습니다").assertDoesNotExist()
        number(1).assertDoesNotExist()
        rule.onNodeWithText("획 번호").assertIsDisplayed()
    }

    @Test fun `누르면 멈추기 끝나면 다시 보기와 알림이 나온다`() {
        show()
        click("획 순서대로 보기")
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("멈추기").assertIsDisplayed()
        rule.onNodeWithText("필순 보기를 마쳤습니다").assertDoesNotExist() // 그리는 동안은 알리지 않는다
        rule.mainClock.advanceTimeBy(playMs)
        rule.onNodeWithText("다시 보기").assertIsDisplayed()
        rule.onNodeWithText("필순 보기를 마쳤습니다").assertIsDisplayed()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertIsDisplayed()
        click("다시 보기")
        rule.onNodeWithText("멈추기").assertIsDisplayed()
        rule.onNodeWithText("필순 보기를 마쳤습니다").assertDoesNotExist()
    }

    @Test fun `멈추기를 누르면 처음 상태로 돌아간다`() {
        show()
        click("획 순서대로 보기")
        rule.mainClock.advanceTimeBy(500)
        click("멈추기")
        rule.mainClock.advanceTimeBy(playMs * 2)
        rule.onNodeWithText("획 순서대로 보기").assertIsDisplayed()
        rule.onNodeWithText("필순 보기를 마쳤습니다").assertDoesNotExist()
    }

    @Test fun `그림 노드는 한국어 설명만 갖는다`() {
        show()
        val d = figure().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("필순 그림, 4획", d)
        assertFalse(Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]").containsMatchIn(d))
        assertFalse(figure().fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
    }

    @Test fun `획 번호 스위치는 기본 꺼짐이고 켜면 번호가 붙는다`() {
        show()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsOff()
        click("획 번호")
        rule.mainClock.advanceTimeBy(100)
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsOn()
        (1..4).forEach { number(it).assertExists() }
    }

    @Test fun `획 번호 스위치는 상태 복원 뒤에도 켜져 있다`() {
        val t = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        t.setContent { JlptTheme { KanjiDetailScreen(state, DetailActions()) } }
        rule.onNodeWithText("획 번호").performClick()
        t.emulateSavedInstanceStateRestore()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsOn()
    }

    @Test fun `애니메이션 삭제면 버튼과 스위치 없이 획 번호가 붙은 완성 그림이다`() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            show()
            rule.onNodeWithText("획 순서대로 보기").assertDoesNotExist()
            rule.onNodeWithText("획 번호").assertDoesNotExist()
            (1..4).forEach { number(it).assertExists() }
        } finally {
            Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test fun `출처 줄은 넘겨 받은 이름을 보이고 누르면 이동하며 이름이 없으면 숨는다`() {
        var n = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { JlptTheme { KanjiDetailScreen(state, DetailActions(onStrokeSource = { n++ })) } }
        click("필순: 합성 필순 출처")
        assertEquals(1, n)
    }

    @Test fun `출처 이름이 null이면 출처 줄이 없다`() {
        rule.setContent { JlptTheme { KanjiDetailScreen(state.copy(strokeSourceName = null)) } }
        rule.onNodeWithText("필순: ", substring = true).assertDoesNotExist()
        rule.onNodeWithText("획 순서대로 보기").assertIsDisplayed()
    }
}
