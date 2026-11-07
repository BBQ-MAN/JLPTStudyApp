package com.jlptstudy.app.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.R
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.LocalExtendedColors
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing

class QuizResultActions(
    val onClose: () -> Unit = {}, val onFilter: (ResultFilter) -> Unit = {}, val onOpen: (Int) -> Unit = {},
    val onBackToList: () -> Unit = {}, val onStep: (Int) -> Unit = {},
    val onTarget: (TargetLink) -> Unit = {}, val onNotice: () -> Unit = {}, val onChipClick: ((MeaningUi) -> Unit)? = null,
)

@Composable
fun QuizResultRoute(
    vm: QuizResultViewModel, onClose: () -> Unit, onTarget: (TargetLink) -> Unit, onNotice: () -> Unit, onChipClick: ((MeaningUi) -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.reviewIndex != null) { vm.closeReview() } // 해설 보기 → 목록. 목록에서의 뒤로는 기본 동작(S12)
    QuizResultScreen(state, QuizResultActions(onClose, vm::setFilter, vm::openReview, vm::closeReview, vm::stepReview, onTarget, onNotice, onChipClick))
}

/** S15(ui-design 6.10): 목록 화면과 같은 라우트 안의 해설 보기를 reviewIndex로 나눈다. */
@Composable
fun QuizResultScreen(state: QuizResultUiState, actions: QuizResultActions = QuizResultActions()) {
    // 목록 스크롤 위치는 해설을 보는 동안에도 여기서 들고 있다(해설에서 뒤로 가면 같은 위치)
    val listState = rememberLazyListState()
    val review = state.reviewIndex?.let { state.items.getOrNull(it) }
    // 해설을 닫고 돌아오면 마지막으로 본 행으로 포커스를 돌려주기 위해 기억한다
    var lastReview by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.reviewIndex) { state.reviewIndex?.let { lastReview = it } }
    Surface(Modifier.fillMaxSize().testTag("result_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter)) {
                if (review != null) ReviewPane(state, review, actions) else ListPane(state, listState, actions, lastReview)
            }
        }
    }
}

