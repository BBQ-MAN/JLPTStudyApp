package com.jlptstudy.app.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.jlptstudy.app.ui.common.KunText
import com.jlptstudy.app.ui.common.kunDisplay
import com.jlptstudy.app.ui.common.infoLabelWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.JpSpans
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.common.RubyText
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing

private const val MAX_MEANINGS = 3

/** 표제어 아래 뒷면 영역이 이보다 낮아질 카드는 카드 전체를 스크롤한다(영역 123 dp는 읽기·품사·뜻 하나도 못 담는다). */
private val COMPACT_BODY_MIN = 180.dp

/**
 * 플래시카드(ui-design 5.1). 표제어(testTag review_headword)는 앞·뒷면 모두 같은 y·같은 글자 크기다:
 * 위치는 카드 높이의 40% 지점 기준으로 앞면에서 정하고, 뒷면 정보는 그 아래에 나타난다.
 * 내용이 카드보다 길면 표제어 아래 영역만 스크롤한다(스크롤 막대 표시).
 */
@Composable
fun Flashcard(
    card: ReviewCardUi,
    revealed: Boolean,
    remaining: Int,
    prefs: ReviewPrefs,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    onChipClick: ((MeaningUi) -> Unit)? = null,
    onGrammarDetail: ((String) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    bottomInset: Dp = 0.dp,
) {
    val jp = LocalJpTypography.current
    val density = LocalDensity.current
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(Modifier.padding(Spacing.lg)) {
            val cardHeight = maxHeight
            val maxWidthPx = constraints.maxWidth
            val headEstimate: Dp = with(density) {
                when (card) {
                    is ReviewCardUi.Kanji -> jp.kanjiHero.lineHeight.toDp()
                    is ReviewCardUi.Grammar -> jp.headword.lineHeight.toDp()
                    is ReviewCardUi.Vocab ->
                        if (card.direction == VocabDirection.JA_TO_KO) {
                            jp.headword.lineHeight.toDp() + (jp.rubySize(jp.headword.fontSize) * 1.2f).toDp() + 2.dp
                        } else {
                            MaterialTheme.typography.headlineMedium.lineHeight.toDp()
                        }
                }
            }
            val chrome = 32.dp + if (header != null) 40.dp else 0.dp
            val topPad = (cardHeight * 0.4f - chrome - headEstimate / 2).coerceAtLeast(0.dp)
            val frontText = remember(card, remaining, prefs.bodyFurigana) { frontText(card, remaining, prefs.bodyFurigana) }
            // 뒷면 안의 누를 것(칩·더 보기·자세히)은 카드 낭독 노드의 사용자 지정 동작으로 낸다: 뒷면도 앞면처럼 clearAndSetSemantics 단일 노드다.
            var allMeanings by remember(card.cardId) { mutableStateOf(false) }
            var grammarOverflow by remember(card.cardId) { mutableStateOf(false) }
            val backText = remember(card, allMeanings) { backText(card, allMeanings) }
            val backActions = if (!revealed) emptyList() else buildList {
                val chip = stringResource(R.string.review_chip_detail)
                when (card) {
                    is ReviewCardUi.Vocab -> {
                        val shown = if (allMeanings) card.meanings else card.meanings.take(MAX_MEANINGS)
                        if (onChipClick != null) shown.forEachIndexed { i, m ->
                            add(CustomAccessibilityAction(if (shown.size == 1) chip else stringResource(R.string.review_chip_detail_n, i + 1)) { onChipClick(m); true })
                        }
                        val more = card.meanings.size - shown.size
                        if (more > 0) add(CustomAccessibilityAction(stringResource(R.string.review_more_meanings, more)) { allMeanings = true; true })
                    }
                    is ReviewCardUi.Grammar -> {
                        if (grammarOverflow && onGrammarDetail != null) {
                            add(CustomAccessibilityAction(stringResource(R.string.review_grammar_detail_action)) { onGrammarDetail(card.cardId.substringAfter(':')); true })
                        }
                        if (onChipClick != null) add(CustomAccessibilityAction(chip) { onChipClick(card.provenance); true })
                    }
                    is ReviewCardUi.Kanji -> Unit
                }
            }
            // 표제어는 고정, 그 아래 뒷면 정보만 스크롤한다(ui-design 5.1). 낮은 카드에서는 표제어 위 여백을 먼저 줄인다.
            val scroll = rememberScrollState()
            // 카드가 너무 낮으면(낮은 높이·글꼴 200%) 뒷면 영역이 보이지 않으므로 카드 전체를 스크롤한다. 스크롤 0에서 표제어 위치는 같다.
            // 문법 카드는 형식(줄바꿈 가능)과 예문이 표제어와 뒷면 영역 사이에 있어, 둘을 실측한 높이를 더해 남은 높이를 따진다.
            val measurer = rememberTextMeasurer()
            val grammarExtra: Dp = (card as? ReviewCardUi.Grammar)?.let { g ->
                with(density) {
                    val c = Constraints(maxWidth = maxWidthPx)
                    val title = measurer.measure(AnnotatedString(g.title), jp.headword.copy(fontSize = jp.headword.fontSize * 0.6f), constraints = c).size.height
                    val ex = measurer.measure(AnnotatedString(g.example.joinToString("") { it.base }), jp.bodyLarge, constraints = c).size.height
                    (title - jp.headword.lineHeight.roundToPx()).coerceAtLeast(0).toDp() + ex.toDp() + (jp.rubySize(jp.bodyLarge.fontSize) * 1.2f).toDp() + Spacing.xl
                }
            } ?: 0.dp
            var preHeadPx by remember { mutableIntStateOf(0) }
            var headerPx by remember { mutableIntStateOf(0) }
            val chromeSeen = maxOf(chrome, with(density) { headerPx.toDp() }) // 글꼴 200%의 진행 머리글은 추정(40 dp)보다 훨씬 높다
            var bodyTopPx by remember(card.cardId) { mutableIntStateOf(0) }
            // 표제어 덩어리의 실제 높이(뒷면 영역 시작 - 위 여백 끝). 추정보다 크면(문법 예문이 줄바꿈될 때) 실측을 쓴다.
            val headSeen = maxOf(headEstimate + grammarExtra, with(density) { (bodyTopPx - preHeadPx).coerceAtLeast(0).toDp() })
            val compact = revealed && cardHeight < headSeen + chromeSeen + COMPACT_BODY_MIN // 위 여백을 다 줄여도 뒷면 영역이 모자랄 때만
            // 카드 전체 스크롤일 때는 뒤집으면 뒷면 영역이 시작하는 곳이 보이게 끌어올린다(표제어는 위로 올라가되 카드 높이의 절반까지는 남긴다). 앞면은 0, 표제어 y 고정의 예외.
            val cardPx = with(density) { cardHeight.toPx() }
            LaunchedEffect(card.cardId, revealed, compact, preHeadPx, bodyTopPx) {
                if (revealed && compact && bodyTopPx > 0) {
                    scroll.animateScrollTo((bodyTopPx - minOf(bodyTopPx - preHeadPx, (cardPx * 0.5f).toInt())).coerceAtLeast(preHeadPx))
                } else if (!revealed) scroll.scrollTo(0)
            }
            val bar = MaterialTheme.colorScheme.outline
            val fade = MaterialTheme.colorScheme.surfaceContainerLow
            val barAlpha = scrollbarAlpha(scroll)
            Column(
                Modifier.fillMaxSize()
                    .then(
                        if (compact) Modifier.scrollCue(scroll, barAlpha, bar, fade).verticalScroll(scroll) else Modifier,
                    )
                    .pointerInput(revealed) { if (!revealed) detectTapGestures { onReveal() } }
                    .testTag("review_card")
                    .then(
                        // 같은 노드의 text만 바꾼다: 문구가 바뀔 때 TalkBack이 한 번 읽는다(ui-design 4.3). 접근성 포커스는 옮기지 않는다.
                        if (revealed) Modifier.clearAndSetSemantics { text = backText; liveRegion = LiveRegionMode.Polite; customActions = backActions }
                        else Modifier.clearAndSetSemantics { text = frontText; liveRegion = LiveRegionMode.Polite },
                    ),
            ) {
                Column(Modifier.onSizeChanged { preHeadPx = it.height }) {
                    Column(Modifier.onSizeChanged { headerPx = it.height }) {
                        header?.invoke()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(kindLabel(card), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LevelChip(levelOf(card))
                        }
                    }
                    Spacer(Modifier.height((topPad).coerceAtMost((cardHeight - chrome - headEstimate - grammarExtra - 120.dp).coerceAtLeast(0.dp))))
                }
                when (card) {
                    is ReviewCardUi.Kanji -> {
                        JpText(card.literal, Modifier.testTag("review_headword"), style = jp.kanjiHero)
                        BackArea(revealed, scroll, barAlpha, compact, bottomInset, { bodyTopPx = it }) { KanjiBack(card) }
                    }
                    is ReviewCardUi.Grammar -> {
                        GrammarTitle(card.title, maxWidthPx)
                        Spacer(Modifier.height(Spacing.xl)) // 24 dp, 뒷면에서도 예문이 같은 자리
                        RubyText(card.example, Modifier.testTag("review_example"), style = jp.bodyLarge, showRuby = prefs.bodyFurigana)
                        BackArea(revealed, scroll, barAlpha, compact, bottomInset, { bodyTopPx = it }) { GrammarBack(card, onChipClick, onGrammarDetail) { grammarOverflow = it } }
                    }
                    is ReviewCardUi.Vocab -> if (card.direction == VocabDirection.JA_TO_KO) {
                        Headword(card, maxWidthPx, showRuby = if (revealed) prefs.bodyFurigana else prefs.frontFurigana, reserve = true, tag = true)
                        BackArea(revealed, scroll, barAlpha, compact, bottomInset, { bodyTopPx = it }) { VocabBack(card, prefs, showHeadword = false, onChipClick, allMeanings) { allMeanings = true } }
                    } else {
                        Text(
                            JpSpans.annotate(card.meanings.firstOrNull()?.textKo ?: card.reading),
                            Modifier.testTag("review_headword"), style = MaterialTheme.typography.headlineMedium,
                        )
                        BackArea(revealed, scroll, barAlpha, compact, bottomInset, { bodyTopPx = it }) { VocabBack(card, prefs, showHeadword = true, onChipClick, allMeanings) { allMeanings = true } }
                    }
                }
            }
        }
    }
}

