package com.jlptstudy.app.ui.common

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.theme.Spacing

/** 라벨 + [−] 값 [+] 한 줄(5~50 새 카드, 시작 시각). 값은 tnum. 행 최소 56 dp, 버튼 48 dp. */
@Composable
fun StepperRow(
    label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit,
    canDecrease: Boolean, canIncrease: Boolean, modifier: Modifier = Modifier,
) {
    val dec = stringResource(R.string.stepper_decrease, label)
    val inc = stringResource(R.string.stepper_increase, label)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onDecrease, Modifier.defaultMinSize(48.dp, 48.dp).semantics { contentDescription = dec }, enabled = canDecrease) {
            Text("−", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value, Modifier.defaultMinSize(minWidth = 56.dp).semantics { contentDescription = "$label $value"; liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onIncrease, Modifier.defaultMinSize(48.dp, 48.dp).semantics { contentDescription = inc }, enabled = canIncrease) {
            Text("+", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
        }
    }
}
