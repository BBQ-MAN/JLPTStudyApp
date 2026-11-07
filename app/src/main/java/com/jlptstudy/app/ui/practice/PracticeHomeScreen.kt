package com.jlptstudy.app.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.theme.Spacing
import com.jlptstudy.quiz.Section

class PracticeHomeActions(
    val onSelectLevel: (JlptLevel) -> Unit = {}, val onStartType: (Section, String) -> Unit = { _, _ -> },
    val onStartExam: (JlptLevel) -> Unit = {}, val onResume: (Long) -> Unit = {},
    val onRequestAbandon: () -> Unit = {}, val onConfirmAbandon: () -> Unit = {}, val onCancelAbandon: () -> Unit = {},
    val onNotice: () -> Unit = {},
    val onResumeReplace: () -> Unit = {}, val onReplace: () -> Unit = {}, val onCancelReplace: () -> Unit = {},
)

@Composable
fun PracticeHomeRoute(vm: PracticeHomeViewModel, onQuiz: (Long) -> Unit, onListening: (Long) -> Unit, onExamStart: (JlptLevel) -> Unit, onNotice: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.reload() } // Quiz에서 돌아오면 미완료 시도를 다시 읽는다
    val failed = stringResource(R.string.practice_start_failed)
    val abandonFailed = stringResource(R.string.practice_abandon_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(state.startFailed) {
        if (state.startFailed) {
            val r = snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) vm.retryStart() else vm.startFailedShown()
        }
    }
    LaunchedEffect(state.abandonFailed) {
        if (state.abandonFailed) { snackbar.showSnackbar(abandonFailed); vm.abandonFailedShown() }
    }
    PracticeHomeScreen(
        state, snackbar,
        PracticeHomeActions(
            vm::selectLevel, { s, t -> vm.startPractice(s, t) { id -> if (s == Section.LISTENING) onListening(id) else onQuiz(id) } }, { vm.requestStartExam(onExamStart) }, onQuiz,
            vm::requestAbandon, vm::confirmAbandon, vm::cancelAbandon, onNotice,
            { vm.resumeFromReplace(onQuiz) }, { vm.replaceWithNew(onExamStart) }, vm::cancelReplace,
        ),
    )
}

