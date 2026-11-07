package com.jlptstudy.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.theme.Spacing

class ExamStartActions(val onBack: () -> Unit = {}, val onStart: () -> Unit = {}, val onReload: () -> Unit = {})

@Composable
fun ExamStartRoute(vm: ExamStartViewModel, onBack: () -> Unit, onStarted: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.practice_start_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(state.startFailed) {
        if (state.startFailed) {
            val r = snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) vm.retryStart() else vm.startFailedShown()
        }
    }
    // 이동은 지금 컴포지션의 콜백(지금 NavController)으로: 회전 중에 시작이 끝나도 새 화면이 이어받는다
    LaunchedEffect(state.startedAttemptId) {
        state.startedAttemptId?.let { onStarted(it); vm.startedConsumed() }
    }
    ExamStartScreen(state, snackbar, ExamStartActions(onBack, vm::start, vm::load))
}

/** S14 시작 화면(ui-design 6.10): 영역별 줄 → 합계 → 청해 제외 줄 → 고지 카드 → 시간 안내, 하단 "시작". 시도 행은 아직 없다. */
@Composable
fun ExamStartScreen(state: ExamStartUiState, snackbar: SnackbarHostState = remember { SnackbarHostState() }, actions: ExamStartActions = ExamStartActions()) {
    Surface(Modifier.fillMaxSize().testTag("exam_start_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(actions.onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.detail_back)) }
                    Text(
                        stringResource(R.string.practice_exam_title, state.level.name),
                        Modifier.weight(1f).padding(start = Spacing.xs).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                    )
                }
                val toPractice = StatusAction(stringResource(R.string.quiz_empty_action), actions.onBack)
                when (state.status) {
                    ExamStartStatus.LOADING -> Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        val loading = stringResource(R.string.detail_loading)
                        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            repeat(5) {
                                Surface(Modifier.fillMaxWidth().heightIn(min = 24.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
                            }
                        }
                    }
                    ExamStartStatus.EMPTY -> StatusContent(
                        stringResource(R.string.exam_empty_title), body = stringResource(R.string.exam_empty_body),
                        icon = painterResource(R.drawable.ic_ms_info), actions = listOf(toPractice), modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ExamStartStatus.FAILED -> StatusContent(
                        stringResource(R.string.exam_failed_title), body = stringResource(R.string.exam_failed_body),
                        icon = painterResource(R.drawable.ic_ms_error),
                        actions = listOf(StatusAction(stringResource(R.string.action_retry), actions.onReload), toPractice),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ExamStartStatus.LOADED -> {
                        IntroBody(state, Modifier.weight(1f))
                        StartButton(state, actions.onStart)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun IntroBody(state: ExamStartUiState, modifier: Modifier) {
    val tnum = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // 이름(titleMedium)과 숫자(bodyMedium tnum)를 두 줄로: 이름 안의 '·'와 구분자가 섞이지 않고 글꼴 200%에서도 숫자가 한 덩어리로 남는다
            state.sections.forEachIndexed { i, s ->
                Column(Modifier.testTag("exam_section_$i").semantics(mergeDescendants = true) {}) {
                    Text(s.titleKo, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.exam_section_detail, s.count, s.minutes), style = tnum)
                }
            }
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.exam_total_line, state.total, (state.limitMs / 60_000).toInt()), Modifier.testTag("exam_total"),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        )
        Text(stringResource(R.string.exam_no_listening), style = MaterialTheme.typography.bodyMedium)
        NoticeCard(stringResource(R.string.exam_notice))
        Text(stringResource(R.string.exam_time_guide), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StartButton(state: ExamStartUiState, onStart: () -> Unit) {
    val a11y = stringResource(R.string.practice_exam_start_a11y)
    val reason = stringResource(R.string.practice_exam_unavailable)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (state.hasShortfall) {
            // 이유는 버튼의 stateDescription으로 읽힌다: "모의고사 시작, 문항이 모자라…, 사용 중지됨"
            Text(reason, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onStart,
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("exam_start")
                .then(if (state.hasShortfall) Modifier.semantics { contentDescription = a11y; stateDescription = reason } else Modifier),
            enabled = !state.hasShortfall && !state.starting,
        ) {
            Text(stringResource(R.string.practice_exam_start))
        }
    }
}
