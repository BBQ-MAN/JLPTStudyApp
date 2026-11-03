package com.jlptstudy.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.width
import com.jlptstudy.app.ui.common.KunText
import com.jlptstudy.app.ui.common.infoLabelWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.JpSpans
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.common.RubyText
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.review.PosChips
import com.jlptstudy.app.ui.review.ProvenanceChip
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_WORDS = 20

class DetailActions(
    val onFailed: () -> Unit = {}, val onToggleFurigana: () -> Unit = {}, val onBack: () -> Unit = {}, val onAboutLevel: () -> Unit = {}, val onKanji: (String) -> Unit = {}, val onWord: (String) -> Unit = {}, val onStrokeSource: () -> Unit = {},
    val onChipClick: ((com.jlptstudy.app.ui.review.MeaningUi) -> Unit)? = null,
)

@Composable
fun VocabDetailRoute(vm: VocabDetailViewModel, actions: DetailActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.status == DetailStatus.ERROR) LaunchedEffect(Unit) { actions.onFailed() }
    VocabDetailScreen(state, actions)
}

@Composable
fun KanjiDetailRoute(vm: KanjiDetailViewModel, actions: DetailActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.status == DetailStatus.ERROR) LaunchedEffect(Unit) { actions.onFailed() }
    KanjiDetailScreen(state, actions)
}