/**
 * 표제어 아래 뒷면 영역: 남은 높이 안에서 스크롤한다. 더 있는 쪽 가장자리는 카드 색으로 사라지게 해(항상 보이는 이어짐 단서),
 * 스크롤하는 동안에는 오른쪽(카드 안쪽 여백)에 스크롤 막대도 그린다.
 */
@Composable
private fun ColumnScope.BackArea(
    revealed: Boolean, scroll: ScrollState, barAlpha: State<Float>, flat: Boolean, bottomInset: Dp, onTop: (Int) -> Unit, content: @Composable () -> Unit,
) {
    val bar = MaterialTheme.colorScheme.outline
    val fade = MaterialTheme.colorScheme.surfaceContainerLow
    AnimatedVisibility(
        revealed, (if (flat) Modifier else Modifier.weight(1f, fill = false)).onGloballyPositioned { onTop(it.positionInParent().y.roundToInt()) }, enter = fadeIn(tween(150, easing = FastOutSlowInEasing))) {
        Column(
            // 그리기를 verticalScroll 바깥에 둬야 막대가 보이는 영역 기준이고 내용과 함께 밀려 가지 않는다
            (if (flat) Modifier else Modifier.scrollCue(scroll, barAlpha, bar, fade).verticalScroll(scroll)),
        ) { content(); Spacer(Modifier.height(bottomInset)) } // 저장 실패 스낵바 밑으로 끝까지 스크롤할 여백
    }
}

