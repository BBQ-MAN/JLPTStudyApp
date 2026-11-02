package com.jlptstudy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// ui-design.md 3.2. 글꼴은 기기 글꼴(FontFamily.Default) + 로캘로 시작한다(AR-U05).
private val KoLocale = LocaleList("ko-KR")
private val JaLocale = LocaleList("ja-JP")
/** 검색 입력란의 IME 로캘 힌트(ui-design 4.2). */
val JaKoHintLocales = LocaleList("ja-JP,ko-KR")

/** 한국어 구간에 거는 SpanStyle. TalkBack은 TextStyle이 아니라 이 구간의 로캘만 받는다. */
val KoSpan: SpanStyle = SpanStyle(localeList = KoLocale)

/** 일본어 구간에 거는 미리 만든 SpanStyle. 화면 코드에서 로캘을 직접 만들지 않는다. */
val JpSpan: SpanStyle = SpanStyle(localeList = JaLocale)

private val KoParagraph = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
private val KoHeading = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)

private fun ko(size: Int, line: Int, weight: Int = 400, lineBreak: LineBreak = KoParagraph) = TextStyle(
    fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight),
    localeList = KoLocale, lineBreak = lineBreak,
)

val KoTypography = Typography(
    displaySmall = ko(36, 44), headlineMedium = ko(28, 36, lineBreak = KoHeading), headlineSmall = ko(24, 32, lineBreak = KoHeading),
    titleLarge = ko(22, 28), titleMedium = ko(16, 24, 500), titleSmall = ko(14, 20, 500),
    bodyLarge = ko(16, 26), bodyMedium = ko(14, 22), bodySmall = ko(12, 18),
    labelLarge = ko(14, 20, 500), labelMedium = ko(12, 16, 500), labelSmall = ko(11, 16, 500),
)

/** 일본어 스타일 묶음(3.2.2). 굵기 400 미만을 쓰지 않는다. 행두·행말 금칙을 건다. */
@Immutable
class JpTypography(
    val kanjiHero: TextStyle, val headword: TextStyle, val title: TextStyle,
    val bodyLarge: TextStyle, val body: TextStyle, val bodySmall: TextStyle,
) {
    /** 후리가나 글자 크기 = max(기반 × 0.5, 11 sp). */
    fun rubySize(base: TextUnit): TextUnit = (base * 0.5f).let { if (it.value > 11f) it else 11.sp }
}

private val JaLineBreak = LineBreak.Paragraph.copy(
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase,
)

private fun jp(size: Int, line: Int, weight: Int = 400) = TextStyle(
    fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight),
    localeList = JaLocale, lineBreak = JaLineBreak,
)

val DefaultJpTypography = JpTypography(
    kanjiHero = jp(88, 104), headword = jp(45, 56), title = jp(24, 36, 500),
    bodyLarge = jp(20, 32), body = jp(16, 26), bodySmall = jp(14, 22),
)

val LocalJpTypography = staticCompositionLocalOf { DefaultJpTypography }
