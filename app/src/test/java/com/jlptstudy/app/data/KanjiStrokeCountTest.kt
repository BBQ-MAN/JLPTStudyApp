package com.jlptstudy.app.data

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.ui.detail.DetailStatus
import com.jlptstudy.app.ui.detail.KanjiDetailScreen
import com.jlptstudy.app.ui.detail.KanjiDetailViewModel
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * U-9(잠정 A): KANJIDIC2 획수와 KanjiVG 획 수가 다른 한자(飴 13/14, 噌 14/15)는 필순 그림이 있으면
 * 상세 화면의 '{n}획'과 TalkBack 문구를 kanji_stroke의 paths 개수에 맞춘다. 실제 번들 에셋 DB로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class KanjiStrokeCountTest {
    @get:Rule val rule = createComposeRule()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun check(literal: String, dictionary: Int, paths: Int) {
        val db = runBlocking { ContentDatabase.open(RuntimeEnvironment.getApplication()) }
        try {
            val stored = db.openHelper.readableDatabase.query(
                "SELECT stroke_count, (SELECT json_array_length(paths_json) FROM kanji_stroke WHERE literal = '$literal') FROM kanji WHERE literal = '$literal'",
            ).use { c -> c.moveToFirst(); c.getInt(0) to c.getInt(1) }
            assertEquals("번들 데이터가 U-9 전제(KANJIDIC2 획수 / paths 개수)와 같다: $literal", dictionary to paths, stored)

            val repo = RoomContentRepository(db.contentDao())
            val detail = runBlocking { repo.kanjiDetail(literal)!! }
            assertEquals(paths, detail.strokes!!.size)
            assertEquals("저장소의 획수 = paths 개수", paths, detail.strokeCount)

            val vm = KanjiDetailViewModel(literal, repo)
            val state = runBlocking { vm.state.first { it.status != DetailStatus.LOADING } }
            assertEquals(DetailStatus.LOADED, state.status)
            assertEquals(paths, state.strokeCount)
            rule.setContent { JlptTheme { KanjiDetailScreen(state) } }
            rule.onNodeWithText("${paths}획").assertExists()
            rule.onNodeWithTag("stroke_figure").assertContentDescriptionEquals("필순 그림, ${paths}획")
        } finally {
            db.close()
        }
    }

    @Test fun `飴 상세의 획은 14이다(KANJIDIC2 13)`() = check("飴", 13, 14)
    @Test fun `噌 상세의 획은 15이다(KANJIDIC2 14)`() = check("噌", 14, 15)
}
