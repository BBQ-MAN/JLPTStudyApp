package com.jlptstudy.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.ui.theme.Spacing

class StatusAction(val label: String, val onClick: () -> Unit)

/** 상태 화면 틀(ui-design 5.10): 아이콘(또는 진행 표시), 제목, 문구, 주 버튼(채움) 1개와 보조 버튼(텍스트). */
/** 스크린샷 테스트가 true로 주면 로딩 표시를 고정 프레임(3/4 링)으로 그려 라이트·다크 비교가 가능하다. */
val LocalFrozenProgress = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun StatusScreen(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: Painter? = null,
    loading: Boolean = false,
    actions: List<StatusAction> = emptyList(),
) {
    Surface(modifier.fillMaxSize().testTag("status_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            StatusContent(title, body = body, icon = icon, loading = loading, actions = actions)
        }
    }
}

/** StatusScreen의 내용만(Surface·스크롤 없음). 홈처럼 이미 스크롤되는 화면 안에 상태 문구를 넣을 때 쓴다. */
@Composable
fun StatusContent(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: Painter? = null,
    loading: Boolean = false,
    actions: List<StatusAction> = emptyList(),
    titleAnnotated: androidx.compose.ui.text.AnnotatedString? = null,
    takeFocus: Boolean = true, // 입력란과 한 화면에 있는 상태(사전 검색 결과 없음)는 포커스를 뺏지 않는다
) {
            Column(
                modifier.widthIn(max = 600.dp).padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
            ) {
                if (loading) {
                  Box(Modifier.clearAndSetSemantics {}) { // 제목·본문이 로딩을 설명한다
                    if (LocalFrozenProgress.current) {
                        CircularProgressIndicator(progress = { 0.75f }, modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        CircularProgressIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
                    }
                  }
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 5.10: 열리면 제목에 포커스를 주고, 포커스가 못 가는 경우를 위해 liveRegion으로도 알린다.
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { if (takeFocus) runCatching { focus.requestFocus() } }
                Column(
                    Modifier.focusRequester(focus).focusable()
                        .semantics(mergeDescendants = true) { heading(); liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (titleAnnotated != null) Text(titleAnnotated, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    else Text(
                        title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    if (body != null) {
                        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (actions.isNotEmpty()) {
                    Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        actions.forEachIndexed { i, a ->
                            if (i == 0) {
                                Button(a.onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(a.label) }
                            } else {
                                TextButton(a.onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(a.label) }
                            }
                        }
                    }
                }
            }
}
