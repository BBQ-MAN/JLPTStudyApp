package com.jlptstudy.app.ui.settings

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.ui.components.NumberStepper
import com.jlptstudy.app.ui.level.LevelPicker
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActions(
    val onLevels: (Set<JlptLevel>) -> Unit = {}, val onNewCards: (Int) -> Unit = {}, val onDayHour: (Int) -> Unit = {},
    val onKind: (CardKind, Boolean) -> Unit = { _, _ -> }, val onFrontFurigana: (Boolean) -> Unit = {},
    val onBodyFurigana: (Boolean) -> Unit = {}, val onTheme: (ThemeMode) -> Unit = {},
    val onAbout: () -> Unit = {}, val onLevelNotice: () -> Unit = {}, val onRetry: () -> Unit = {}, val onSaveFailedShown: () -> Unit = {},
    val onListeningScriptFirst: (Boolean) -> Unit = {},
)

@Composable
fun SettingsRoute(vm: SettingsViewModel, openLevelPicker: Boolean, onAbout: () -> Unit, onLevelNotice: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    val failed = stringResource(R.string.settings_save_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            val r = host.showSnackbar(failed, retry, duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) vm.retrySave() else vm.dismissSaveFailed()
        }
    }
    SettingsScreen(
        state, host, openLevelPicker,
        SettingsActions(
            vm::setLevels, vm::setNewCards, vm::setDayBoundaryHour, vm::setKind, vm::setFrontFurigana, vm::setBodyFurigana,
            vm::setTheme, onAbout, onLevelNotice, onListeningScriptFirst = vm::setListeningScriptFirst,
        ),
    )
}