@Composable
internal fun DetailFrame(status: DetailStatus, actions: DetailActions, tag: String, topEnd: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag(tag), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(actions.onBack, Modifier.minimumInteractiveComponentSize()) {
                    Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.detail_back))
                }
                Spacer(Modifier.weight(1f))
                topEnd()
            }
            when (status) {
                DetailStatus.LOADING, DetailStatus.ERROR -> StatusContent(stringResource(R.string.detail_loading), loading = true)
                DetailStatus.RETIRED -> StatusContent(
                    stringResource(R.string.detail_retired_title), body = stringResource(R.string.detail_retired_body),
                    actions = listOf(StatusAction(stringResource(R.string.detail_back), actions.onBack)),
                )
                DetailStatus.LOADED -> Column(
                    Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) { content(); Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun LevelButton(level: JlptLevel, label: String, onClick: () -> Unit) {
    val detail = stringResource(R.string.detail_level_about)
    Row(Modifier.minimumInteractiveComponentSize().clickable(onClickLabel = detail, role = Role.Button, onClick = onClick)) {
        Text(
            label, Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 상단 바 오른쪽 본문 후리가나 토글(ui-design 5.2). */
@Composable
internal fun FuriganaToggle(showRuby: Boolean, onToggle: () -> Unit) {
    val jp = LocalJpTypography.current
    val furiganaLabel = stringResource(R.string.review_furigana)
    val on = stringResource(R.string.level_state_on)
    val off = stringResource(R.string.level_state_off)
    IconButton(
        onToggle,
        Modifier.minimumInteractiveComponentSize().clearAndSetSemantics {
            contentDescription = furiganaLabel; stateDescription = if (showRuby) on else off; role = Role.Switch
            onClick { onToggle(); true }
        },
    ) {
        val bg = if (showRuby) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(bg)
                .border(1.dp, if (showRuby) Color.Transparent else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { JpText("あ", style = jp.bodyLarge, color = if (showRuby) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** S06(ui-design 6.6). 예문 절은 콘텐츠에 예문이 들어오는 마일스톤에서 더한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabDetailScreen(state: VocabDetailUiState, actions: DetailActions = DetailActions()) {
    val jp = LocalJpTypography.current
    val toggle: @Composable () -> Unit = { if (state.status == DetailStatus.LOADED) FuriganaToggle(state.showRuby, actions.onToggleFurigana) }
    DetailFrame(state.status, actions, "vocab_detail", toggle) {
        RubyText(state.ruby, style = jp.headword, showRuby = state.showRuby)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            JpText(state.reading, style = jp.body)
            state.level?.let { LevelButton(it, stringResource(R.string.detail_level_word, it.name), actions.onAboutLevel) }
        }
        PosChips(state.pos)
        HorizontalDivider()
        if (state.meanings.isNotEmpty()) {
            SectionTitle(stringResource(R.string.detail_meanings))
            state.meanings.forEachIndexed { i, m ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge)
                    Column {
                        Text(JpSpans.annotate(m.textKo), style = MaterialTheme.typography.bodyLarge)
                        ProvenanceChip(m, actions.onChipClick)
                    }
                }
            }
        }
        if (state.kanji.isNotEmpty()) {
            SectionTitle(stringResource(R.string.detail_kanji_used))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.kanji.forEach { k ->
                    Surface(
                        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(role = Role.Button) { actions.onKanji(k) },
                        shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            JpText(k, style = jp.title)
                        }
                    }
                }
            }
        }
        if (state.cardStates.isNotEmpty()) {
            SectionTitle(stringResource(R.string.detail_card_state))
            state.cardStates.forEach { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (c.kind == CardKind.VR) R.string.detail_card_recognition else R.string.detail_card_recall), style = MaterialTheme.typography.bodyLarge)
                    Text(progressText(c), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val DueFormat = DateTimeFormatter.ofPattern("M월 d일")

@Composable
private fun progressText(c: CardStateSummary): String = when (c.progress) {
    CardProgress.NEW -> stringResource(R.string.detail_card_new)
    CardProgress.LEARNING -> stringResource(R.string.detail_card_learning)
    CardProgress.REVIEW -> stringResource(R.string.detail_card_due, DueFormat.format(Instant.ofEpochMilli(c.dueAtEpochMs ?: 0).atZone(ZoneId.systemDefault())))
}

/** S07(ui-design 6.7). 뜻 절은 없다. 필순 절은 strokes가 null이면 그리지 않는다(StrokeOrder.kt). */
@Composable
fun KanjiDetailScreen(state: KanjiDetailUiState, actions: DetailActions = DetailActions()) {
    val jp = LocalJpTypography.current
    DetailFrame(state.status, actions, "kanji_detail") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            JpText(state.literal, style = jp.kanjiHero)
            Column {
                Text(stringResource(R.string.review_strokes_value, state.strokeCount), style = MaterialTheme.typography.bodyLarge)
                state.level?.let { LevelButton(it, stringResource(R.string.detail_level_kanji, it.name), actions.onAboutLevel) }
            }
        }
        HorizontalDivider()
        if (state.koreanReadings.isNotEmpty() || state.on.isNotEmpty() || state.kun.isNotEmpty()) {
            SectionTitle(stringResource(R.string.detail_readings))
            @Composable fun row(label: Int, value: @Composable () -> Unit) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                    Text(stringResource(label), Modifier.width(infoLabelWidth()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    value()
                }
            }
            if (state.koreanReadings.isNotEmpty()) row(R.string.review_korean_reading) { Text(state.koreanReadings.joinToString(", "), style = MaterialTheme.typography.bodyLarge) }
            if (state.on.isNotEmpty()) row(R.string.review_on_reading) { JpText(state.on.joinToString("、"), style = jp.body) }
            if (state.kun.isNotEmpty()) row(R.string.review_kun_reading) { KunText(state.kun) }
        }
        if (state.words.isNotEmpty()) {
            var all by remember { mutableStateOf(false) }
            SectionTitle(stringResource(R.string.review_words_used))
            (if (all) state.words else state.words.take(MAX_WORDS)).forEach { w ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { actions.onWord(w.vocabId) },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically,
                ) {
                    JpText(w.written ?: w.reading, style = jp.body)
                    Text(JpSpans.annotate(w.meaningKo), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!all && state.words.size > MAX_WORDS) {
                TextButton({ all = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.detail_words_more)) }
            }
        }
        if (state.strokes != null) {
            SectionTitle(stringResource(R.string.detail_strokes_section))
            StrokeOrder(state.strokes, state.strokeSourceName, actions.onStrokeSource)
        }
    }
}
