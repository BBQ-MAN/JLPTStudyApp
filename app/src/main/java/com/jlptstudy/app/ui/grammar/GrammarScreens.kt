package com.jlptstudy.app.ui.grammar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.data.content.ConnectionUi
import com.jlptstudy.app.data.content.ContrastUi
import com.jlptstudy.app.data.content.ExampleUi
import com.jlptstudy.app.data.content.GrammarRow
import com.jlptstudy.app.ui.common.JpSpans
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.common.RubyText
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.detail.DetailActions
import com.jlptstudy.app.ui.detail.DetailFrame
import com.jlptstudy.app.ui.detail.DetailStatus
import com.jlptstudy.app.ui.detail.FuriganaToggle
import com.jlptstudy.app.ui.detail.LevelButton
import com.jlptstudy.app.ui.detail.SectionTitle
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.review.ProvenanceChip
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing

class GrammarListActions(
    val onToggleLevel: (JlptLevel) -> Unit = {}, val onClearLevels: () -> Unit = {}, val onOpen: (String) -> Unit = {},
    val onNotice: () -> Unit = {},
)

class GrammarDetailActions(
    val onFailed: () -> Unit = {}, val onToggleFurigana: () -> Unit = {}, val onBack: () -> Unit = {},
    val onAboutLevel: () -> Unit = {}, val onNotice: () -> Unit = {}, val onGrammar: (String) -> Unit = {},
    val onChipClick: ((MeaningUi) -> Unit)? = null,
)

@Composable
fun GrammarListRoute(vm: GrammarListViewModel, onOpen: (String) -> Unit, onNotice: () -> Unit, onFailed: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.status == GrammarListStatus.ERROR) LaunchedEffect(Unit) { onFailed() }
    GrammarListScreen(state, GrammarListActions(vm::toggleLevel, vm::clearLevels, onOpen, onNotice))
}