@Composable
private fun TopBar(icon: Int, description: String, onClick: () -> Unit, title: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick, Modifier.testTag("result_close")) { Icon(painterResource(icon), description) }
        Text(title, Modifier.padding(start = Spacing.xs), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ColumnScope.ListPane(state: QuizResultUiState, listState: androidx.compose.foundation.lazy.LazyListState, actions: QuizResultActions, lastReview: Int?) {
    TopBar(R.drawable.ic_ms_close, stringResource(R.string.result_close), actions.onClose, stringResource(R.string.result_title))
    if (state.status == QuizResultStatus.LOADING) {
        LoadingPlaceholder(Modifier.weight(1f))
        return
    }
    val titleFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val visible = state.visibleItems
    // 처음 열리면 제목 줄로, 해설에서 돌아오면 방금 본 행으로(TalkBack). 행은 목록의 마지막 구간이라 순번을 셀 수 있다
    LaunchedEffect(Unit) {
        val pos = visible.indexOfFirst { it.index == lastReview }
        if (lastReview == null || pos < 0) { runCatching { titleFocus.requestFocus() }; return@LaunchedEffect }
        val info = listState.layoutInfo
        if (info.totalItemsCount > 0 && info.visibleItemsInfo.none { it.key == lastReview }) listState.scrollToItem(info.totalItemsCount - visible.size + pos)
        withFrameNanos { }
        runCatching { rowFocus.requestFocus() }
    }
    val time = timeLine(state)
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().testTag("result_list"), state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { TitleLine(state, Modifier.focusRequester(titleFocus)) }
        item { Summary(state) }
        if (state.status == QuizResultStatus.LOADED) {
            state.score?.let { score ->
                val lines = if (state.mode == QuizMode.EXAM) {
                    score.examSections.map { Triple(it.titleKo, it.correct, it.total) }
                } else if (score.sections.size >= 2) {
                    score.sections.map { Triple(QuizTypeLabels.sectionName(it.section), it.correct, it.total) }
                } else emptyList()
                if (lines.isNotEmpty()) item { SectionLines(lines, state.mode == QuizMode.EXAM) }
            }
            time?.let { t -> item { Text(t, Modifier.testTag("result_time"), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")) } }
            if (state.scriptShownCount > 0) {
                item { Text(stringResource(R.string.result_script_shown, state.scriptShownCount), Modifier.testTag("result_script_shown"), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item { NoticeCard(stringResource(R.string.result_notice), Modifier.testTag("result_notice"), actions.onNotice, Modifier.testTag("result_notice_text")) }
        if (state.status == QuizResultStatus.LOADED) {
            item { ListHeader(state.filter, actions.onFilter) }
            if (visible.isEmpty()) {
                item { Text(stringResource(R.string.result_none_wrong), Modifier.testTag("result_none_wrong"), style = MaterialTheme.typography.bodyMedium) }
            }
            items(visible, key = { it.index }) { ResultRow(it, if (it.index == lastReview) Modifier.focusRequester(rowFocus) else Modifier) { actions.onOpen(it.index) } }
        }
    }
    Button(
        actions.onClose,
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg).heightIn(min = 56.dp).testTag("result_to_practice"),
    ) { Text(stringResource(R.string.quiz_empty_action)) }
}

/** 300 ms 넘게 걸릴 때만 합계 자리표시 + 행 자리표시 3개. */
@Composable
private fun LoadingPlaceholder(modifier: Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(LoadingDelayMs); show = true }
    if (!show) { Box(modifier.fillMaxWidth()); return }
    val loading = stringResource(R.string.detail_loading)
    Column(
        modifier.fillMaxWidth().padding(Spacing.lg).semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(4) { i ->
            Surface(Modifier.fillMaxWidth().height(if (i == 0) 48.dp else 64.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
        }
    }
}

@Composable
private fun TitleLine(state: QuizResultUiState, modifier: Modifier) {
    val level = state.level?.name
    val typeName = state.items.firstOrNull()?.question?.let { q -> state.type?.let { QuizTypeLabels.name(q.section, it) } }
    val text = when {
        level == null -> stringResource(R.string.result_title)
        state.mode == QuizMode.EXAM -> stringResource(R.string.result_title_exam, level)
        typeName == null -> stringResource(R.string.result_title)
        state.mode == QuizMode.LISTENING -> stringResource(R.string.result_title_listening, level, typeName)
        else -> stringResource(R.string.result_title_practice, level, typeName)
    }
    Text(text, modifier.focusable().semantics { heading() }.testTag("result_title"), style = MaterialTheme.typography.headlineSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(state: QuizResultUiState) {
    val score = state.score
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (state.status == QuizResultStatus.EMPTY || score == null) {
            Text(stringResource(R.string.result_empty), Modifier.testTag("result_empty"), style = MaterialTheme.typography.titleLarge)
        } else {
            val p = percent(score.correct, score.total)
            val a11y = stringResource(R.string.result_total_a11y, score.correct, score.total, p)
            // 글꼴 200%에서는 비율이 다음 줄로 내려간다(FlowRow)
            FlowRow(
                Modifier.testTag("result_total").clearAndSetSemantics { contentDescription = a11y },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.Bottom,
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text("${score.correct}/${score.total}", style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"))
                Text(stringResource(R.string.result_percent, p), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            }
            if (state.mode == QuizMode.EXAM && score.unanswered > 0) {
                Text(stringResource(R.string.result_unanswered, score.unanswered), Modifier.testTag("result_unanswered"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.retiredCount > 0) {
            Text(stringResource(R.string.result_retired, state.retiredCount), Modifier.testTag("result_retired"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionLines(lines: List<Triple<String, Int, Int>>, exam: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        lines.forEachIndexed { i, (name, c, t) ->
            val p = percent(c, t)
            val a11y = stringResource(R.string.result_section_a11y, name, c, t, p)
            Column(Modifier.testTag("result_section_$i").clearAndSetSemantics { contentDescription = a11y }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.result_section_line, name, c, t, p), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                LinearProgressIndicator(
                    progress = { if (t == 0) 0f else c.toFloat() / t }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        }
        if (exam) Text(stringResource(R.string.result_no_listening), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun timeLine(state: QuizResultUiState): String? {
    val elapsed = state.elapsedMs ?: return null
    val m = (elapsed / 60_000).toInt()
    val s = (elapsed / 1000 % 60).toInt()
    val limit = state.limitMs ?: return stringResource(R.string.result_time, m, s)
    val limitMin = (limit / 60_000).toInt()
    return stringResource(if (elapsed > limit) R.string.result_time_over else R.string.result_time_limit, m, s, limitMin)
}

@Composable
private fun ListHeader(filter: ResultFilter, onFilter: (ResultFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.result_list_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ResultFilter.entries.forEach { f ->
                val sel = filter == f
                FilterChip(
                    sel, { onFilter(f) }, { Text(stringResource(if (f == ResultFilter.WRONG) R.string.result_filter_wrong else R.string.result_filter_all)) },
                    Modifier.semantics { role = Role.RadioButton }.testTag(if (f == ResultFilter.WRONG) "result_filter_wrong" else "result_filter_all"),
                    leadingIcon = if (sel) {
                        { Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = sel, borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ResultRow(item: QuizReviewItemUi, modifier: Modifier, onClick: () -> Unit) {
    val q = item.question
    val ext = LocalExtendedColors.current
    val type = QuizTypeLabels.name(q.section, q.type)
    val answered = item.chosenIndex != null
    val answerNo = q.answerIndex + 1
    val status = stringResource(if (!answered) R.string.result_status_none else if (item.correct) R.string.result_status_correct else R.string.result_status_wrong)
    val answers = if (answered) stringResource(R.string.result_a11y_answers, answerNo, item.chosenIndex!! + 1) else stringResource(R.string.result_a11y_answers_none, answerNo)
    val script = if (item.scriptShown) stringResource(R.string.result_row_script_a11y) else ""
    val a11y = stringResource(R.string.result_row_a11y, item.index + 1, type, status, answers) + script
    val detail = (
        if (answered) stringResource(R.string.result_row_answers, answerNo, item.chosenIndex!! + 1) else stringResource(R.string.result_row_unanswered, answerNo)
        ) + if (item.scriptShown) stringResource(R.string.result_row_script) else ""
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("result_row_${item.index}")
            .then(modifier).clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = a11y },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        // 상태는 아이콘과 글자 둘 다로 전한다. 답하지 않음은 아이콘 없이 자리만 맞춘다
        when {
            !answered -> Box(Modifier.size(24.dp))
            item.correct -> Icon(painterResource(R.drawable.ic_ms_check_circle), null, Modifier.size(24.dp), tint = ext.correct)
            else -> Icon(painterResource(R.drawable.ic_ms_cancel), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.result_row_title, item.index + 1, type), style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ReviewPane(state: QuizResultUiState, item: QuizReviewItemUi, actions: QuizResultActions) {
    val q = item.question
    val noRuby = QuizTypeLabels.alwaysNoRuby(q.section, q.type)
    val showRuby = state.showRuby && !noRuby
    val visible = state.visibleItems
    val pos = visible.indexOfFirst { it.index == item.index }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    LaunchedEffect(item.index) { scroll.scrollTo(0); runCatching { focus.requestFocus() } } // 문항이 바뀌면 맨 위·제목 줄로
    TopBar(R.drawable.ic_ms_arrow_back, stringResource(R.string.result_review_back), actions.onBackToList, "")
    Column(
        Modifier.weight(1f).fillMaxWidth().testTag("result_review").verticalScroll(scroll)
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.result_review_title, item.index + 1),
            Modifier.focusRequester(focus).focusable().semantics { heading() }.testTag("result_review_title"), style = MaterialTheme.typography.titleLarge,
        )
        Instruction(q)
        q.passage?.let { PassageCard(it, showRuby) }
        StemView(q.stem, showRuby)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            q.choices.forEachIndexed { i, segs ->
                val look = when {
                    i == q.answerIndex -> ChoiceLook.CORRECT
                    i == item.chosenIndex -> ChoiceLook.WRONG
                    else -> ChoiceLook.NORMAL
                }
                ChoiceButton(i + 1, segs, showRuby, look, chosen = i == item.chosenIndex, enabled = false) {}
            }
        }
        if (item.chosenIndex == null) {
            Text(stringResource(R.string.result_review_unanswered), Modifier.testTag("result_review_unanswered"), style = MaterialTheme.typography.bodyMedium)
        }
        ExplanationCard(q, item.chosenIndex, actions.onChipClick)
        val links = state.targetLinks[q.id].orEmpty()
        if (links.isNotEmpty()) {
            Column(Modifier.testTag("result_links")) {
                Text(stringResource(R.string.result_review_links), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    links.forEach { l ->
                        // 종류(단어·한자·문법)를 보이는 글자로 붙인다. 같은 글자의 단어와 한자도 구별되고 TalkBack도 종류를 함께 읽는다
                        OutlinedButton({ actions.onTarget(l) }, Modifier.heightIn(min = 48.dp).testTag("result_link_${l.id}")) {
                            Text(
                                stringResource(when (l.kind) { TargetKind.VOCAB -> R.string.result_link_vocab; TargetKind.KANJI -> R.string.result_link_kanji; TargetKind.GRAMMAR -> R.string.result_link_grammar }),
                                Modifier.padding(end = Spacing.sm), style = MaterialTheme.typography.labelMedium,
                            )
                            JpText(l.label, style = LocalJpTypography.current.bodyLarge)
                        }
                    }
                }
            }
        }
        Box(Modifier.size(Spacing.sm))
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OutlinedButton({ actions.onStep(-1) }, Modifier.weight(1f).heightIn(min = 56.dp).testTag("result_prev"), enabled = pos > 0) {
            Text(stringResource(R.string.result_review_prev))
        }
        OutlinedButton({ actions.onStep(1) }, Modifier.weight(1f).heightIn(min = 56.dp).testTag("result_next"), enabled = pos in 0 until visible.lastIndex) {
            Text(stringResource(R.string.result_review_next))
        }
    }
}
