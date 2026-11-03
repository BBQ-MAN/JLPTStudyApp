package com.jlptstudy.app.ui.detail

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/** ui-design 6.7.1. 화면 안 보기 상태이며 UiState에 두지 않는다. */
enum class StrokePlayback { IDLE, PLAYING, DONE }

object StrokeGeometry {
    private val num = "(?>[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)"
    private val first = Regex("^\\s*[Mm]\\s*($num)[\\s,]*($num)")

    /** `d`의 첫 M/m 좌표. 첫 명령이 M/m이 아니거나 좌표가 없으면 null. 첫 m은 원점 기준이라 M과 같다. */
    fun start(d: String): Pair<Float, Float>? {
        val m = first.find(d) ?: return null
        val x = m.groupValues[1].toFloatOrNull() ?: return null
        val y = m.groupValues[2].toFloatOrNull() ?: return null
        return x to y
    }
}

private const val VIEW_BOX = 109f
private const val STROKE_WIDTH = 3f // 원본 좌표
private const val STROKE_MS = 400
private const val GAP_MS = 150L

/** 시스템 "애니메이션 삭제"(animator duration scale 0). 설정에서 돌아올 때 바뀔 수 있어 ON_RESUME마다 다시 읽는다. */
@Composable
private fun animationsDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val read = { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var disabled by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { disabled = read() }
    return disabled
}

/** 필순 영역. strokes의 모든 경로는 저장소가 미리 해석해 본 것이다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrokeOrder(strokes: List<String>, sourceName: String?, onSource: () -> Unit) {
    val reduced = animationsDisabled()
    var playback by rememberSaveable { mutableStateOf(StrokePlayback.IDLE) }
    var numbers by rememberSaveable { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val playing = playback == StrokePlayback.PLAYING && !reduced

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (playback == StrokePlayback.PLAYING) playback = StrokePlayback.IDLE }
    LaunchedEffect(playback, reduced) {
        if (playback != StrokePlayback.PLAYING || reduced) return@LaunchedEffect
        for (i in strokes.indices) {
            step = i
            animate(0f, 1f, animationSpec = tween(STROKE_MS, easing = LinearEasing)) { v, _ -> progress = v }
            if (i < strokes.lastIndex) delay(GAP_MS)
        }
        playback = StrokePlayback.DONE
    }

    val paths = remember(strokes) { strokes.map { PathParser.createPathFromPathData(it).asComposePath() } }
    val starts = remember(strokes) { strokes.map(StrokeGeometry::start) }
    val color = MaterialTheme.colorScheme.onSurface
    val pad = 16.dp
    val description = stringResource(R.string.stroke_figure, strokes.size)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(
            Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .clearAndSetSemantics { contentDescription = description; testTag = "stroke_figure" },
        ) {
            val measure = remember { PathMeasure() }
            Canvas(Modifier.matchParentSize()) {
                val p = pad.toPx()
                val sc = (size.width - 2 * p) / VIEW_BOX
                withTransform({ translate(p, p); scale(sc, sc, Offset.Zero) }) {
                    val style = Stroke(STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    paths.forEachIndexed { i, path ->
                        val frac = if (!playing || i < step) 1f else if (i == step) progress else 0f
                        if (frac >= 1f) drawPath(path, color, style = style)
                        else if (frac > 0f) {
                            measure.setPath(path, false)
                            val seg = Path()
                            measure.getSegment(0f, measure.length * frac, seg, true)
                            drawPath(seg, color, style = style)
                        }
                    }
                }
            }
            val shown = if (reduced || numbers) starts.indices.filter { starts[it] != null && (!playing || it <= step) } else emptyList()
            val numStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            val numColor = MaterialTheme.colorScheme.onSurfaceVariant
            Layout(
                content = { shown.forEach { i -> Text("${i + 1}", Modifier.testTag("stroke_number_${i + 1}"), style = numStyle, color = numColor) } },
                modifier = Modifier.matchParentSize(),
            ) { measurables, c ->
                val p = pad.toPx(); val gap = 2.dp.toPx(); val sc = (c.maxWidth - 2 * p) / VIEW_BOX
                layout(c.maxWidth, c.maxHeight) {
                    measurables.forEachIndexed { k, m ->
                        val pl = m.measure(Constraints())
                        val (sx, sy) = starts[shown[k]]!!
                        // 그림 안쪽 경계에 붙인다(자르지 않는다). 영역이 번호보다 작으면 왼쪽 위 기준.
                        val x = (p + sx * sc - pl.width - gap).coerceIn(p, maxOf(p, c.maxWidth - p - pl.width))
                        val y = (p + sy * sc - pl.height - gap).coerceIn(p, maxOf(p, c.maxHeight - p - pl.height))
                        pl.place(IntOffset(x.toInt(), y.toInt()))
                    }
                }
            }
        }
        if (!reduced) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        {
                            if (playback == StrokePlayback.PLAYING) playback = StrokePlayback.IDLE
                            else { step = 0; progress = 0f; playback = StrokePlayback.PLAYING }
                        },
                        Modifier.heightIn(min = 48.dp),
                    ) {
                        val (icon, label) = when (playback) {
                            StrokePlayback.IDLE -> R.drawable.ic_ms_play_arrow to R.string.stroke_play
                            StrokePlayback.PLAYING -> R.drawable.ic_ms_stop to R.string.stroke_stop
                            StrokePlayback.DONE -> R.drawable.ic_ms_replay to R.string.stroke_replay
                        }
                        Icon(painterResource(icon), contentDescription = null)
                        Text(stringResource(label), Modifier.padding(start = Spacing.sm))
                    }
                }
                val state = stringResource(if (numbers) R.string.stroke_on else R.string.stroke_off)
                Row(
                    Modifier.heightIn(min = 48.dp).toggleable(numbers, role = Role.Switch) { numbers = it }.semantics { stateDescription = state },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(stringResource(R.string.stroke_numbers), style = MaterialTheme.typography.bodyMedium)
                    Switch(numbers, null)
                }
            }
        }
        if (!reduced) {
            // 상태 문구는 버튼 줄 밖 고정 높이 자리에 둔다. 나타나도 스위치와 출처 줄이 밀리지 않는다.
            Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                if (playback == StrokePlayback.DONE) {
                    Text(stringResource(R.string.stroke_done), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (sourceName != null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSource), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.stroke_source, sourceName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
