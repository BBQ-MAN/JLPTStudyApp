package com.jlptstudy.app.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.jlptstudy.app.ui.theme.LocalJpTypography

/** 훈독 원문 'た.べる' -> 'た(べる)', 접두·접미 '-' -> '～'. DB 값은 그대로 둔다(architecture 4.2). */
fun kunDisplay(raw: String): String {
    val s = raw.replace('-', '～')
    val i = s.indexOf('.')
    if (i < 0) return s
    return s.substring(0, i) + "(" + s.substring(i + 1).replace(".", "") + ")"
}

/** 라벨 열 너비: 글꼴 배율을 따라 커져서 값 열이 행마다 같은 x에서 시작한다. */
@Composable
fun infoLabelWidth(): Dp = with(LocalDensity.current) { 88.sp.toDp() }

/** 읽기 하나는 줄 가운데서 끊기지 않는다. 줄바꿈은 읽기 사이에서만. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KunText(kun: List<String>) {
    FlowRow {
        kun.forEachIndexed { i, k ->
            JpText(kunDisplay(k) + if (i < kun.lastIndex) "、" else "", style = LocalJpTypography.current.body)
        }
    }
}
