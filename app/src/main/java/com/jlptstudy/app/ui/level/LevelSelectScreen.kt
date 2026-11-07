package com.jlptstudy.app.ui.level

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusScreen
import com.jlptstudy.app.ui.components.NumberStepper
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun LevelSelectRoute(vm: LevelSelectViewModel, onStarted: () -> Unit, onOpenNotice: () -> Unit, onOpenAppInfo: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LevelSelectScreen(state, vm::toggle, vm::setNewCards, { vm.start(onStarted) }, onOpenNotice, onOpenAppInfo)
}

/** S01(ui-design 6.1). 하단 고정 "시작" 56 dp. */
@Composable
fun LevelSelectScreen(
    state: LevelUiState, onToggle: (JlptLevel) -> Unit, onSetNewCards: (Int) -> Unit, onStart: () -> Unit,
    onOpenNotice: () -> Unit, onOpenAppInfo: () -> Unit,
) {
    if (state.status == LevelStatus.NO_CONTENT) {
        StatusScreen(
            title = stringResource(R.string.level_no_content_title), body = stringResource(R.string.level_no_content_body),
            icon = painterResource(R.drawable.ic_ms_error),
            actions = listOf(StatusAction(stringResource(R.string.action_app_info), onOpenAppInfo)),
        )
        return
    }
    val preparing = state.status == LevelStatus.PREPARING
    var bars by remember { mutableStateOf(false) }
    LaunchedEffect(preparing) { if (preparing) { delay(LoadingDelayMs); bars = true } }
    val options = state.options.ifEmpty { JlptLevel.entries.map { LevelOption(it, 0, 0, true) } }
    Surface(Modifier.fillMaxSize().testTag("level_select"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(R.string.level_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.level_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LevelPicker(options, state.selected, onToggle, onOpenNotice, counting = preparing, showBars = bars)
                NumberStepper(stringResource(R.string.settings_new_cards), state.newCardsPerDay, onSetNewCards)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (state.selected.isEmpty() && !preparing) {
                    Text(stringResource(R.string.level_none_selected), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (state.saveFailed) {
                    Text(stringResource(R.string.level_start_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(onStart, Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 56.dp),
                    enabled = !preparing && state.selected.isNotEmpty()) { Text(stringResource(R.string.level_start)) }
            }
        }
    }
}