/** 그리기는 verticalScroll 바깥에 둬야 보이는 영역 기준이고 내용과 함께 밀려 가지 않는다. 가장자리 페이드는 항상, 막대는 [barAlpha]만큼. */
private fun Modifier.scrollCue(scroll: ScrollState, barAlpha: State<Float>, bar: Color, fade: Color): Modifier = drawWithContent {
    drawContent()
    val h = minOf(28.dp.toPx(), size.height / 2)
    if (scroll.canScrollForward) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, fade), startY = size.height - h, endY = size.height), Offset(0f, size.height - h), Size(size.width, h))
    }
    if (scroll.canScrollBackward) {
        drawRect(Brush.verticalGradient(listOf(fade, Color.Transparent), startY = 0f, endY = h), Offset.Zero, Size(size.width, h))
    }
    if (scroll.maxValue > 0 && barAlpha.value > 0f) drawScrollbar(scroll, bar.copy(alpha = barAlpha.value))
}

/** 스크롤 중과 멈춘 뒤 0.7초만 1, 이어서 0.3초 동안 사라진다(항상 떠 있으면 칩과 겹친다). */
@Composable
private fun scrollbarAlpha(scroll: ScrollState): State<Float> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(scroll.isScrollInProgress) {
        if (scroll.isScrollInProgress) alpha.snapTo(1f) else { delay(700); alpha.animateTo(0f, tween(300)) }
    }
    return alpha.asState()
}