/** S10(ui-design 6.9): 급수 칩, 비공식 안내 줄, 급수별 묶음 목록. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GrammarListScreen(state: GrammarListUiState, actions: GrammarListActions = GrammarListActions()) {
    val jp = LocalJpTypography.current
    Surface(Modifier.fillMaxSize().testTag("grammar_list_screen"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Text(
                stringResource(R.string.grammar_title),
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.availableLevels.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    JlptLevel.entries.filter { it in state.availableLevels }.forEach { l ->
                        val sel = l in state.levels
                        FilterChip(
                            selected = sel, onClick = { actions.onToggleLevel(l) }, label = { Text(l.name) },
                            leadingIcon = if (sel) {
                                { Icon(painterResource(R.drawable.ic_ms_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = sel, borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.testTag("grammar_level_chip_${l.name}"),
                        )
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        stringResource(R.string.grammar_note), Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(actions.onNotice, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.grammar_note_more)) }
                }
            }
            when (state.status) {
                GrammarListStatus.LOADING, GrammarListStatus.ERROR ->
                    StatusContent(stringResource(R.string.detail_loading), loading = true, modifier = Modifier.align(Alignment.CenterHorizontally))
                GrammarListStatus.EMPTY -> StatusContent(
                    stringResource(R.string.grammar_empty_title), body = stringResource(R.string.grammar_empty_body), takeFocus = false,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    actions = listOf(StatusAction(stringResource(R.string.dict_filter_clear), actions.onClearLevels)),
                )
                GrammarListStatus.LOADED -> LazyColumn(Modifier.fillMaxSize().testTag("grammar_list")) {
                    state.rows.groupBy { it.level }.forEach { (level, rows) ->
                        stickyHeader(key = "h-${level.name}") {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                Text(
                                    stringResource(R.string.grammar_level_header, level.name),
                                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).semantics { heading() },
                                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(rows, key = { it.id }) { GrammarRowItem(it, jp.body, actions.onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarRowItem(row: GrammarRow, titleStyle: androidx.compose.ui.text.TextStyle, onOpen: (String) -> Unit) {
    val label = stringResource(R.string.grammar_row_click)
    Column {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = label, role = Role.Button) { onOpen(row.id) }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.Center,
        ) {
            JpText(row.title, style = titleStyle.copy(fontWeight = FontWeight.Medium))
            Text(
                JpSpans.annotate(row.meaningKo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()
    }
}

@Composable
fun GrammarDetailRoute(vm: GrammarDetailViewModel, actions: GrammarDetailActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.status == DetailStatus.ERROR) LaunchedEffect(Unit) { actions.onFailed() }
    GrammarDetailScreen(state, actions)
}

/** S11(ui-design 6.9): 머리, 한국어로는, 접속 표, 설명, 예문, 비슷한 문법. 콘텐츠에 없는 절은 숨긴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrammarDetailScreen(state: GrammarDetailUiState, actions: GrammarDetailActions = GrammarDetailActions()) {
    val jp = LocalJpTypography.current
    val frame = DetailActions(
        onFailed = actions.onFailed, onToggleFurigana = actions.onToggleFurigana, onBack = actions.onBack,
        onAboutLevel = actions.onAboutLevel, onChipClick = actions.onChipClick,
    )
    val toggle: @Composable () -> Unit = { if (state.status == DetailStatus.LOADED) FuriganaToggle(state.showRuby, actions.onToggleFurigana) }
    DetailFrame(state.status, frame, "grammar_detail", toggle) {
        val d = state.detail ?: return@DetailFrame
        JpText(d.title, Modifier.semantics { heading() }, style = jp.title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LevelButton(d.level, stringResource(R.string.detail_level_word, d.level.name), actions.onAboutLevel)
            ProvenanceChip(d.provenance, actions.onChipClick)
            if (d.needsReview) {
                Text(
                    stringResource(R.string.grammar_review_chip),
                    Modifier.align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                        .padding(horizontal = Spacing.sm, vertical = 6.dp).testTag("grammar_review_chip"),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GrammarSection(stringResource(R.string.grammar_sec_meaning))
        Text(JpSpans.annotate(d.meaningKo), style = MaterialTheme.typography.bodyLarge)
        if (d.connections.isNotEmpty()) {
            GrammarSection(stringResource(R.string.grammar_sec_connect))
            ConnectionTable(d.connections, state.showRuby)
        }
        GrammarSection(stringResource(R.string.grammar_sec_explain))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            d.explanationKo.split("\n\n").filter { it.isNotBlank() }.forEach {
                Text(JpSpans.annotate(it.trim()), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (d.examples.isNotEmpty()) {
            GrammarSection(stringResource(R.string.grammar_sec_examples))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                d.examples.forEach { ExampleCard(it, state.showRuby) }
            }
        }
        if (d.contrasts.isNotEmpty()) {
            GrammarSection(stringResource(R.string.grammar_sec_contrast))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                d.contrasts.forEach { ContrastCard(it, state.showRuby, actions.onGrammar) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.grammar_footer), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(actions.onNotice, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.grammar_note_more)) }
        }
    }
}

// 절 사이 24dp(ui-design 3.3 xl): 부모 spacedBy 12dp + 위 여백 12dp
@Composable
private fun GrammarSection(text: String) = SectionTitle(text, Modifier.padding(top = Spacing.md))

@Composable
private fun cardColors() = MaterialTheme.colorScheme.surfaceContainerLow

@Composable
private fun ExampleCard(e: ExampleUi, showRuby: Boolean) {
    val jp = LocalJpTypography.current
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = cardColors()) {
        Column(Modifier.padding(Spacing.lg).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            RubyText(e.ja, style = jp.bodyLarge, showRuby = showRuby)
            Text(JpSpans.annotate(e.ko), style = MaterialTheme.typography.bodyMedium)
            if (e.sourceId != "authored") {
                val who = listOfNotNull(e.author, e.license).joinToString(" · ").ifEmpty { e.sourceId }
                Text(stringResource(R.string.grammar_example_source, who), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContrastCard(c: ContrastUi, showRuby: Boolean, onGrammar: (String) -> Unit) {
    val jp = LocalJpTypography.current
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = cardColors()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            JpText(
                c.title,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onGrammar(c.grammarId) },
                style = jp.body.copy(fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
            )
            ContrastLine(R.string.grammar_mine, c.mine, showRuby)
            ContrastLine(R.string.grammar_other, c.other, showRuby)
        }
    }
}

@Composable
private fun ContrastLine(label: Int, e: ExampleUi, showRuby: Boolean) {
    val jp = LocalJpTypography.current
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RubyText(e.ja, style = jp.bodyLarge, showRuby = showRuby)
        Text(JpSpans.annotate(e.ko), style = MaterialTheme.typography.bodyMedium)
    }
}

/** 표 3열이 본문 폭에 안 들어가면(글자 크기 기준 폭이 320 sp 미만) 행마다 카드로 바꾼다. */
@Composable
private fun ConnectionTable(rows: List<ConnectionUi>, showRuby: Boolean) {
    val jp = LocalJpTypography.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("grammar_connections")) {
        val scale = LocalDensity.current.fontScale
        if (maxWidth.value / scale >= 320f) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    HeaderCell(R.string.grammar_col_pos, Modifier.weight(0.22f))
                    HeaderCell(R.string.grammar_col_form, Modifier.weight(0.3f))
                    HeaderCell(R.string.grammar_col_example, Modifier.weight(0.48f))
                }
                HorizontalDivider()
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Text(r.posKo, Modifier.weight(0.22f).heightIn(min = 48.dp).padding(vertical = Spacing.sm), style = MaterialTheme.typography.bodyMedium)
                        RubyText(r.form, Modifier.weight(0.3f).heightIn(min = 48.dp).padding(vertical = Spacing.sm), style = jp.body, showRuby = showRuby)
                        RubyText(r.example, Modifier.weight(0.48f).heightIn(min = 48.dp).padding(vertical = Spacing.sm), style = jp.bodyLarge, showRuby = showRuby)
                    }
                    HorizontalDivider()
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rows.forEach { r ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = cardColors()) {
                        Column(Modifier.padding(Spacing.lg).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            LabeledValue(R.string.grammar_col_pos) { Text(r.posKo, style = MaterialTheme.typography.bodyMedium) }
                            LabeledValue(R.string.grammar_col_form) { RubyText(r.form, style = jp.body, showRuby = showRuby) }
                            LabeledValue(R.string.grammar_col_example) { RubyText(r.example, style = jp.bodyLarge, showRuby = showRuby) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(label: Int, modifier: Modifier) {
    Text(stringResource(label), modifier.padding(vertical = Spacing.sm), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun LabeledValue(label: Int, value: @Composable () -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}
