package com.jlptstudy.app.ui.level

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.theme.Spacing

/**
 * 급수 선택(ui-design 5.9). 행 전체가 하나의 toggleable. 콘텐츠가 없는 급수는 비활성 + "콘텐츠 준비 중".
 * counting = true면 개수 자리에 자리표시 막대(levelCounts 계산 중, showBars는 300 ms 뒤부터).
 */
@Composable
fun LevelPicker(
    options: List<LevelOption>, selected: Set<JlptLevel>, onToggle: (JlptLevel) -> Unit,
    onNoticeClick: () -> Unit, modifier: Modifier = Modifier, counting: Boolean = false, showBars: Boolean = true,
) {
    Column(modifier) {
        for (o in options) LevelRow(o, o.level in selected, counting, showBars, onToggle)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.level_notice), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onNoticeClick) { Text(stringResource(R.string.level_notice_more)) }
        }
    }
}

@Composable
private fun LevelRow(o: LevelOption, checked: Boolean, counting: Boolean, showBars: Boolean, onToggle: (JlptLevel) -> Unit) {
    val on = stringResource(R.string.level_state_on)
    val off = stringResource(R.string.level_state_off)
    val soon = stringResource(R.string.level_state_unavailable)
    val enabled = o.available
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("level_row_${o.level.name}")
            .toggleable(value = checked && enabled, enabled = enabled, role = Role.Checkbox) { onToggle(o.level) }
            .semantics { stateDescription = if (!enabled) soon else if (checked) on else off }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Checkbox(checked = checked && enabled, onCheckedChange = null, enabled = enabled)
        Text(o.level.name, style = MaterialTheme.typography.titleMedium, color = rowColor(enabled), modifier = Modifier.width(44.dp))
        when {
            !enabled -> Text(stringResource(R.string.level_unavailable), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            counting -> if (showBars) Box(Modifier.size(width = 120.dp, height = 12.dp).clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest).testTag("level_count_placeholder")) else Unit
            else -> Text(stringResource(R.string.level_counts, o.wordCount, o.kanjiCount),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun rowColor(enabled: Boolean) =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
