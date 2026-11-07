package com.jlptstudy.app.ui.listening

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.R
import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.speech.Voice
import com.jlptstudy.app.ui.common.LocalFrozenProgress
import com.jlptstudy.app.ui.common.RubyText
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.common.StatusScreen
import com.jlptstudy.app.ui.common.startExternal
import com.jlptstudy.app.ui.detail.FuriganaToggle
import com.jlptstudy.app.ui.practice.ChoiceButton
import com.jlptstudy.app.ui.practice.ChoiceLook
import com.jlptstudy.app.ui.practice.ExplanationCard
import com.jlptstudy.app.ui.practice.FeedbackLine
import com.jlptstudy.app.ui.practice.Instruction
import com.jlptstudy.app.ui.practice.ListeningLineUi
import com.jlptstudy.app.ui.practice.QuizQuestionUi
import com.jlptstudy.app.ui.practice.QuizTypeLabels
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing

class ListeningActions(
    val onClose: () -> Unit = {}, val onChoose: (Int) -> Unit = {}, val onNext: () -> Unit = {}, val onToggleFurigana: () -> Unit = {},
    val onChipClick: ((MeaningUi) -> Unit)? = null,
    val onPlay: () -> Unit = {}, val onStop: () -> Unit = {}, val onRetrySpeaker: () -> Unit = {}, val onToggleRate: () -> Unit = {},
    val onToggleScript: () -> Unit = {}, val onOpenScript: () -> Unit = {},
    val onInstallVoice: () -> Unit = {}, val onOpenVoiceSettings: () -> Unit = {}, val onNotice: () -> Unit = {},
)