/** S08(ui-design 6.8). 행 최소 56 dp. openLevelPicker면 학습 급수 하단 시트를 연 채로 시작한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState, host: SnackbarHostState, openLevelPicker: Boolean = false, actions: SettingsActions = SettingsActions(),
) {
    var sheetOpen by rememberSaveable { mutableStateOf(openLevelPicker) }
    val levelRowFocus = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(sheetOpen) { // 시트를 닫으면 포커스는 "학습 급수" 행으로
        if (wasOpen && !sheetOpen) runCatching { levelRowFocus.requestFocus() }
        wasOpen = sheetOpen
    }
    var daySheetOpen by rememberSaveable { mutableStateOf(false) }
    val dayRowFocus = remember { FocusRequester() }
    var dayWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(daySheetOpen) { // 키보드·D-pad 입력 포커스(터치 모드에서는 clickable이 거절한다. 낭독 포커스는 시트가 닫히며 플랫폼이 돌려준다, 11.1). 고르거나 닫으면 포커스는 "하루 시작 시각" 행으로
        if (dayWasOpen && !daySheetOpen) runCatching { dayRowFocus.requestFocus() }
        dayWasOpen = daySheetOpen
    }
    Surface(Modifier.fillMaxSize().testTag("settings_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            if (state.loading) {
                SettingsPlaceholder()
            } else {
                Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)) {
                    Text(stringResource(R.string.tab_settings), Modifier.padding(vertical = Spacing.lg).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    Section(R.string.settings_group_study)
                    val levelsText = JlptLevel.entries.filter { it in state.targetLevels }.joinToString(" · ") { it.name }
                        .ifEmpty { stringResource(R.string.settings_levels_none) }
                    ClickRow(stringResource(R.string.settings_levels), levelsText, Modifier.focusRequester(levelRowFocus).testTag("row_levels")) { sheetOpen = true }
                    NumberStepper(stringResource(R.string.settings_new_cards), state.newCardsPerDay, actions.onNewCards)
                    val dayHour = stringResource(R.string.settings_day_start_hour, state.dayBoundaryHour)
                    ClickRow(
                        stringResource(R.string.settings_day_start), "%02d:00".format(state.dayBoundaryHour),
                        Modifier.focusRequester(dayRowFocus).testTag("row_day_start").semantics { stateDescription = dayHour },
                        onClickLabel = stringResource(R.string.action_change), tnum = true,
                    ) { daySheetOpen = true }
                    Text(stringResource(R.string.settings_kinds), Modifier.padding(top = Spacing.md), style = MaterialTheme.typography.titleSmall)
                    SwitchRow(stringResource(R.string.settings_kind_vr), state.vocabRecognitionEnabled) { actions.onKind(CardKind.VR, it) }
                    SwitchRow(stringResource(R.string.settings_kind_kr), state.kanjiCardsEnabled) { actions.onKind(CardKind.KR, it) }
                    SwitchRow(stringResource(R.string.settings_kind_vp), state.vocabRecallEnabled) { actions.onKind(CardKind.VP, it) }
                    if (state.grammarAvailable) SwitchRow(stringResource(R.string.settings_kind_gr), state.grammarCardsEnabled) { actions.onKind(CardKind.GR, it) }
                    if (state.kindsWarning) {
                        Text(stringResource(R.string.settings_kinds_warning), Modifier.testTag("kinds_warning").semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(stringResource(R.string.settings_kinds_note), Modifier.padding(vertical = Spacing.xs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Section(R.string.settings_group_display)
                    SwitchRow(stringResource(R.string.settings_front_furigana), state.frontFurigana, actions.onFrontFurigana)
                    SwitchRow(stringResource(R.string.settings_body_furigana), state.bodyFurigana, actions.onBodyFurigana)
                    ThemeRow(state.theme, actions.onTheme)
                    if (state.listeningAvailable) SwitchRow(stringResource(R.string.settings_listening_script_first), state.listeningScriptFirst, actions.onListeningScriptFirst)

                    Section(R.string.settings_group_info)
                    ClickRow(stringResource(R.string.about_title), null, Modifier.testTag("row_about")) { actions.onAbout() }
                    val version = appVersion()
                    InfoRow(stringResource(R.string.settings_version), stringResource(R.string.settings_version_value, version, state.contentVersion))
                    Box(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg))
                }
            }
            SnackbarHost(host, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (daySheetOpen && !state.loading) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { daySheetOpen = false }, sheetState = sheet) {
            // 고르면 값을 저장하고 시트를 내리는 애니메이션이 끝난 뒤에 닫는다(바로 지우면 시트가 툭 사라진다)
            DayStartSheetContent(state.dayBoundaryHour) { actions.onDayHour(it); scope.launch { sheet.hide() }.invokeOnCompletion { daySheetOpen = false } }
        }
    }
    if (sheetOpen && !state.loading) { // 로딩 전엔 선택값이 비어 있으므로 기다린다
        val available = state.levelOptions.filter { it.available }.map { it.level }.toSet()
        var picked by remember { mutableStateOf(state.targetLevels intersect available) }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheet) {
            Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg).testTag("level_picker_sheet"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.settings_levels), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                LevelPicker(
                    state.levelOptions, picked, { picked = if (it in picked) picked - it else picked + it },
                    onNoticeClick = { sheetOpen = false; actions.onLevelNotice() },
                )
                Text(stringResource(R.string.settings_levels_sheet_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (picked.isEmpty()) {
                    Text(stringResource(R.string.level_none_selected), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    { actions.onLevels(picked); scope.launch { sheet.hide() }.invokeOnCompletion { sheetOpen = false } }, Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    enabled = picked.isNotEmpty(),
                ) { Text(stringResource(R.string.level_save)) }
            }
        }
    }
}

/** 하루 시작 시각 시트 본문(ui-design 6.8): 정시 라디오 13개. 고르면 [onPick]. */
@Composable
internal fun DayStartSheetContent(hour: Int, onPick: (Int) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg).testTag("day_start_sheet")) {
        Text(stringResource(R.string.settings_day_start), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_day_start_note), Modifier.padding(vertical = Spacing.sm),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            (0..12).forEach { h ->
                val description = stringResource(R.string.settings_day_start_hour, h)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("day_start_$h")
                        .selectable(h == hour, role = Role.RadioButton) { onPick(h) }
                        .semantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    RadioButton(h == hour, onClick = null)
                    Text("%02d:00".format(h), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider(Modifier.padding(top = Spacing.md))
    Text(stringResource(title), Modifier.padding(top = Spacing.md, bottom = Spacing.xs).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ClickRow(label: String, value: String?, modifier: Modifier = Modifier, onClickLabel: String? = null, tnum: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, style = MaterialTheme.typography.let { if (tnum) it.bodyLarge.copy(fontFeatureSettings = "tnum") else it.bodyMedium }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 누르면 열린다는 표시. 행 전체가 하나의 버튼이라 따로 낭독하지 않는다
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, Modifier.padding(start = Spacing.xs), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.sm).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.Center) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun ThemeRow(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val items = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = theme == mode, onClick = { onTheme(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, items.size), modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

/** 공통 로딩 규칙(7절): 300 ms 안에 끝나면 아무것도 보이지 않고, 넘으면 행 6개 자리표시. */
@Composable
private fun SettingsPlaceholder() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LoadingDelayMs); show = true }
    if (!show) return
    val a11y = stringResource(R.string.review_loading_a11y)
    Column(Modifier.statusBarsPadding().padding(Spacing.lg).semantics(mergeDescendants = true) { contentDescription = a11y }, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        repeat(6) {
            Box(Modifier.fillMaxWidth().height(24.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}
