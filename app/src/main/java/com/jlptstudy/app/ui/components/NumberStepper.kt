package com.jlptstudy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.theme.Spacing

/**
 * 라벨 + [−] 값 [+] (ui-design 6.8, S01·S08 "하루 새 카드"). `Slider`를 쓰지 않는다.
 * 값을 바꾸는 알림은 버튼 stateDescription "{n}장"이 맡는다: 값 노드에 liveRegion을 걸면 두 번 읽힌다(11.1).
 * 글꼴이 커서 한 줄에 안 들어가면 [− 값 +]가 라벨 아래로 내려간다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberStepper(
    label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier,
    range: IntRange = 5..50, step: Int = 5,
) {
    val valueText = stringResource(R.string.stepper_value, value)
    FlowRow(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.xs).semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.stepper_decrease, label), valueText, value > range.first) {
                onValueChange((value - step).coerceAtLeast(range.first))
            }
            Text(
                valueText, Modifier.defaultMinSize(minWidth = 64.dp).padding(horizontal = Spacing.sm),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center,
            )
            StepButton("+", stringResource(R.string.stepper_increase, label), valueText, value < range.last) {
                onValueChange((value + step).coerceAtMost(range.last))
            }
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, state: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick, Modifier.defaultMinSize(48.dp, 48.dp).semantics { contentDescription = description; stateDescription = state },
        enabled = enabled, colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text(glyph, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge) }
}
