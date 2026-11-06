package com.jlptstudy.app.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.JpSpans
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.ui.common.RubyText
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.common.StatusScreen
import com.jlptstudy.app.ui.detail.FuriganaToggle
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.review.ProvenanceChip
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan
import com.jlptstudy.app.ui.theme.LocalExtendedColors
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing

class QuizActions(
    val onClose: () -> Unit = {}, val onChoose: (Int) -> Unit = {}, val onNext: () -> Unit = {}, val onToggleFurigana: () -> Unit = {},
    val onChipClick: ((MeaningUi) -> Unit)? = null,
)

@Composable
fun QuizRoute(vm: QuizViewModel, onExit: () -> Unit, onResult: (Long) -> Unit, onChipClick: ((MeaningUi) -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val exit = { vm.exit(onExit) }
    BackHandler { exit() } // 확인 없이 finish 후 S12
    LaunchedEffect(state.status) { if (state.status == QuizStatus.SUBMITTED) onResult(state.attemptId) }
    val message = stringResource(if (state.status == QuizStatus.ANSWERING) R.string.quiz_save_failed else R.string.quiz_finish_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed && snackbar.showSnackbar(message, retry, duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) vm.retry()
    }
    QuizScreen(state, snackbar, QuizActions(exit, vm::choose, vm::next, vm::toggleFurigana, onChipClick))
}

/** S13(ui-design 6.10): 연습 문제 풀이. 고르면 즉시 채점해 피드백과 해설 카드를 보인다. */
@Composable
fun QuizScreen(state: QuizUiState, snackbar: SnackbarHostState = remember { SnackbarHostState() }, actions: QuizActions = QuizActions()) {
    val q = state.question
    if (state.status == QuizStatus.EMPTY) {
        StatusScreen(
            stringResource(R.string.quiz_empty_title), body = stringResource(R.string.quiz_empty_body),
            icon = painterResource(R.drawable.ic_ms_info), actions = listOf(StatusAction(stringResource(R.string.quiz_empty_action), actions.onClose)),
        )
        return
    }
    Surface(Modifier.fillMaxSize().testTag("quiz_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter)) {
                val noRuby = q != null && QuizTypeLabels.alwaysNoRuby(q.section, q.type)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(actions.onClose) { Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.quiz_close)) }
                    Box(Modifier.weight(1f))
                    if (q != null && !noRuby) FuriganaToggle(state.showRuby, actions.onToggleFurigana)
                }
                if (q == null) {
                    StatusContent(stringResource(R.string.detail_loading), loading = true, takeFocus = false, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    QuizBody(state, q, noRuby, actions, Modifier.weight(1f))
                    if (state.status == QuizStatus.FEEDBACK) {
                        Button(
                            actions.onNext,
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg)
                                .heightIn(min = 56.dp).testTag("quiz_next"),
                        ) { Text(stringResource(if (state.index + 1 >= state.total) R.string.quiz_result else R.string.quiz_next)) }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun QuizBody(state: QuizUiState, q: QuizQuestionUi, noRuby: Boolean, actions: QuizActions, modifier: Modifier) {
    val jp = LocalJpTypography.current
    val showRuby = state.showRuby && !noRuby
    // 문항이 바뀌면 접근성 포커스를 진행 표시("3/10")로 옮긴다: "다음" 버튼이 사라져도 새 문항이 읽힌다(WCAG 2.4.3)
    val progressFocus = remember { FocusRequester() }
    LaunchedEffect(q.id) { runCatching { progressFocus.requestFocus() } }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${state.index + 1}/${state.total}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.focusRequester(progressFocus).focusable().testTag("quiz_progress"))
                Text(QuizTypeLabels.name(q.section, q.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else (state.index + if (state.status == QuizStatus.FEEDBACK) 1 else 0).toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().heightIn(min = 4.dp).clearAndSetSemantics {}, // 같은 줄의 "3/10"이 정보를 담는다
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Instruction(q)
            q.passage?.let { p ->
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.padding(Spacing.lg)) { RubyText(p, style = jp.bodyLarge, showRuby = showRuby) }
                }
            }
            StemView(q.stem, showRuby)
            val feedback = state.status == QuizStatus.FEEDBACK
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                q.choices.forEachIndexed { i, segs ->
                    val look = when {
                        feedback && i == q.answerIndex -> ChoiceLook.CORRECT
                        feedback && i == state.chosenIndex -> ChoiceLook.WRONG
                        !feedback && i == state.chosenIndex -> ChoiceLook.SELECTED
                        else -> ChoiceLook.NORMAL
                    }
                    ChoiceButton(
                        number = i + 1, segments = segs, showRuby = showRuby, look = look,
                        chosen = i == state.chosenIndex, enabled = state.status == QuizStatus.ANSWERING && state.chosenIndex == null && !state.saving,
                    ) { actions.onChoose(i) }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 4.dp)) {
                if (state.saving) {
                    val saving = stringResource(R.string.quiz_saving)
                    LinearProgressIndicator(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = saving })
                }
            }
            if (feedback) {
                FeedbackLine(q, state.chosenIndex, showRuby)
                ExplanationCard(q, state.chosenIndex, actions.onChipClick)
            }
            Box(Modifier.size(Spacing.sm))
        }
    }
}

