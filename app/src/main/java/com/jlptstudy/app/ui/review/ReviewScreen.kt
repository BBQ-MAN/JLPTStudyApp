package com.jlptstudy.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.common.LocalFrozenProgress
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusScreen
import com.jlptstudy.app.ui.home.HomeStatus
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing
import com.jlptstudy.srs.Rating
import kotlinx.coroutines.delay

/** 상태만 받아 그리는 S03·S04. 저장 실패 스낵바는 snackbar 호스트로 호출 쪽이 띄운다. */
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    prefs: ReviewPrefs,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onToggleFurigana: () -> Unit,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onStudyMore: () -> Unit,
    onChipClick: ((MeaningUi) -> Unit)? = null,
    onGrammarDetail: ((String) -> Unit)? = null,
) {
    when {
        state.finished -> DoneScreen(state, onClose, onStudyMore)
        state.status == ReviewStatus.EMPTY -> EmptyScreen(state, onClose)
        else -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val low = maxHeight < 480.dp
                var bottomPx by remember { mutableStateOf(0) }
                var snackPx by remember { mutableStateOf(0) }
                val twoPane = low && maxWidth >= 600.dp
                val card = state.card
                if (card == null) {
                    LoadingBody(onClose)
                } else {
                    val topBar: @Composable () -> Unit = { TopBar(state, prefs, low, onClose, onUndo, onToggleFurigana) }
                    val progress: @Composable () -> Unit = { SessionProgress(state) }
                    val cardView: @Composable (Modifier) -> Unit = { m ->
                        Flashcard(
                            card, state.revealed, state.remainingReview + state.remainingNew, prefs, onReveal, m,
                            onChipClick, onGrammarDetail, header = if (low) progress else null,
                            bottomInset = if (twoPane) 0.dp else with(LocalDensity.current) { snackPx.toDp() }, // 스낵바 실측 높이
                        )
                    }
                    if (twoPane) {
                        Row(Modifier.fillMaxSize().padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                topBar()
                                cardView(Modifier.weight(1f).fillMaxWidth())
                            }
                            Column(
                                Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).semantics { isTraversalGroup = true; traversalIndex = 1f },
                                verticalArrangement = Arrangement.Center,
                            ) { BottomArea(state, onReveal, onRate, forceGrid = true) }
                        }
                    } else {
                        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter)) {
                            topBar()
                            if (!low) progress()
                            cardView(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.lg))
                            Box(Modifier.onSizeChanged { bottomPx = it.height }.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg).semantics { isTraversalGroup = true; traversalIndex = 1f }) {
                                BottomArea(state, onReveal, onRate, forceGrid = false)
                            }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).onSizeChanged { snackPx = it.height }.padding(bottom = if (twoPane) 0.dp else with(LocalDensity.current) { bottomPx.toDp() }))
            }
        }
    }
}

@Composable
private fun LoadingBody(onClose: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LoadingDelayMs); show = true }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClose)
        }
        val a11y = stringResource(R.string.review_loading_a11y)
        Box(Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = a11y }, contentAlignment = Alignment.Center) {
            if (show) CircularProgressIndicator(Modifier.testTag("review_loading"))
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconButton(onClose) { Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.review_close)) }
}

