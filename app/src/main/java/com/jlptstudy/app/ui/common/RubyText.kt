package com.jlptstudy.app.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan
import com.jlptstudy.app.ui.theme.LocalJpTypography

private const val NO_LINE_START = "、。，．，」』）】〉》〕ー・！？!?)]ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ々"
private const val NO_LINE_END = "「『（【〈《〔([("

private fun isWordChar(cp: Int) = cp < 128 && Character.isLetterOrDigit(cp) || cp in 0xFF10..0xFF19

/**
 * 루비 없는 구간을 글자 단위로 쪼갠다(줄 끝에서 감기게). 루비 구간은 그대로.
 * 행두 금칙 문자는 앞 글자에, 행말 금칙 문자는 뒤 글자에 붙이고, ASCII·숫자 연속은 한 덩어리로 둔다.
 * 구간 경계를 넘는 금칙은 groupPieces가 묶는다.
 */
internal fun splitPlain(segments: List<RubySegment>): List<RubySegment> = segments.flatMap { seg ->
    if (seg.ruby != null) return@flatMap listOf(seg)
    val chunks = mutableListOf<StringBuilder>()
    var prev = -1
    for (cp in seg.base.codePoints().toArray()) {
        val glue = prev >= 0 && (
            NO_LINE_START.indexOf(cp.toChar()) >= 0 && cp < 0x10000 ||
                NO_LINE_END.indexOf(prev.toChar()) >= 0 && prev < 0x10000 ||
                isWordChar(prev) && isWordChar(cp))
        if (glue) chunks.last().appendCodePoint(cp) else chunks.add(StringBuilder().appendCodePoint(cp))
        prev = cp
    }
    chunks.map { seg.copy(base = it.toString()) }
}

/** 구간 경계를 넘는 금칙: 행두 금칙 조각은 앞 조각과, 행말 금칙 조각은 뒤 조각과 한 묶음(줄바꿈 불가)으로 둔다. */
internal fun groupPieces(pieces: List<RubySegment>): List<List<RubySegment>> {
    val groups = mutableListOf<MutableList<RubySegment>>()
    for (p in pieces) {
        val prev = groups.lastOrNull()?.last()
        val glue = prev != null && (
            p.ruby == null && p.base.isNotEmpty() && NO_LINE_START.indexOf(p.base.first()) >= 0 ||
                prev.ruby == null && prev.base.isNotEmpty() && NO_LINE_END.indexOf(prev.base.last()) >= 0)
        if (glue) groups.last().add(p) else groups.add(mutableListOf(p))
    }
    return groups
}

/**
 * 후리가나 텍스트(ui-design 5.2). 구간마다 본문 위에 읽기를 얹고, 줄바꿈은 구간 경계에서만 한다.
 * emphasis 구간은 밑줄 2 dp + 굵기 500. showRuby = false면 읽기와 위 여백을 없앤다.
 * reserveRubyLine = true(플래시카드 표제어, 5.1)면 읽기를 안 그려도 읽기 줄 높이와 읽기 폭을 남기고,
 * showRuby가 바뀌면 읽기를 150 ms 페이드한다(3.6). 폭 기준 글자 축소(긴 표기)는 호출하는 쪽(Flashcard)이 style로 정한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RubyText(
    segments: List<RubySegment>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalJpTypography.current.bodyLarge,
    showRuby: Boolean = true,
    reserveRubyLine: Boolean = false,
) {
    val jp = LocalJpTypography.current
    val rubyAlpha by animateFloatAsState(if (showRuby) 1f else 0f, tween(150, easing = FastOutSlowInEasing), label = "ruby")
    val rubySize = jp.rubySize(style.fontSize)
    val rubyLine = rubySize * 1.2f
    val rubyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rubyStyle = remember(style, rubySize, rubyColor) {
        style.copy(
            fontSize = rubySize, lineHeight = rubyLine, fontWeight = FontWeight.Normal,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            color = rubyColor,
        )
    }
    val groups = remember(segments) { groupPieces(splitPlain(segments)) }
    val underline = MaterialTheme.colorScheme.onSurface
    val spoken = remember(segments, showRuby) {
        buildAnnotatedString {
            withStyle(JpSpan) { append(segments.joinToString("") { it.base }) }
            if (showRuby && segments.any { it.ruby != null }) {
                withStyle(KoSpan) { append(", 읽기 ") }
                withStyle(JpSpan) { append(segments.joinToString("") { it.ruby ?: it.base }) }
            }
        }
    }
    if (!reserveRubyLine && (!showRuby || segments.none { it.ruby != null }) && segments.none { it.emphasis }) {
        // 읽기도 강조도 없으면 Text 하나로 그린다. 강조(2 dp 밑줄)는 조각 경로로 그려 두 경로의 밑줄이 같다.
        val plain = remember(segments) { buildAnnotatedString { withStyle(JpSpan) { append(segments.joinToString("") { it.base }) } } }
        Text(plain, modifier.clearAndSetSemantics { text = spoken }, style = style, color = LocalContentColor.current)
        return
    }
    FlowRow(
        modifier = modifier.clearAndSetSemantics { text = spoken },
        horizontalArrangement = Arrangement.Start,
    ) {
        groups.forEach { group ->
            Row {
                group.forEach { seg -> RubyPiece(seg, style, rubyStyle, rubyLine, underline, showRuby, reserveRubyLine, rubyAlpha) }
            }
        }
    }
}

@Composable
private fun RubyPiece(
    seg: RubySegment, style: TextStyle, rubyStyle: TextStyle, rubyLine: TextUnit, underline: Color, showRuby: Boolean,
    reserve: Boolean, rubyAlpha: Float,
) {
    val hasRuby = seg.ruby != null && (showRuby || reserve)
    val space = showRuby || reserve
    // 읽기 baseline을 본문 한자 윗선(baseline - 0.90em, 근사)에서 2 dp 위에 둔다. 글꼴 ascent·줄 간격에 따라 간격이 변하지 않게 baseline으로 배치한다.
    Layout(content = {
        if (seg.ruby != null && hasRuby) {
            // 읽기를 그리지 않는 앞면에서도 폭을 같게 재려고 투명으로 둔다(앞·뒷면 표제어 위치 고정).
            Text(seg.ruby, style = rubyStyle, modifier = Modifier.alpha(if (reserve) rubyAlpha else 1f).semantics { invisibleToUser() })
        }
        JpText(seg.base, style = if (seg.emphasis) style.copy(fontWeight = FontWeight.Medium) else style)
        // 밑줄은 읽기가 더 넓어도 구간 전체 폭에 이어지도록 Layout 폭으로 그린다.
        if (seg.emphasis) Box(Modifier.background(underline).testTag("ruby_underline"))
    }) { ms, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val ruby = if (hasRuby) ms[0].measure(loose) else null
        val base = ms[if (ruby != null) 1 else 0].measure(loose)
        val inkTop = (base[FirstBaseline] - 0.90f * style.fontSize.toPx()).roundToInt()
        val reserved = if (space) (rubyLine.toPx() + 2.dp.toPx()).roundToInt() else 0
        val w = maxOf(base.width, ruby?.width ?: 0)
        val line = if (seg.emphasis) ms.last().measure(Constraints.fixed(w, 2.dp.roundToPx())) else null
        layout(w, reserved + base.height - inkTop) {
            base.place((w - base.width) / 2, reserved - inkTop)
            ruby?.place((w - ruby.width) / 2, reserved - 2.dp.roundToPx() - ruby[FirstBaseline])
            line?.place(0, reserved - inkTop + base.height - 3.dp.roundToPx())
        }
    }
}