/** 막대는 내용 영역 오른쪽 바깥(카드 안쪽 여백 16 dp 안)에 그려 오른쪽 위 급수 칩을 가리지 않는다. */
private fun DrawScope.drawScrollbar(scroll: ScrollState, color: Color) {
    val h = size.height * size.height / (size.height + scroll.maxValue)
    val y = (size.height - h) * scroll.value / scroll.maxValue
    drawRoundRect(color, Offset(size.width + 6.dp.toPx(), y), Size(3.dp.toPx(), h), CornerRadius(2.dp.toPx()))
}

/** 어휘 표기. 폭에 맞을 때까지 글자 크기를 줄인다(최저 60%, ui-design 5.1 "긴 표기"). 앞·뒷면이 같은 크기를 쓴다. */
@Composable
private fun Headword(
    card: ReviewCardUi.Vocab, availablePx: Int, showRuby: Boolean, reserve: Boolean, tag: Boolean,
) {
    val jp = LocalJpTypography.current
    val measurer = rememberTextMeasurer()
    val base = card.ruby.joinToString("") { it.base }
    val available = availablePx
    val scale = remember(base, available, jp) {
        val w = measurer.measure(AnnotatedString(base), jp.headword).size.width
        if (w <= 0) 1f else (available.toFloat() / w).coerceIn(0.6f, 1f)
    }
    val style = jp.headword.copy(fontSize = jp.headword.fontSize * scale, lineHeight = jp.headword.lineHeight * scale)
    RubyText(
        card.ruby, if (tag) Modifier.testTag("review_headword") else Modifier,
        style = style, showRuby = showRuby, reserveRubyLine = reserve,
    )
}

/** 문법 형식. 폭에 맞을 때까지 줄여 최저 60%(27 sp), 그래도 넘치면 줄바꿈(ui-design 6.9.1). 루비가 없으므로 루비 줄을 확보하지 않는다. */
@Composable
private fun GrammarTitle(title: String, availablePx: Int) {
    val jp = LocalJpTypography.current
    val measurer = rememberTextMeasurer()
    val scale = remember(title, availablePx, jp) {
        val w = measurer.measure(AnnotatedString(title), jp.headword).size.width
        if (w <= 0) 1f else (availablePx.toFloat() / w).coerceIn(0.6f, 1f)
    }
    JpText(
        title, Modifier.testTag("review_headword"),
        style = jp.headword.copy(fontSize = jp.headword.fontSize * scale, lineHeight = jp.headword.lineHeight * scale),
    )
}

