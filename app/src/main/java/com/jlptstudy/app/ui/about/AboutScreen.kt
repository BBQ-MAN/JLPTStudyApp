package com.jlptstudy.app.ui.about

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.R
import com.jlptstudy.app.data.attribution.LicenseUi
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.attribution.SourceUi
import com.jlptstudy.app.data.content.ReviewCounts
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.common.startExternal
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AboutRoute(vm: AboutViewModel, anchor: String?, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    AboutScreen(state, anchor, onBack, vm::retry)
}

/** S09(ui-design 9절). 문구는 모두 상태(attribution.json)에서 온 값이다. */
@Composable
fun AboutScreen(state: AboutUiState, anchor: String? = null, onBack: () -> Unit = {}, onRetry: () -> Unit = {}) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val noApp = stringResource(R.string.about_link_unavailable)
    val copy = stringResource(R.string.about_copy_url)
    // ACTION_VIEW는 미리 판정하지 않고 시작한 뒤 예외로 안다(architecture.md 5.1).
    val onOpen: (String) -> Unit = { url ->
        val uri = Uri.parse(url)
        if (uri.scheme == "https" || uri.scheme == "http") {
            if (!context.startExternal(Intent(Intent.ACTION_VIEW, uri))) {
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar(noApp, copy, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, url)))
                    }
                }
            }
        }
    }
    Surface(Modifier.fillMaxSize().testTag("about_screen"), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack, Modifier.minimumInteractiveComponentSize()) {
                        Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.detail_back))
                    }
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                when (state.status) {
                    AboutStatus.LOADING -> LoadingCards()
                    AboutStatus.FAILED -> Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                        StatusContent(
                            stringResource(R.string.about_failed_title), body = stringResource(R.string.about_failed_body),
                            actions = listOf(StatusAction(stringResource(R.string.action_retry), onRetry)),
                        )
                        // 9.4 최소 표시: 파이프라인이 만든 문단 그대로
                        Text(
                            stringResource(R.string.attribution_fallback), Modifier.padding(horizontal = Spacing.lg).testTag("about_fallback"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    AboutStatus.LOADED -> Loaded(state, anchor, onOpen)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun LoadingCards() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LoadingDelayMs); show = true } // 300 ms 안에 끝나면 아무것도 보이지 않는다
    if (show) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(3) {
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium))
            }
        }
    }
}

private class Row_(val key: String, val content: @Composable () -> Unit)

@Composable
private fun Loaded(state: AboutUiState, anchor: String?, onOpen: (String) -> Unit) {
    val rows = buildList {
        fun header(key: String, res: Int) = add(Row_("h:$key") {
            Text(stringResource(res), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        })
        if (state.notices.isNotEmpty()) header("notices", R.string.about_section_notices)
        state.notices.forEach { n ->
            add(Row_(n.id) { NoticeCard(n, n.id == anchor, state.reviewCounts.takeIf { n.id == REVIEW_NOTICE_ID }) })
        }
        state.dbLicense?.let { db ->
            header("db", R.string.about_section_db)
            add(Row_("db") {
                Card {
                    Text(stringResource(R.string.about_db_title, db.label), style = MaterialTheme.typography.titleMedium)
                    UrlText(db.url, onOpen = onOpen)
                    Text(stringResource(R.string.about_db_note), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_content_version, state.contentVersion), style = MaterialTheme.typography.bodyMedium)
                }
            })
        }
        if (state.sources.isNotEmpty()) header("sources", R.string.about_section_sources)
        state.sources.forEach { s -> add(Row_(s.id) { SourceCard(s, s.id == anchor, onOpen) }) }
        if (state.openSourceLicenses.isNotEmpty()) {
            header("other", R.string.about_section_other)
            state.openSourceLicenses.forEach { l -> add(Row_("lib:${l.name}") { LibraryCard(l, onOpen) }) }
            state.openSourceLicenses.filter { it.fullText != null }.distinctBy { it.fullTextAsset }.forEach { l ->
                add(Row_("text:${l.fullTextAsset}") {
                    Card {
                        Text(l.license, style = MaterialTheme.typography.titleMedium)
                        Text(l.fullText.orEmpty(), style = MaterialTheme.typography.bodySmall.copy(localeList = LocaleList("en")))
                    }
                })
            }
        }
    }
    // 처음부터 anchor 카드가 맨 위에 오도록 시작 위치를 정한다(움직임 없음). 없는 anchor·null이면 맨 위.
    val start = rows.indexOfFirst { it.key == anchor }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LazyColumn(
        Modifier.fillMaxSize().testTag("about_list"), state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(rows, key = { it.key }) { it.content() }
    }
}

