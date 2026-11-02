package com.jlptstudy.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.R
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.theme.Spacing

/** attribution.json의 meaningSourceTexts. 읽지 못하면 빈 맵(시트에서 생성 방법 문장만 빠진다). */
@Composable
fun rememberMeaningSourceTexts(repo: AttributionRepository?): Map<String, String> =
    produceState(emptyMap(), repo) {
        value = try { repo?.load()?.meaningSourceTexts.orEmpty() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyMap() }
    }.value

/** 칩을 눌렀을 때 쓸 콜백을 돌려주고, 눌린 뜻이 있으면 하단 시트(ui-design 5.7)를 그린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberProvenanceSheet(texts: Map<String, String>, onAbout: () -> Unit): (MeaningUi) -> Unit {
    var shown by remember { mutableStateOf<MeaningUi?>(null) }
    shown?.let { m ->
        ModalBottomSheet(onDismissRequest = { shown = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ProvenanceSheetContent(m, texts) { shown = null; onAbout() }
        }
    }
    return { shown = it }
}

@Composable
internal fun ProvenanceSheetContent(m: MeaningUi, texts: Map<String, String>, onAbout: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.sheet_provenance_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        ProvenanceLabel.sheetText(m, texts).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(ProvenanceLabel.reviewLine(m), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onAbout, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.sheet_provenance_about)) }
    }
}