@Composable
private fun GrammarBack(card: ReviewCardUi.Grammar, onChipClick: ((MeaningUi) -> Unit)?, onDetail: ((String) -> Unit)?, onOverflow: (Boolean) -> Unit) {
    var overflow by remember(card.cardId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalDivider()
        Text(JpSpans.annotate(card.meaningKo), style = MaterialTheme.typography.bodyLarge)
        Text(JpSpans.annotate(card.exampleKo), style = MaterialTheme.typography.bodyMedium)
        Text(
            JpSpans.annotate(card.summaryKo), style = MaterialTheme.typography.bodyMedium,
            maxLines = 4, overflow = TextOverflow.Ellipsis, onTextLayout = { overflow = it.hasVisualOverflow; onOverflow(overflow) },
        )
        if (overflow && onDetail != null) {
            TextButton({ onDetail(card.cardId.substringAfter(':')) }, Modifier.heightIn(min = 48.dp).testTag("review_grammar_detail")) {
                Text(stringResource(R.string.review_grammar_detail))
            }
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
            ProvenanceChip(card.provenance, onChipClick)
            if (card.needsReview) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        stringResource(R.string.grammar_review_chip), Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabBack(card: ReviewCardUi.Vocab, prefs: ReviewPrefs, showHeadword: Boolean, onChipClick: ((MeaningUi) -> Unit)?, all: Boolean, onAll: () -> Unit) {
    val jp = LocalJpTypography.current
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (showHeadword) {
            RubyText(card.ruby, style = jp.headword, showRuby = prefs.bodyFurigana)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.review_reading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            JpText(card.reading, style = jp.body)
        }
        PosChips(card.pos.map(PosLabels::of))
        HorizontalDivider()
        Meanings(card, onChipClick, all, onAll)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PosChips(labels: List<String>) {
    if (labels.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        labels.forEach {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    JpSpans.annotate(it), Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Meanings(card: ReviewCardUi.Vocab, onChipClick: ((MeaningUi) -> Unit)?, all: Boolean, onAll: () -> Unit) {
    val shown = if (all) card.meanings else card.meanings.take(MAX_MEANINGS)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        shown.forEachIndexed { i, m ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge)
                Column {
                    Text(JpSpans.annotate(m.textKo), style = MaterialTheme.typography.bodyLarge)
                    ProvenanceChip(m, onChipClick)
                }
            }
        }
        val more = card.meanings.size - shown.size
        if (more > 0) {
            TextButton(onAll, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.review_more_meanings, more)) }
        }
    }
}

@Composable
private fun KanjiBack(card: ReviewCardUi.Kanji) {
    val jp = LocalJpTypography.current
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        @Composable fun row(label: Int, value: @Composable () -> Unit) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                Text(
                    stringResource(label), Modifier.width(infoLabelWidth()),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                value()
            }
        }
        if (card.koreanReadings.isNotEmpty()) row(R.string.review_korean_reading) {
            Text(card.koreanReadings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        }
        if (card.on.isNotEmpty()) row(R.string.review_on_reading) { JpText(card.on.joinToString("、"), style = jp.body) }
        if (card.kun.isNotEmpty()) row(R.string.review_kun_reading) { KunText(card.kun) }
        row(R.string.review_strokes) { Text(stringResource(R.string.review_strokes_value, card.strokeCount), style = MaterialTheme.typography.bodyLarge) }
        if (card.words.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.review_words_used), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            card.words.forEach { w ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    JpText(w.written ?: w.reading, style = jp.body)
                    Text(JpSpans.annotate(w.meaningKo), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun LevelChip(level: com.jlptstudy.app.JlptLevel) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            level.name, Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 출처·검수 칩(ui-design 5.7). onClick이 null이면 누를 수 없는 표시만(하단 시트는 M7). */
@Composable
fun ProvenanceChip(meaning: MeaningUi, onClick: ((MeaningUi) -> Unit)?, modifier: Modifier = Modifier) {
    val label = ProvenanceLabel.of(meaning)
    val detail = stringResource(R.string.review_chip_detail)
    val icon = if (ProvenanceLabel.isTranslated(meaning)) R.drawable.ic_ms_translate else R.drawable.ic_ms_edit_note
    Column(modifier.then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier.padding(vertical = Spacing.xs))) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClickLabel = detail, role = Role.Button) { onClick(meaning) }
                    } else Modifier,
                )
                .heightIn(min = 32.dp).padding(horizontal = Spacing.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun levelOf(card: ReviewCardUi) = when (card) {
    is ReviewCardUi.Vocab -> card.level
    is ReviewCardUi.Kanji -> card.level
    is ReviewCardUi.Grammar -> card.level
}

@Composable
private fun kindLabel(card: ReviewCardUi): String = stringResource(
    when {
        card is ReviewCardUi.Kanji -> R.string.review_kind_kanji
        card is ReviewCardUi.Grammar -> R.string.review_kind_grammar
        (card as ReviewCardUi.Vocab).direction == VocabDirection.JA_TO_KO -> R.string.review_kind_word
        else -> R.string.review_kind_recall
    },
)

/** 앞면 낭독(11.1): 일본어 구간에 JpSpan. contentDescription이 아니라 semantics text로 건다(4.3). */
internal fun frontText(card: ReviewCardUi, remaining: Int, bodyFurigana: Boolean = false): AnnotatedString = buildAnnotatedString {
    fun ko(s: String) = withStyle(KoSpan) { append(s) }
    when (card) {
        is ReviewCardUi.Kanji -> {
            ko("한자 카드, 앞면. "); withStyle(JpSpan) { append(card.literal) }
        }
        is ReviewCardUi.Grammar -> {
            ko("문법 카드, 앞면. "); withStyle(JpSpan) { append(card.title) }
            ko(". 예문 "); withStyle(JpSpan) { append(card.example.joinToString("") { it.base }) }
            if (bodyFurigana && card.example.any { it.ruby != null }) {
                ko(", 읽기 "); withStyle(JpSpan) { append(card.example.joinToString("") { it.ruby ?: it.base }) }
            }
        }
        is ReviewCardUi.Vocab ->
            if (card.direction == VocabDirection.JA_TO_KO) {
                ko("단어 카드, 앞면. "); withStyle(JpSpan) { append(card.written ?: card.reading) }
            } else {
                ko("뜻을 보고 일본어 떠올리기 카드, 앞면. "); append(JpSpans.annotate(card.meanings.firstOrNull()?.textKo ?: card.reading))
            }
    }
    ko(". 남은 ${remaining}장")
}

/**
 * 뒷면 카드 노드(11.1). 뒷면은 clearAndSetSemantics 단일 노드라 화면에 보이는 정보를 모두 여기에 담는다.
 * 누를 것(칩·더 보기·자세히)만 사용자 지정 동작으로 따로 낸다. [allMeanings]가 바뀌면 text가 바뀌어 Polite로 다시 읽힌다.
 */
internal fun backText(card: ReviewCardUi, allMeanings: Boolean = false): AnnotatedString = buildAnnotatedString {
    fun ko(s: String) = withStyle(KoSpan) { append(s) }
    fun jp(s: String) = withStyle(JpSpan) { append(s) }
    ko("뒷면.")
    when (card) {
        is ReviewCardUi.Vocab -> {
            // 떠올리기 카드는 앞면이 뜻이라 일본어 표기가 정답이다
            if (card.direction != VocabDirection.JA_TO_KO) card.written?.let { ko(" 일본어 "); jp(it); ko(".") }
            ko(" 읽기 "); jp(card.reading)
            if (card.pos.isNotEmpty()) { ko(". 품사 "); card.pos.forEachIndexed { i, p -> if (i > 0) ko(", "); append(JpSpans.annotate(PosLabels.of(p))) } }
            val shown = if (allMeanings) card.meanings else card.meanings.take(MAX_MEANINGS)
            shown.forEachIndexed { i, m ->
                ko(if (shown.size == 1) ". 뜻 " else ". ${i + 1}번 뜻 "); append(JpSpans.annotate(m.textKo)); ko(". ${ProvenanceLabel.of(m)}")
            }
            val more = card.meanings.size - shown.size
            if (more > 0) ko(". 뜻 ${more}개 더")
        }
        is ReviewCardUi.Kanji -> {
            ko(" "); jp(card.literal)
            if (card.koreanReadings.isNotEmpty()) ko(". 한국어 음 " + card.koreanReadings.joinToString(", "))
            if (card.on.isNotEmpty()) { ko(". 음독 "); jp(card.on.joinToString("、")) }
            if (card.kun.isNotEmpty()) { ko(". 훈독 "); jp(card.kun.joinToString("、") { kunDisplay(it) }) }
            ko(". ${card.strokeCount}획")
            if (card.words.isNotEmpty()) {
                ko(". 쓰인 단어 ")
                card.words.forEachIndexed { i, w -> if (i > 0) ko(", "); jp(w.written ?: w.reading); ko(" "); append(JpSpans.annotate(w.meaningKo)) }
            }
        }
        is ReviewCardUi.Grammar -> {
            ko(" 뜻 "); append(JpSpans.annotate(card.meaningKo))
            ko(". 예문 뜻 "); append(JpSpans.annotate(card.exampleKo))
            ko(". "); append(JpSpans.annotate(card.summaryKo))
            ko(". ${ProvenanceLabel.of(card.provenance)}")
            if (card.needsReview) ko(". 설명 검토 중")
        }
    }
}
