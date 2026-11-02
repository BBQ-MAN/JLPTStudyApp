package com.jlptstudy.app.ui.home

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeActions(
    val onStart: () -> Unit = {}, val onPickLevels: () -> Unit = {}, val onChangeLimit: () -> Unit = {},
    val onMoreNew: () -> Unit = {}, val onDismissNotice: () -> Unit = {}, val onNoticeMore: () -> Unit = {},
    val onAbout: () -> Unit = {}, val onDictionary: () -> Unit = {},
)

@Composable
fun HomeRoute(vm: HomeViewModel, actions: HomeActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() } // 세션에서 돌아오면 수치를 다시 센다
    HomeScreen(state, HomeActions(actions.onStart, actions.onPickLevels, actions.onChangeLimit, vm::moreNewCards, vm::dismissFirstNotice, actions.onNoticeMore, actions.onAbout, actions.onDictionary))
}

/** S02(ui-design 6.2, 7절). 샘플 띠·첫 안내 카드는 어떤 상태와도 함께 나온다. */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions = HomeActions()) {
    Surface(Modifier.fillMaxSize().testTag("home_screen"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            HomeHeader(actions.onAbout)
            if (state.status == HomeStatus.LOADING) {
                LoadingBars()
            } else {
                Column(
                    Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state.isSampleContent) SampleBanner()
                    val notice = state.firstNotice
                    if (state.showFirstNotice && notice != null) {
                        FirstNoticeCard(notice.title, notice.body, actions.onNoticeMore, actions.onDismissNotice)
                    }
                    Body(state, actions)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onAbout: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.home_title), Modifier.weight(1f).padding(vertical = Spacing.md).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Box {
            IconButton({ menu = true }, Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_ms_more_vert), contentDescription = stringResource(R.string.home_menu_more))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.home_menu_about)) }, onClick = { menu = false; onAbout() })
            }
        }
    }
}

@Composable
private fun SampleBanner() {
    Row(
        Modifier.fillMaxWidth().testTag("sample_banner").clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(painterResource(R.drawable.ic_ms_info), contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.home_sample_banner), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FirstNoticeCard(title: String, body: String, onMore: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("first_notice").clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onMore, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_notice_more)) }
            TextButton(onClose, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_notice_close)) }
        }
    }
}

@Composable
private fun Body(state: HomeUiState, a: HomeActions) {
    val nextDue = state.nextDueAtEpochMs?.let {
        val at = DateTimeFormatter.ofPattern("M월 d일 HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        stringResource(R.string.review_empty_next_due, at, state.nextDueCount)
    } ?: stringResource(R.string.home_no_next_due)
    when (state.status) {
        HomeStatus.READY -> Summary(state, a)
        HomeStatus.NO_LEVEL_SELECTED -> StatusContent(
            stringResource(R.string.home_no_level_title), body = stringResource(R.string.home_no_level_body),
            icon = painterResource(R.drawable.ic_ms_info), actions = listOf(StatusAction(stringResource(R.string.home_no_level_action), a.onPickLevels)),
        )
        HomeStatus.NO_CONTENT_FOR_LEVELS -> StatusContent(
            stringResource(R.string.home_no_content_title), body = stringResource(R.string.home_no_content_body),
            icon = painterResource(R.drawable.ic_ms_info), actions = listOf(StatusAction(stringResource(R.string.home_no_content_action), a.onPickLevels)),
        )
        HomeStatus.ALL_DONE -> StatusContent(
            stringResource(R.string.review_empty_all_done), body = nextDue, icon = painterResource(R.drawable.ic_ms_check_circle),
            actions = listOf(StatusAction(stringResource(R.string.home_all_done_add), a.onPickLevels), StatusAction(stringResource(R.string.home_dictionary), a.onDictionary)),
        )
        HomeStatus.NEW_LIMIT_REACHED -> StatusContent(
            stringResource(R.string.review_empty_new_limit, state.newLimit), body = nextDue, icon = painterResource(R.drawable.ic_ms_check_circle),
            actions = listOf(StatusAction(stringResource(R.string.home_more_new), a.onMoreNew), StatusAction(stringResource(R.string.home_change_limit), a.onChangeLimit)),
        )
        HomeStatus.LOADING -> Unit
    }
    if (state.levelProgress.isNotEmpty() && state.status in setOf(HomeStatus.READY, HomeStatus.ALL_DONE, HomeStatus.NEW_LIMIT_REACHED)) {
        Progress(state.levelProgress)
    }
}

@Composable
private fun Summary(state: HomeUiState, a: HomeActions) {
    val levels = JlptLevel.entries.filter { it in state.targetLevels }.joinToString("·") { it.name }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.home_summary, state.dueCount, state.newTodayCount), Modifier.testTag("home_summary").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_levels, levels), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(a.onPickLevels, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_levels_change)) }
        }
        Button(a.onStart, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.home_start)) }
    }
}

@Composable
private fun Progress(items: List<LevelProgress>) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        for (p in items) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.home_progress_line, p.level.name, p.learnedWords, p.totalWords),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                LinearProgressIndicator(
                    progress = { if (p.totalWords == 0) 0f else (p.learnedWords.toFloat() / p.totalWords).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
            }
        }
    }
}

/** 공통 로딩 규칙(7절): 300 ms 안에 끝나면 아무것도 보이지 않고, 넘으면 요약 카드 자리표시 막대. */
@Composable
private fun LoadingBars() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LoadingDelayMs); show = true }
    if (!show) return
    val a11y = stringResource(R.string.review_loading_a11y)
    Column(
        Modifier.padding(horizontal = Spacing.lg).semantics(mergeDescendants = true) { contentDescription = a11y }.testTag("home_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        for (h in listOf(120, 24, 56)) {
            Box(Modifier.fillMaxWidth().height(h.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}
