package com.jlptstudy.app.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
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
            val focus = remember { FocusRequester() }
            LaunchedEffect(card.cardId, revealed) { runCatching { focus.requestFocus() } } // 이동만, liveRegion 없음(11.1)
            val frontText = remember(card, remaining) { frontText(card, remaining) }
            val backText = remember(card) { backText(card) }
            // 표제어는 고정, 그 아래 뒷면 정보만 스크롤한다(ui-design 5.1). 낮은 카드에서는 표제어 위 여백을 먼저 줄인다.
            val scroll = rememberScrollState()
            // 카드가 너무 낮으면(낮은 높이·글꼴 200%) 뒷면 영역이 보이지 않으므로 카드 전체를 스크롤한다. 스크롤 0에서 표제어 위치는 같다.
            val compact = revealed && cardHeight < headEstimate + chrome + 120.dp
            val bar = MaterialTheme.colorScheme.outline
            Column(
                Modifier.fillMaxSize()
                    .then(
                        if (compact) Modifier.drawWithContent {
                            drawContent()
                            if (scroll.maxValue > 0) {
                                val h = size.height * size.height / (size.height + scroll.maxValue)
                                val y = (size.height - h) * scroll.value / scroll.maxValue
                                drawRoundRect(bar, Offset(size.width - 3.dp.toPx(), y), Size(3.dp.toPx(), h), CornerRadius(2.dp.toPx()))
                            }
                        }.verticalScroll(scroll) else Modifier,
                    )
                    .pointerInput(revealed) { if (!revealed) detectTapGestures { onReveal() } }
                    .testTag("review_card")
                    .focusRequester(focus).focusable()
                    .then(
                        if (revealed) Modifier.semantics { text = backText }
                        else Modifier.clearAndSetSemantics { text = frontText },
                    ),
            ) {
                header?.invoke()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(kindLabel(card), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LevelChip(levelOf(card))
                }
                Spacer(Modifier.height((topPad).coerceAtMost((cardHeight - chrome - headEstimate - 120.dp).coerceAtLeast(0.dp))))
                when (card) {
                    is ReviewCardUi.Kanji -> {
                        JpText(card.literal, Modifier.testTag("review_headword"), style = jp.kanjiHero)
                        BackArea(revealed, scroll, compact, bottomInset) { KanjiBack(card) }
                    }
                    is ReviewCardUi.Vocab -> if (card.direction == VocabDirection.JA_TO_KO) {
                        Headword(card, maxWidthPx, showRuby = if (revealed) prefs.bodyFurigana else prefs.frontFurigana, reserve = true, tag = true)
                        BackArea(revealed, scroll, compact, bottomInset) { VocabBack(card, prefs, showHeadword = false, onChipClick) }
                    } else {
                        Text(
                            JpSpans.annotate(card.meanings.firstOrNull()?.textKo ?: card.reading),
                            Modifier.testTag("review_headword"), style = MaterialTheme.typography.headlineMedium,
                        )
                        BackArea(revealed, scroll, compact, bottomInset) { VocabBack(card, prefs, showHeadword = true, onChipClick) }
                    }
                }
            }
        }
    }
}

/** 표제어 아래 뒷면 영역: 남은 높이 안에서 스크롤하고, 더 있으면 오른쪽에 스크롤 막대를 그린다. */
@Composable
private fun ColumnScope.BackArea(revealed: Boolean, scroll: ScrollState, flat: Boolean, bottomInset: Dp, content: @Composable () -> Unit) {
    val bar = MaterialTheme.colorScheme.outline
    AnimatedVisibility(revealed, if (flat) Modifier else Modifier.weight(1f, fill = false), enter = fadeIn(tween(150, easing = FastOutSlowInEasing))) {
        Column(
            (if (flat) Modifier else Modifier.verticalScroll(scroll)).drawWithContent {
                drawContent()
                if (!flat && scroll.maxValue > 0) {
                    val h = size.height * size.height / (size.height + scroll.maxValue)
                    val y = (size.height - h) * scroll.value / scroll.maxValue
                    drawRoundRect(bar, Offset(size.width - 4.dp.toPx(), y), Size(3.dp.toPx(), h), CornerRadius(2.dp.toPx()))
                }
            },
        ) { content(); Spacer(Modifier.height(bottomInset)) } // 저장 실패 스낵바 밑으로 끝까지 스크롤할 여백
    }
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

@Composable
private fun VocabBack(card: ReviewCardUi.Vocab, prefs: ReviewPrefs, showHeadword: Boolean, onChipClick: ((MeaningUi) -> Unit)?) {
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
        Meanings(card, onChipClick)
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
private fun Meanings(card: ReviewCardUi.Vocab, onChipClick: ((MeaningUi) -> Unit)?) {
    var all by remember(card.cardId) { mutableStateOf(false) }
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
            TextButton({ all = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.review_more_meanings, more)) }
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
}

@Composable
private fun kindLabel(card: ReviewCardUi): String = stringResource(
    when {
        card is ReviewCardUi.Kanji -> R.string.review_kind_kanji
        (card as ReviewCardUi.Vocab).direction == VocabDirection.JA_TO_KO -> R.string.review_kind_word
        else -> R.string.review_kind_recall
    },
)

/** 앞면 낭독(11.1): 일본어 구간에 JpSpan. contentDescription이 아니라 semantics text로 건다(4.3). */
internal fun frontText(card: ReviewCardUi, remaining: Int): AnnotatedString = buildAnnotatedString {
    fun ko(s: String) = withStyle(KoSpan) { append(s) }
    when (card) {
        is ReviewCardUi.Kanji -> {
            ko("한자 카드, 앞면. "); withStyle(JpSpan) { append(card.literal) }
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

/** 뒷면 카드 노드(11.1). 뒷면 안쪽 요소는 각자 노드로 남아(칩은 버튼) 그 뒤에 읽힌다. */
internal fun backText(card: ReviewCardUi): AnnotatedString = buildAnnotatedString {
    fun ko(s: String) = withStyle(KoSpan) { append(s) }
    ko("뒷면.")
    when (card) {
        is ReviewCardUi.Vocab -> { ko(" 읽기 "); withStyle(JpSpan) { append(card.reading) } }
        is ReviewCardUi.Kanji -> { ko(" "); withStyle(JpSpan) { append(card.literal) } }
    }
}