/** S12(ui-design 6.10): 급수 칩, 이어서 풀기 카드, 모의고사 카드, 영역별 유형 행, 맨 아래 N-11 고지 줄. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeHomeScreen(state: PracticeHomeUiState, snackbar: SnackbarHostState = remember { SnackbarHostState() }, actions: PracticeHomeActions = PracticeHomeActions()) {
    Surface(Modifier.fillMaxSize().testTag("practice_home_screen"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Text(
                stringResource(R.string.practice_title),
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.levels.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    state.levels.forEach { l ->
                        val sel = l == state.level
                        FilterChip(
                            selected = sel, onClick = { actions.onSelectLevel(l) }, label = { Text(l.name) },
                            leadingIcon = if (sel) {
                                { Icon(painterResource(R.drawable.ic_ms_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = sel, borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.semantics { role = Role.RadioButton }.testTag("practice_level_chip_${l.name}"),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    state.resume?.let { ResumeCard(it, actions) }
                    when (state.status) {
                        PracticeHomeStatus.LOADING -> repeat(5) {
                            Surface(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.lg),
                                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {}
                        }
                        PracticeHomeStatus.EMPTY -> StatusContent(
                            stringResource(R.string.practice_empty_title), body = stringResource(R.string.practice_empty_body), takeFocus = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        PracticeHomeStatus.LOADED -> {
                            state.exam?.let { ExamCard(it, actions) }
                            state.types.groupBy { it.section }.forEach { (section, rows) ->
                                Text(
                                    QuizTypeLabels.sectionName(section),
                                    Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm).semantics { heading() },
                                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column {
                                    rows.forEach { TypeRow(it, actions) }
                                }
                            }
                        }
                    }
                    NoticeCard(stringResource(R.string.practice_notice), Modifier.padding(horizontal = Spacing.lg), actions.onNotice, Modifier.testTag("practice_notice"))
                    Spacer(Modifier.navigationBarsPadding())
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    if (state.confirmAbandon) AbandonDialog(actions)
    state.confirmReplace?.let { ReplaceDialog(it, actions) }
}

internal fun mmss(ms: Long): String = String.format(java.util.Locale.ROOT, "%02d:%02d", ms / 60_000, ms / 1000 % 60)

@Composable
private fun ResumeCard(r: ResumeExamUi, actions: PracticeHomeActions) {
    val stacked = LocalDensity.current.fontScale > 1.3f // 글꼴 200%: 두 버튼을 세로로 쌓는다
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("practice_resume_card"),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // 화면은 mm:ss, TalkBack에는 "분 초"로 읽혀 시각(22시 05분)으로 들리지 않게 한다
            val spoken = stringResource(R.string.practice_resume_summary_a11y, r.level.name, r.answered, r.total, r.elapsedMs / 60_000, r.elapsedMs / 1000 % 60)
            Text(
                stringResource(R.string.practice_resume_summary, r.level.name, r.answered, r.total, mmss(r.elapsedMs)),
                Modifier.semantics { contentDescription = spoken },
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
            val resume: @Composable (Modifier) -> Unit = { m ->
                Button({ actions.onResume(r.attemptId) }, m.heightIn(min = 48.dp)) { Text(stringResource(R.string.practice_resume_action)) }
            }
            val abandon: @Composable (Modifier) -> Unit = { m ->
                TextButton({ actions.onRequestAbandon() }, m.heightIn(min = 48.dp).testTag("practice_abandon")) { Text(stringResource(R.string.practice_abandon)) }
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { resume(Modifier.fillMaxWidth()); abandon(Modifier.fillMaxWidth()) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) { resume(Modifier); abandon(Modifier) }
            }
        }
    }
}

@Composable
private fun ExamCard(e: ExamCardUi, actions: PracticeHomeActions) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("practice_exam_card"),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.practice_exam_title, e.level.name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.practice_exam_info, e.questionCount, (e.limitMs / 60_000).toInt()),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
            Text(stringResource(R.string.practice_exam_no_listening), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val a11y = stringResource(R.string.practice_exam_start_a11y)
            val reason = stringResource(R.string.practice_exam_unavailable)
            Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // 비활성 이유는 버튼의 stateDescription으로 읽힌다(보이는 이유 글은 중복 낭독 방지로 숨김)
                Button(
                    { actions.onStartExam(e.level) },
                    Modifier.heightIn(min = 48.dp).testTag("practice_exam_start")
                        .then(if (e.hasShortfall) Modifier.semantics { contentDescription = a11y; stateDescription = reason } else Modifier),
                    enabled = !e.hasShortfall,
                ) {
                    Text(stringResource(R.string.practice_exam_start))
                }
                if (e.hasShortfall) {
                    Text(reason, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TypeRow(t: PracticeTypeUi, actions: PracticeHomeActions) {
    val name = QuizTypeLabels.name(t.section, t.type)
    val a11y = stringResource(R.string.practice_type_a11y, name, t.count)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(onClickLabel = stringResource(R.string.practice_type_start), role = Role.Button) { actions.onStartType(t.section, t.type) }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .semantics(mergeDescendants = true) { contentDescription = a11y }.testTag("practice_type_${t.section.code}_${t.type}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.practice_type_count, t.count), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}

/** 문제 고지 카드(ui-design 6.10): 문자열 리소스이므로 attribution.json이 없어도 늘 보인다. onMore가 있으면 "자세히". */
@Composable
internal fun NoticeCard(text: String, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null, textModifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_ms_info), contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(text, textModifier, style = MaterialTheme.typography.bodyMedium)
                if (onMore != null) {
                    val moreA11y = stringResource(R.string.practice_notice_more_a11y) // 보이는 "자세히"를 포함한 목적지 이름
                    TextButton(onMore, Modifier.heightIn(min = 48.dp).semantics { contentDescription = moreA11y }) { Text(stringResource(R.string.practice_notice_more)) }
                }
            }
        }
    }
}

@Composable
private fun AbandonDialog(actions: PracticeHomeActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } } // "취소"가 기본 포커스
    AlertDialog(
        onDismissRequest = actions.onCancelAbandon,
        title = { Text(stringResource(R.string.practice_abandon_title)) },
        text = { Text(stringResource(R.string.practice_abandon_body)) },
        confirmButton = { TextButton(actions.onConfirmAbandon, Modifier.testTag("practice_abandon_confirm")) { Text(stringResource(R.string.practice_abandon)) } },
        dismissButton = { TextButton(actions.onCancelAbandon, Modifier.focusRequester(focus)) { Text(stringResource(R.string.practice_cancel)) } },
    )
}

/** 미완료 모의고사가 있는데 "시작"(ui-design 7절 S12 confirmReplace). "이어서 풀기"가 기본 포커스. */
@Composable
private fun ReplaceDialog(r: ResumeExamUi, actions: PracticeHomeActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = actions.onCancelReplace,
        title = { Text(stringResource(R.string.practice_replace_title)) },
        text = {
            // 카드와 같이 경과는 "분 초"로 읽힌다
            val spoken = stringResource(R.string.practice_replace_body_a11y, r.level.name, r.answered, r.total, r.elapsedMs / 60_000, r.elapsedMs / 1000 % 60)
            Text(
                stringResource(R.string.practice_replace_body, r.level.name, r.answered, r.total, mmss(r.elapsedMs)),
                Modifier.testTag("practice_replace_body").semantics { contentDescription = spoken },
            )
        },
        // 버튼 셋: 세로로 쌓아 글꼴 200%에서도 잘리지 않게 한다
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(actions.onResumeReplace, Modifier.focusRequester(focus).testTag("practice_replace_resume")) { Text(stringResource(R.string.practice_resume_action)) }
                TextButton(actions.onReplace, Modifier.testTag("practice_replace_new")) { Text(stringResource(R.string.practice_replace_new)) }
                TextButton(actions.onCancelReplace) { Text(stringResource(R.string.practice_cancel)) }
            }
        },
    )
}