@Composable
private fun TopBar(
    state: ReviewUiState, prefs: ReviewPrefs, compact: Boolean,
    onClose: () -> Unit, onUndo: () -> Unit, onToggleFurigana: () -> Unit,
) {
    val on = stringResource(R.string.review_on)
    val off = stringResource(R.string.review_off)
    val furiganaLabel = stringResource(R.string.review_furigana)
    val furiganaOn = if (state.revealed || state.card is ReviewCardUi.Grammar) prefs.bodyFurigana else prefs.frontFurigana
    val undoLabel = stringResource(if (state.canUndo) R.string.review_undo else R.string.review_undo_none)
    Row(
        Modifier.fillMaxWidth().height(if (compact) 48.dp else 64.dp).padding(horizontal = Spacing.xs)
            .semantics { isTraversalGroup = true; traversalIndex = 2f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloseButton(onClose)
        Box(Modifier.weight(1f))
        IconButton(
            onToggleFurigana,
            Modifier.clearAndSetSemantics {
                contentDescription = furiganaLabel
                stateDescription = if (furiganaOn) on else off
                role = Role.Switch
                onClick { onToggleFurigana(); true }
            },
        ) {
            // 켜짐은 채운 배경으로 모양도 달라진다(색만으로 구분하지 않는다). 아이콘처럼 글꼴 배율을 따르지 않는 고정 크기.
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (furiganaOn) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .border(1.dp, if (furiganaOn) Color.Transparent else MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    JpText("あ", style = LocalJpTypography.current.bodyLarge, color = if (furiganaOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onUndo, enabled = state.canUndo) {
            Icon(painterResource(R.drawable.ic_ms_undo), undoLabel)
        }
    }
}

/** 진행 표시(5.6). 남은 수 = 복습 + 새 카드. 진행 막대는 평가한 수 / (평가한 수 + 남은 수). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionProgress(state: ReviewUiState) {
    val remaining = state.remainingReview + state.remainingNew
    val done = state.reviewedInSession + state.newInSession
    val a11y = stringResource(R.string.review_progress_a11y, remaining, state.remainingReview, state.remainingNew)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = a11y }.testTag("review_progress"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.review_remaining, remaining), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            Text(
                stringResource(R.string.review_progress_detail, state.remainingReview, state.remainingNew),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { if (done + remaining == 0) 0f else done.toFloat() / (done + remaining) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    }
}

/**
 * 하단 고정 영역: 앞면이면 "답 보기", 뒷면이면 평가 줄(5.3). 앞면에도 평가 줄을 투명·비활성으로 깔아 두 면의 높이를 같게 한다
 * (카드 높이가 달라지면 40% 지점 기준의 표제어 위치가 움직인다).
 */
@Composable
private fun BottomArea(state: ReviewUiState, onReveal: () -> Unit, onRate: (Rating) -> Unit, forceGrid: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        RatingBar(state, onRate, forceGrid, active = state.revealed)
        if (!state.revealed) {
            val label = stringResource(R.string.review_show_answer_label)
            Button(
                onReveal,
                Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { onClick(label) { onReveal(); true } },
            ) { Text(stringResource(R.string.review_show_answer)) }
        }
    }
}

private val RatingOrder = listOf(Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY)

/** 평가 버튼 줄(5.3). 한 버튼이라도 글자가 한 줄에 안 들어가면(측정으로 판단) 2×2 격자로 바꾼다. */
@Composable
fun RatingBar(state: ReviewUiState, onRate: (Rating) -> Unit, forceGrid: Boolean = false, active: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelLarge
    val previewStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val density = LocalDensity.current
    val items = RatingOrder.map { r ->
        val interval = state.previews[r]?.let(IntervalFormat::format).orEmpty()
        Triple(r, interval, RatingLabels.onClickLabel(r, interval))
    }
    val enabled = !state.saving && active
    Column(
        Modifier.fillMaxWidth().then(if (active) Modifier.semantics { isTraversalGroup = true } else Modifier.alpha(0f).clearAndSetSemantics {}),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.saving) {
                if (LocalFrozenProgress.current) LinearProgressIndicator({ 0.5f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } // 자리를 늘 남긴다(저장 중에 카드가 움직이지 않게)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - Spacing.sm * 3) / 4
            val fits = items.all { (r, interval, _) ->
                val w = maxOf(
                    measurer.measure(RatingLabels.name(r), nameStyle).size.width,
                    measurer.measure(interval, previewStyle).size.width,
                )
                with(density) { w.toDp() } + Spacing.sm * 2 <= cell
            }
            val button: @Composable (Modifier, Triple<Rating, String, String>) -> Unit = { m, item ->
                RatingButton(item.first, item.second, item.third, enabled, m) { onRate(item.first) }
            }
            if (fits && !forceGrid) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items.forEach { button(Modifier.weight(1f).fillMaxHeight(), it) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            pair.forEach { button(Modifier.weight(1f).fillMaxHeight(), it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(rating: Rating, interval: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (rating) {
        Rating.AGAIN -> cs.errorContainer to cs.onErrorContainer
        Rating.HARD -> cs.secondaryContainer to cs.onSecondaryContainer
        Rating.GOOD -> cs.primary to cs.onPrimary
        Rating.EASY -> cs.tertiaryContainer to cs.onTertiaryContainer
    }
    Column(
        modifier.heightIn(min = 64.dp).alpha(if (enabled) 1f else 0.5f)
            .clip(MaterialTheme.shapes.medium).background(bg)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm).testTag("rate_${rating.name}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(RatingLabels.name(rating), style = MaterialTheme.typography.labelLarge, color = fg)
        Text(interval, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = fg)
    }
}

@Composable
private fun EmptyScreen(state: ReviewUiState, onClose: () -> Unit) {
    val next = state.nextDueAtEpochMs?.let {
        val at = java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm").format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
        stringResource(R.string.review_empty_next_due, at, state.nextDueCount)
    }
    StatusScreen(
        title = if (state.emptyReason == HomeStatus.NEW_LIMIT_REACHED) stringResource(R.string.review_empty_new_limit, state.newLimit)
        else stringResource(R.string.review_empty_all_done),
        body = next,
        icon = painterResource(R.drawable.ic_ms_check_circle),
        actions = listOf(StatusAction(stringResource(R.string.review_empty_home), onClose)),
    )
}

/** S04 세션 완료(6.4). */
@Composable
fun DoneScreen(state: ReviewUiState, onHome: () -> Unit, onStudyMore: () -> Unit) {
    val left = state.newCardsLeftAfterSession
    val body = buildString {
        append(stringResource(R.string.done_body, state.reviewedInSession, state.newInSession))
        if (left > 0) append("\n").append(stringResource(R.string.done_more_available, left))
    }
    StatusScreen(
        title = stringResource(R.string.done_title), body = body, icon = painterResource(R.drawable.ic_ms_check_circle),
        actions = buildList {
            add(StatusAction(stringResource(R.string.review_empty_home), onHome))
            if (left > 0) add(StatusAction(stringResource(R.string.done_more_action), onStudyMore))
        },
    )
}
