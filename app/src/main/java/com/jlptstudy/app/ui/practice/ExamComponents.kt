package com.jlptstudy.app.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.theme.Spacing

// S14 모의고사 진행 화면 조각(ui-design 6.10 S14). QuizScreen이 mode EXAM일 때 쓴다.

/** 상단 바: 멈추기(확인 대화상자) · 경과 시간 · 제출. */
@Composable
internal fun ExamTopBar(state: QuizUiState, actions: QuizActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(actions.onClose) { Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.exam_close)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ExamTimer(state.elapsedMs, state.limitMs) }
        val a11y = stringResource(R.string.exam_submit_a11y)
        Box(
            Modifier.defaultMinSize(48.dp, 48.dp).clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClickLabel = a11y, role = Role.Button, onClick = actions.onSubmit).padding(horizontal = Spacing.md).testTag("exam_submit"),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.exam_submit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
    }
}

/** 경과 시간. 1초마다 알리지 않는다(liveRegion 없음). 기준을 넘으면 아래 줄을 더하고 색은 바꾸지 않는다(경고 아님). */
@Composable
private fun ExamTimer(elapsedMs: Long, limitMs: Long?) {
    val over = limitMs != null && elapsedMs > limitMs
    val limitMin = ((limitMs ?: 0) / 60_000).toInt()
    val m = (elapsedMs / 60_000).toInt()
    val s = (elapsedMs / 1000 % 60).toInt()
    val desc = if (over) stringResource(R.string.exam_timer_over_a11y, m, s, limitMin) else stringResource(R.string.exam_timer_a11y, m, s)
    Column(Modifier.testTag("exam_timer").clearAndSetSemantics { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(painterResource(R.drawable.ic_ms_schedule), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(mmss(elapsedMs), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        }
        if (over) Text(stringResource(R.string.exam_over, limitMin), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 문항 번호 이동 줄. 답함 = 채움 + check, 현재 = 2 dp primary 테두리 + 아래 막대(색만으로 구분하지 않음). 영역이 바뀌면 16 dp + 영역 이름. */
@Composable
internal fun ExamNumberRow(state: QuizUiState, onGoTo: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(state.index) { list.scrollToItem((state.index - 2).coerceAtLeast(0)) } // 현재 문항이 보이게
    LazyRow(
        Modifier.fillMaxWidth().testTag("exam_number_row"), state = list, contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalAlignment = Alignment.Bottom, // 영역 이름이 있는 칸과 없는 칸의 칩 높이를 맞춘다
    ) {
        items(state.total) { i ->
            val section = state.sectionStarts.indexOf(i)
            val gap = when { i == 0 -> 0.dp; section > 0 -> 16.dp; else -> 8.dp }
            Column(Modifier.padding(start = gap)) {
                // 영역 이름은 칩 폭을 늘리지 않고 오른쪽으로 넘쳐 그린다(다음 칩들 위는 비어 있다)
                Text(
                    state.sectionTitles.getOrNull(section).orEmpty(),
                    Modifier.clearAndSetSemantics {}.layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                        layout(0, p.height) { p.placeRelative(0, 0) }
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                )
                NumberChip(i + 1, i in state.answeredIndices, i == state.index) { onGoTo(i) }
            }
        }
    }
}

@Composable
private fun NumberChip(n: Int, answered: Boolean, current: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(if (answered) R.string.exam_chip_answered else R.string.exam_chip_unanswered, n) +
        (if (current) stringResource(R.string.exam_chip_current) else "")
    Column(
        Modifier.testTag("exam_chip_$n").clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            Modifier.defaultMinSize(48.dp, 48.dp), shape = MaterialTheme.shapes.small,
            color = if (answered) cs.primaryContainer else cs.surface, contentColor = if (answered) cs.onPrimaryContainer else cs.onSurface,
            border = when { current -> BorderStroke(2.dp, cs.primary); answered -> null; else -> BorderStroke(1.dp, cs.outline) },
        ) {
            Row(Modifier.padding(horizontal = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text("$n", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                if (answered) Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(16.dp).testTag("exam_chip_check_$n"))
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(24.dp).height(3.dp).then(if (current) Modifier.background(cs.primary).testTag("exam_chip_current_$n") else Modifier))
    }
}

/** 하단 고정: "이전"·"다음"(마지막이면 "제출"), 폭 반씩. 글꼴 200%면 세로로 쌓는다. */
@Composable
internal fun ExamBottomBar(state: QuizUiState, actions: QuizActions) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    val last = state.index + 1 >= state.total
    val prev: @Composable (Modifier) -> Unit = { m ->
        TextButton(actions.onPrev, m.heightIn(min = 56.dp).testTag("exam_prev"), enabled = state.index > 0) { Text(stringResource(R.string.exam_prev)) }
    }
    val next: @Composable (Modifier) -> Unit = { m ->
        Button(if (last) actions.onSubmit else actions.onNext, m.heightIn(min = 56.dp).testTag("exam_next")) {
            Text(stringResource(if (last) R.string.exam_submit else R.string.quiz_next))
        }
    }
    val pad = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg)
    if (stacked) {
        Column(pad, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { prev(Modifier.fillMaxWidth()); next(Modifier.fillMaxWidth()) }
    } else {
        Row(pad, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { prev(Modifier.weight(1f)); next(Modifier.weight(1f)) }
    }
}

/** CONFIRM_EXIT. "계속 풀기"가 기본 포커스. */
@Composable
internal fun ExamExitDialog(actions: QuizActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = actions.onContinue,
        title = { Text(stringResource(R.string.exam_exit_title)) },
        text = { Text(stringResource(R.string.exam_exit_body)) },
        confirmButton = { TextButton(actions.onStopExit, Modifier.testTag("exam_exit_stop")) { Text(stringResource(R.string.exam_exit_stop)) } },
        dismissButton = { TextButton(actions.onContinue, Modifier.focusRequester(focus).testTag("exam_exit_continue")) { Text(stringResource(R.string.exam_exit_continue)) } },
    )
}

/** CONFIRM_SUBMIT(미응답 > 0). "돌아가서 풀기"가 기본 포커스. */
@Composable
internal fun ExamSubmitDialog(unanswered: Int, actions: QuizActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = actions.onCancelSubmit,
        title = { Text(stringResource(R.string.exam_submit_title, unanswered)) },
        text = { Text(stringResource(R.string.exam_submit_body)) },
        confirmButton = { TextButton(actions.onConfirmSubmit, Modifier.testTag("exam_submit_anyway")) { Text(stringResource(R.string.exam_submit_anyway)) } },
        dismissButton = { TextButton(actions.onCancelSubmit, Modifier.focusRequester(focus).testTag("exam_submit_back")) { Text(stringResource(R.string.exam_submit_back)) } },
    )
}
