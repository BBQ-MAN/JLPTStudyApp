package com.jlptstudy.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RubyTextWrapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `루비 없는 구간은 쪼개고 루비 구간은 그대로 둔다`() {
        val out = splitPlain(listOf(RubySegment("私", "わたし"), RubySegment("は毎日", emphasis = true)))
        assertEquals(listOf("私", "は", "毎", "日"), out.map { it.base })
        assertEquals(listOf("わたし", null, null, null), out.map { it.ruby })
        assertEquals(listOf(false, true, true, true), out.map { it.emphasis })
    }

    @Test fun `금칙 문자와 ASCII 단어는 이웃과 한 덩어리다`() {
        val out = splitPlain(listOf(RubySegment("N5は「食べる」ね。ー JLPT10"))).map { it.base }
        assertEquals(listOf("N5", "は", "「食", "べ", "る」", "ね。ー", " ", "JLPT10"), out)
    }

    @Test fun `루비 구간 앞뒤 금칙 조각은 한 묶음이다`() {
        val g = groupPieces(splitPlain(listOf(
            RubySegment("「"), RubySegment("先生", "せんせい"), RubySegment("」と"), RubySegment("今日は"), RubySegment("雨", "あめ"), RubySegment("。"),
        ))).map { it.joinToString("") { s -> s.base } }
        assertEquals(listOf("「先生」", "と", "今", "日", "は", "雨。"), g)
    }

    @Test fun `읽기가 있는 문장도 좁은 폭에서 2줄로 감긴다`() {
        val one = listOf(RubySegment("学", "が"))
        val long = listOf(RubySegment("学", "が"), RubySegment("学".repeat(19)))
        var px by mutableStateOf(0)
        rule.setContent {
            JlptTheme {
                Column {
                    RubyText(one, Modifier.testTag("one").onSizeChanged { px = it.width })
                    val w = with(LocalDensity.current) { (px * 10 + 1).toDp() }
                    Box(Modifier.width(w)) { RubyText(long, Modifier.testTag("long")) }
                }
            }
        }
        val h1 = rule.onNodeWithTag("one").fetchSemanticsNode().size.height
        assertEquals(2 * h1, rule.onNodeWithTag("long").fetchSemanticsNode().size.height)
    }

    @Test fun `읽기가 하나도 없으면 읽기 자리 여백 없이 Text 하나 높이다`() {
        var plainH = 0
        var withRubyH = 0
        rule.setContent {
            JlptTheme {
                Column {
                    RubyText(listOf(RubySegment("学")), Modifier.onSizeChanged { plainH = it.height })
                    RubyText(listOf(RubySegment("学", "が")), Modifier.onSizeChanged { withRubyH = it.height })
                }
            }
        }
        rule.waitForIdle()
        assertTrue("plain=$plainH ruby=$withRubyH", plainH in 1 until withRubyH)
    }
}