/** N-10 카드에만 집계 줄이 붙는다. 번들 N-10 id는 파이프라인 고정 값(architecture.md 6.6). */
private const val REVIEW_NOTICE_ID = "N-10"

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}

/** 카드 제목. 이동해 온 카드면 접근성 포커스를 준다. */
@Composable
private fun CardTitle(text: String, focused: Boolean) {
    val focus = remember { FocusRequester() }
    if (focused) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.then(if (focused) Modifier.focusRequester(focus).focusable() else Modifier).semantics { heading() },
    )
}

@Composable
private fun NoticeCard(n: NoticeUi, focused: Boolean, counts: ReviewCounts?) {
    Card {
        CardTitle(n.title, focused)
        Text(n.body, style = MaterialTheme.typography.bodyMedium)
        counts?.let {
            Text(
                stringResource(R.string.about_review_counts, it.total, it.human, it.agent, it.sampled, it.unreviewed, it.reviewerUnrecorded),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.testTag("review_counts"),
            )
        }
    }
}

@Composable
private fun Labeled(label: Int, content: @Composable () -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun SourceCard(s: SourceUi, focused: Boolean, onOpen: (String) -> Unit) {
    Card {
        CardTitle(s.name, focused)
        s.version?.let { Text(stringResource(R.string.about_source_version, it), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")) }
        Text(s.attribution, style = MaterialTheme.typography.bodyMedium.copy(localeList = localeOf(s.attribution)))
        Labeled(R.string.about_label_license) {
            Text(s.license.label, style = MaterialTheme.typography.bodyMedium)
            UrlText(s.license.url, onOpen = onOpen)
        }
        Labeled(R.string.about_label_modifications) { Text(s.modifications, style = MaterialTheme.typography.bodyMedium) }
        s.homepage?.let { h -> Labeled(R.string.about_label_homepage) { UrlText(h, onOpen = onOpen) } }
        if (s.links.isNotEmpty()) Labeled(R.string.about_label_links) { s.links.forEach { UrlText(it, onOpen = onOpen) } }
        s.extraCredits.forEach { c ->
            Text(c.text, style = MaterialTheme.typography.bodyMedium.copy(localeList = localeOf(c.text)))
            c.url?.let { UrlText(it, onOpen = onOpen) }
        }
    }
}

@Composable
private fun LibraryCard(l: LicenseUi, onOpen: (String) -> Unit) {
    Card {
        Text(l.name, style = MaterialTheme.typography.titleSmall)
        Text(l.license, style = MaterialTheme.typography.bodyMedium)
        l.url?.let { UrlText(it, onOpen = onOpen) }
    }
}

/** 한글이 있으면 ko-KR, 없으면 en(ui-design 9.2). */
private fun localeOf(text: String): LocaleList =
    LocaleList(if (text.any { it in '가'..'힣' || it in 'ㄱ'..'ㆎ' }) "ko-KR" else "en")

/** URL을 글자로 보이고(밑줄 + primary), 누르면 onOpen, 길게 누르거나 옆 버튼으로 복사(9.1). 기본 URI 처리기는 쓰지 않는다(열 앱이 없을 때 죽지 않게). */
@Composable
private fun UrlText(url: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copy: () -> Unit = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, url))) } }
    val text: AnnotatedString = buildAnnotatedString {
        withLink(
            LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { onOpen(url) },
        ) { append(url) }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                .pointerInput(url) { detectTapGestures(onLongPress = { copy() }) },
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(copy, Modifier.minimumInteractiveComponentSize()) {
            Icon(painterResource(R.drawable.ic_ms_content_copy), contentDescription = stringResource(R.string.about_copy_url))
        }
    }
}