/** 지시문: 한국어 문구 + `{대상}` 일본어 구간(ja-JP). 문항 target이 없으면 대상 자리를 비운다. */
@Composable
private fun Instruction(q: QuizQuestionUi) {
    val text = remember(q.id) {
        val tpl = QuizTypeLabels.instruction(q.section, q.type)
        buildAnnotatedString {
            val at = tpl.indexOf(QuizTypeLabels.TARGET)
            if (at < 0) withStyle(KoSpan) { append(tpl) } else {
                withStyle(KoSpan) { append(tpl.substring(0, at)) }
                withStyle(JpSpan) { append(q.target.orEmpty()) }
                withStyle(KoSpan) { append(tpl.substring(at + QuizTypeLabels.TARGET.length)) }
            }
        }
    }
    Text(text, Modifier.testTag("quiz_instruction"), style = MaterialTheme.typography.bodyLarge)
}

/** 지문 `stem`: 빈칸 토큰을 앱이 그리는 칸으로 바꾼다. 토큰이 없으면 RubyText 하나. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StemView(stem: List<RubySegment>, showRuby: Boolean) {
    val jp = LocalJpTypography.current
    val parts = remember(stem) { QuizStem.split(stem) }
    if (parts.none { it !is StemPart.Text }) {
        RubyText(stem, Modifier.testTag("quiz_stem"), style = jp.bodyLarge, showRuby = showRuby)
        return
    }
    // 연속한 글자 조각은 한 RubyText로 묶는다(구간 경계에서만 줄바꿈).
    val items = remember(parts) {
        buildList<Any> {
            var run = mutableListOf<RubySegment>()
            parts.forEach { p ->
                if (p is StemPart.Text) run.add(p.segment) else { if (run.isNotEmpty()) add(run); run = mutableListOf(); add(p) }
            }
            if (run.isNotEmpty()) add(run)
        }
    }
    FlowRow(Modifier.testTag("quiz_stem"), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.forEach { item ->
            when (item) {
                is StemPart.Blank -> BlankBox(star = false)
                is StemPart.StarBlank -> BlankBox(star = true)
                else -> @Suppress("UNCHECKED_CAST") RubyText(item as List<RubySegment>, style = jp.bodyLarge, showRuby = showRuby)
            }
        }
    }
}

@Composable
private fun BlankBox(star: Boolean) {
    val label = stringResource(if (star) R.string.quiz_blank_star else R.string.quiz_blank)
    Column(Modifier.clearAndSetSemantics { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.defaultMinSize(48.dp, 32.dp).border(
                if (star) 2.dp else 1.dp, if (star) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall,
            ),
        )
        if (star) Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

enum class ChoiceLook { NORMAL, SELECTED, CORRECT, WRONG }

/** 선택지(ui-design 5.4·5.5). 동작 수정자를 clearAndSetSemantics보다 바깥에 두어 Role·Selected·OnClick을 남긴다(4.3 5번). */
@Composable
fun ChoiceButton(
    number: Int, segments: List<RubySegment>, showRuby: Boolean, look: ChoiceLook, chosen: Boolean, enabled: Boolean, onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current
    val jp = LocalJpTypography.current
    val (bg, fg) = when (look) {
        ChoiceLook.CORRECT -> ext.correctContainer to ext.onCorrectContainer
        ChoiceLook.WRONG -> cs.errorContainer to cs.onErrorContainer
        ChoiceLook.SELECTED -> cs.primaryContainer to cs.onPrimaryContainer
        ChoiceLook.NORMAL -> cs.surface to cs.onSurface
    }
    val border = if (look == ChoiceLook.SELECTED) BorderStroke(2.dp, cs.primary) else BorderStroke(1.dp, cs.outline)
    val numberLabel = stringResource(R.string.quiz_choice_number, number)
    val readingLabel = stringResource(R.string.quiz_choice_reading)
    val suffix = when (look) {
        ChoiceLook.CORRECT -> stringResource(R.string.quiz_choice_correct)
        ChoiceLook.WRONG -> stringResource(R.string.quiz_choice_wrong)
        else -> null
    }
    val spoken = remember(segments, showRuby, numberLabel, readingLabel, suffix) {
        buildAnnotatedString {
            withStyle(KoSpan) { append(numberLabel) }
            withStyle(JpSpan) { append(segments.joinToString("") { it.base }) }
            if (showRuby && segments.any { it.ruby != null }) {
                withStyle(KoSpan) { append(readingLabel) }
                withStyle(JpSpan) { append(segments.joinToString("") { it.ruby ?: it.base }) }
            }
            if (suffix != null) withStyle(KoSpan) { append(", $suffix") }
        }
    }
    Surface(
        Modifier.testTag("quiz_choice_$number").fillMaxWidth().heightIn(min = 56.dp)
            // 채점 뒤·저장 중에는 클릭 없이 역할·선택만 남긴다: enabled=false면 TalkBack이 모든 선택지를 "비활성"으로 읽는다
            .then(
                if (enabled) Modifier.selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
                else Modifier.semantics { role = Role.RadioButton; selected = chosen },
            )
            .clearAndSetSemantics { text = spoken },
        shape = MaterialTheme.shapes.medium, color = bg, contentColor = fg, border = border,
    ) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.defaultMinSize(28.dp, 28.dp).border(1.dp, fg, MaterialTheme.shapes.large).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$number", style = MaterialTheme.typography.labelLarge) }
            Column(Modifier.weight(1f)) {
                RubyText(segments, style = jp.bodyLarge, showRuby = showRuby)
                if (suffix != null) Text(suffix, style = MaterialTheme.typography.labelLarge)
            }
            when (look) {
                ChoiceLook.CORRECT -> Icon(painterResource(R.drawable.ic_ms_check_circle), null, Modifier.size(24.dp), tint = ext.correct)
                ChoiceLook.WRONG -> Icon(painterResource(R.drawable.ic_ms_cancel), null, Modifier.size(24.dp), tint = cs.error)
                ChoiceLook.SELECTED -> Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(24.dp), tint = cs.primary)
                ChoiceLook.NORMAL -> Unit
            }
        }
    }
}