@Composable
fun ListeningRoute(
    vm: ListeningViewModel, onExit: () -> Unit, onResult: (Long) -> Unit, onNotice: () -> Unit, onChipClick: ((MeaningUi) -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    BackHandler { vm.exit(onExit) }
    LaunchedEffect(state.status) { if (state.status == ListeningStatus.SUBMITTED) onResult(state.attemptId) }
    // 설치·설정 화면에서 돌아오면 ON_START다(ON_RESUME은 쓰지 않는다 — ON_STOP의 stop()과 짝, architecture.md 9.3.2)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.stopPlayback() } } // 화면을 떠나면 정지
    val message = stringResource(if (state.finishFailed) R.string.quiz_finish_failed else R.string.quiz_save_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed && snackbar.showSnackbar(message, retry, duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) vm.retry()
    }
    ListeningScreen(
        state, snackbar,
        ListeningActions(
            { vm.exit(onExit) }, vm::choose, vm::next, vm::toggleFurigana, onChipClick,
            vm::play, vm::stopPlayback, vm::retrySpeaker, vm::toggleRate, vm::toggleScript, vm::openScript,
            { vm.externalOpened(context.startExternal(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) },
            { vm.externalOpened(context.startExternal(Intent(TTS_SETTINGS_ACTION))) },
            onNotice,
        ),
    )
}

private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"

/** S16(ui-design 6.11): 지시문, 질문, 재생 컨트롤, 대본 패널, 선택지, 피드백·해설, 음성 고지. */
@Composable
fun ListeningScreen(
    state: ListeningUiState, snackbar: SnackbarHostState = remember { SnackbarHostState() }, actions: ListeningActions = ListeningActions(),
) {
    if (state.status == ListeningStatus.EMPTY) {
        StatusScreen(
            stringResource(R.string.quiz_empty_title), body = stringResource(R.string.quiz_empty_body),
            icon = painterResource(R.drawable.ic_ms_info), actions = listOf(StatusAction(stringResource(R.string.quiz_empty_action), actions.onClose)),
        )
        return
    }
    val q = state.question
    Surface(Modifier.fillMaxSize().testTag("listening_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(actions.onClose) { Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.listening_close)) }
                    Box(Modifier.weight(1f))
                    if (q != null) FuriganaToggle(state.showRuby, actions.onToggleFurigana)
                }
                if (q == null) {
                    StatusContent(stringResource(R.string.detail_loading), loading = true, takeFocus = false, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    // 글꼴 200%에서는 고지가 스크롤 영역을 줄이지 않게 본문 맨 끝으로 옮긴다(SC 1.4.4)
                    val bigFont = LocalDensity.current.fontScale > 1.3f
                    val notice: @Composable () -> Unit = { state.audioNotice?.let { AudioNotice(it.body, actions.onNotice) } }
                    ListeningBody(state, q, actions, Modifier.weight(1f), if (bigFont) notice else null)
                    if (state.status == ListeningStatus.FEEDBACK) {
                        Button(
                            actions.onNext,
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.sm)
                                .heightIn(min = 56.dp).testTag("listening_next"),
                        ) { Text(stringResource(if (state.index + 1 >= state.total) R.string.quiz_result else R.string.quiz_next)) }
                    }
                    if (!bigFont) notice()
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ListeningBody(state: ListeningUiState, q: QuizQuestionUi, actions: ListeningActions, modifier: Modifier, scrollNotice: (@Composable () -> Unit)? = null) {
    val jp = LocalJpTypography.current
    val showRuby = state.showRuby
    val feedback = state.status == ListeningStatus.FEEDBACK
    // 문항이 바뀌면 접근성 포커스를 지시문으로 옮긴다(ui-design 6.11 TalkBack)
    val focus = remember { FocusRequester() }
    LaunchedEffect(q.id) { runCatching { focus.requestFocus() } }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${state.index + 1}/${state.total}", Modifier.testTag("listening_progress"), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                Text(QuizTypeLabels.name(q.section, q.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else (state.index + if (feedback) 1 else 0).toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().heightIn(min = 4.dp).clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Instruction(q, Modifier.focusRequester(focus).focusable())
            // 질문이 없는 유형(즉시 응답 등)은 줄째 숨긴다. 질문은 재생하지 않는다.
            if (q.stem.isNotEmpty()) RubyText(q.stem, Modifier.testTag("listening_question"), style = jp.bodyLarge, showRuby = showRuby)
            PlaybackControl(state, q.id, actions)
            q.lines?.let { ScriptPanel(it, state.scriptOpen, showRuby, actions.onToggleScript) }
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                q.choices.forEachIndexed { i, segs ->
                    val look = when {
                        feedback && i == q.answerIndex -> ChoiceLook.CORRECT
                        feedback && i == state.chosenIndex -> ChoiceLook.WRONG
                        !feedback && i == state.chosenIndex -> ChoiceLook.SELECTED
                        else -> ChoiceLook.NORMAL
                    }
                    ChoiceButton(
                        number = i + 1, segments = segs, showRuby = showRuby, look = look, chosen = i == state.chosenIndex,
                        enabled = state.status == ListeningStatus.ANSWERING && state.chosenIndex == null && !state.saving,
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
            scrollNotice?.invoke()
            Box(Modifier.size(Spacing.sm))
        }
    }
}

/** 재생 컨트롤(ui-design 5.8). 상태 알림은 옆 문구 노드 하나(Polite)이고 "재생 중"/"정지됨" 두 가지뿐이다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackControl(state: ListeningUiState, questionId: String, actions: ListeningActions) {
    val sp = state.speaker
    var wasPlaying by remember(questionId) { mutableStateOf(false) }
    SideEffect { if (sp is SpeakerState.Playing) wasPlaying = true }
    val side = when (sp) {
        SpeakerState.Initializing -> R.string.listening_side_initializing
        SpeakerState.Ready -> if (wasPlaying) R.string.listening_side_stopped else null
        is SpeakerState.Playing -> R.string.listening_side_playing
        SpeakerState.MissingJapaneseData -> R.string.listening_side_missing
        SpeakerState.NoEngine -> R.string.listening_side_no_engine
        SpeakerState.Error -> R.string.listening_side_error
    }
    val sideText = side?.let { stringResource(it) }.orEmpty()
    val stacked = LocalDensity.current.fontScale > 1.3f // 글꼴 200%: 옆 문구는 버튼 아래 줄로
    val primary: @Composable () -> Unit = { PrimaryButton(sp, actions) }
    val sideNode: @Composable (Modifier) -> Unit = { m ->
        Text(sideText, m.testTag("listening_side").semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md).testTag("listening_playback"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (stacked) {
            primary(); if (sideText.isNotEmpty()) sideNode(Modifier)
        } else {
            // 버튼은 항상 가운데: 좌우 weight 칸을 대칭으로 두고 옆 문구는 오른쪽 칸에 둔다
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                primary()
                Box(Modifier.weight(1f).padding(start = Spacing.md)) { if (sideText.isNotEmpty()) sideNode(Modifier) }
            }
        }
        when (sp) {
            SpeakerState.Ready, is SpeakerState.Playing -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(actions.onPlay, Modifier.heightIn(min = 48.dp).testTag("listening_replay"), border = outlinedBorder(), contentPadding = PaddingValues(horizontal = Spacing.md)) {
                    Icon(painterResource(R.drawable.ic_ms_replay), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.listening_replay), Modifier.padding(start = Spacing.xs))
                }
                val slow = state.rate < 1f
                val rateText = stringResource(if (slow) R.string.listening_rate_slow else R.string.listening_rate_normal)
                // 접근 이름에 보이는 글자를 넣는다(SC 2.5.3)
                val rateName = stringResource(R.string.listening_rate_name, rateText)
                OutlinedButton(
                    actions.onToggleRate,
                    Modifier.heightIn(min = 48.dp).testTag("listening_rate").semantics { contentDescription = rateName },
                    border = outlinedBorder(), contentPadding = PaddingValues(horizontal = Spacing.md),
                ) { Text(rateText) }
            }
            SpeakerState.MissingJapaneseData -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Button(actions.onInstallVoice, Modifier.heightIn(min = 48.dp).testTag("listening_install")) { Text(stringResource(R.string.listening_install)) }
                    if (!state.scriptOpen) ScriptButton(actions)
                }
                if (state.externalOpenFailed) ExternalFailed(R.string.listening_install_failed)
            }
            SpeakerState.NoEngine -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (!state.scriptOpen) ScriptButton(actions)
                    Button(actions.onOpenVoiceSettings, Modifier.heightIn(min = 48.dp).testTag("listening_open_settings")) { Text(stringResource(R.string.listening_open_settings)) }
                }
                if (state.externalOpenFailed) ExternalFailed(R.string.listening_settings_failed)
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScriptButton(actions: ListeningActions) =
    OutlinedButton(actions.onOpenScript, Modifier.heightIn(min = 48.dp).testTag("listening_script_button"), border = outlinedBorder()) { Text(stringResource(R.string.listening_script_show)) }

/** 의미 있는 경계는 outline(3:1 이상)을 쓴다. 기본값 outlineVariant는 장식용이다(ui-design 색 표). */
@Composable
private fun outlinedBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

/** 56 dp 원형 주 버튼: 준비 중·재생 불가는 비활성. */
@Composable
private fun PrimaryButton(sp: SpeakerState, actions: ListeningActions) {
    val enabled = sp == SpeakerState.Ready || sp is SpeakerState.Playing || sp == SpeakerState.Error
    val (icon, label) = when (sp) {
        SpeakerState.Ready -> R.drawable.ic_ms_play_arrow to stringResource(R.string.listening_play)
        is SpeakerState.Playing -> R.drawable.ic_ms_stop to stringResource(R.string.listening_stop)
        SpeakerState.Error -> R.drawable.ic_ms_replay to stringResource(R.string.action_retry)
        SpeakerState.Initializing -> R.drawable.ic_ms_volume_up to stringResource(R.string.listening_preparing_a11y)
        else -> R.drawable.ic_ms_volume_up to stringResource(R.string.listening_unavailable_a11y)
    }
    Button(
        {
            when (sp) {
                SpeakerState.Ready -> actions.onPlay()
                is SpeakerState.Playing -> actions.onStop()
                SpeakerState.Error -> actions.onRetrySpeaker()
                else -> Unit
            }
        },
        Modifier.size(56.dp).testTag("listening_primary").semantics { contentDescription = label },
        enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(0.dp),
    ) {
        if (sp == SpeakerState.Initializing) {
            if (LocalFrozenProgress.current) CircularProgressIndicator(progress = { 0.75f }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        } else {
            Icon(painterResource(icon), null, Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ExternalFailed(message: Int) {
    Row(Modifier.fillMaxWidth().testTag("listening_external_failed").semantics { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_ms_info), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
    }
}

/** 대본 패널(ui-design 6.11): 머리 줄 전체가 버튼, 펼치면 화자 라벨 + 본문. ttsText는 보이지 않는다. */
@Composable
private fun ScriptPanel(lines: List<ListeningLineUi>, open: Boolean, showRuby: Boolean, onToggle: () -> Unit) {
    val jp = LocalJpTypography.current
    val stateText = stringResource(if (open) R.string.listening_script_state_open else R.string.listening_script_state_closed)
    Surface(Modifier.fillMaxWidth().testTag("listening_script"), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = Spacing.lg).testTag("listening_script_toggle")
                    .semantics { stateDescription = stateText },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.listening_script_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lines.forEachIndexed { i, l ->
                        val who = stringResource(speakerLabel(l.voice))
                        val spoken = remember(l, who) {
                            buildAnnotatedString {
                                withStyle(KoSpan) { append("$who, ") }
                                withStyle(JpSpan) { append(l.text.joinToString("") { it.base }) }
                            }
                        }
                        Column(Modifier.testTag("listening_script_line_$i").clearAndSetSemantics { text = spoken }) {
                            Text(who, style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                            RubyText(l.text, style = jp.bodyLarge, showRuby = showRuby)
                        }
                    }
                }
            }
        }
    }
}

private fun speakerLabel(v: Voice): Int = when (v) {
    Voice.MALE -> R.string.listening_speaker_male
    Voice.FEMALE -> R.string.listening_speaker_female
    Voice.NARRATOR -> R.string.listening_speaker_narrator
}

/** 화면 아래 음성 고지 한 줄(N-12 본문 첫 문장 + "자세히"). notices에 N-12가 없으면 호출하지 않는다. */
@Composable
private fun AudioNotice(body: String, onMore: () -> Unit) {
    val moreA11y = stringResource(R.string.practice_notice_more_a11y)
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Text(firstSentence(body), Modifier.weight(1f).testTag("listening_audio_notice"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onMore, Modifier.heightIn(min = 48.dp).semantics { contentDescription = moreA11y }) { Text(stringResource(R.string.practice_notice_more)) }
    }
}

/** 첫 문장: 마침표(문장 끝 + 공백) 또는 줄바꿈까지. 없으면 전체. */
internal fun firstSentence(body: String): String {
    val m = Regex("""[.。!?]\s|\n""").find(body.trim()) ?: return body.trim()
    return body.trim().substring(0, m.range.first + if (m.value[0] == '\n') 0 else 1)
}
