package com.jlptstudy.app.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.LocalJpTypography

/** 일본어 텍스트. 글리프 선택(style)과 접근성 낭독 언어(전체 범위 LocaleSpan)에 모두 ja-JP를 건다. */
@Composable
fun JpText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalJpTypography.current.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val annotated: AnnotatedString = remember(text) { buildAnnotatedString { withStyle(JpSpan) { append(text) } } }
    Text(
        annotated, modifier = modifier, style = style,
        color = if (color == Color.Unspecified) LocalContentColor.current else color, maxLines = maxLines, overflow = overflow,
    )
}