/** 피드백 노드(5.5): 선택 직후 Polite로 한 번 읽는다. 일본어 정답은 JpSpan으로 건다. */
@Composable
private fun FeedbackLine(q: QuizQuestionUi, chosen: Int?, showRuby: Boolean) {
    val right = chosen == q.answerIndex
    val ext = LocalExtendedColors.current
    val head = stringResource(if (right) R.string.quiz_feedback_correct else R.string.quiz_feedback_wrong)
    val tail = stringResource(R.string.quiz_feedback_wrong_answer, q.answerIndex + 1)
    val answer = q.choices[q.answerIndex].joinToString("") { it.base }
    val spoken: AnnotatedString = remember(head, tail, answer, right) {
        buildAnnotatedString {
            withStyle(KoSpan) { append(head) }
            if (!right) { withStyle(KoSpan) { append(tail) }; withStyle(JpSpan) { append(answer) } }
        }
    }
    Row(
        Modifier.testTag("quiz_feedback").clearAndSetSemantics { text = spoken; liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (right) R.drawable.ic_ms_check_circle else R.drawable.ic_ms_cancel), null, Modifier.size(24.dp),
            tint = if (right) ext.correct else MaterialTheme.colorScheme.error,
        )
        Text(head, style = MaterialTheme.typography.labelLarge)
    }
}

/** 해설 카드(5.5): 정답 근거, 고른 오답의 이유를 맨 위로 한 오답별 이유, 출처 칩과 검토 중 표시. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplanationCard(q: QuizQuestionUi, chosen: Int?, onChipClick: ((MeaningUi) -> Unit)?) {
    Surface(Modifier.fillMaxWidth().testTag("quiz_explanation"), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.quiz_expl_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            q.explanationKo.split("\n\n").filter { it.isNotBlank() }.forEach {
                Text(JpSpans.annotate(it.trim()), style = MaterialTheme.typography.bodyMedium)
            }
            val order = q.whyWrongKo.indices.filter { it != q.answerIndex && !q.whyWrongKo[it].isNullOrBlank() }
                .sortedBy { if (it == chosen) 0 else 1 }
            order.forEach { i ->
                Column {
                    Text(
                        stringResource(if (i == chosen) R.string.quiz_expl_why_chosen else R.string.quiz_expl_why, i + 1),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(JpSpans.annotate(q.whyWrongKo[i].orEmpty()), style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ProvenanceChip(q.provenance, onChipClick)
                if (q.needsReview) {
                    Text(
                        stringResource(R.string.quiz_review_chip),
                        Modifier.align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                            .padding(horizontal = Spacing.sm, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
