package com.jlptstudy.app.ui.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.JpSpans
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusContent
import com.jlptstudy.app.ui.review.LevelChip
import com.jlptstudy.app.ui.theme.JaKoHintLocales
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.jlptstudy.app.ui.theme.Spacing

class BrowseActions(
    val onQuery: (String) -> Unit = {}, val onTab: (BrowseTab) -> Unit = {}, val onToggleLevel: (JlptLevel) -> Unit = {},
    val onClearLevels: () -> Unit = {}, val onWord: (String) -> Unit = {}, val onKanji: (String) -> Unit = {},
)

@Composable
fun BrowseRoute(vm: BrowseViewModel, onWord: (String) -> Unit, onKanji: (String) -> Unit, onFailed: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.status == BrowseStatus.ERROR) LaunchedEffect(Unit) { onFailed() }
    BrowseScreen(state, BrowseActions(vm::setQuery, vm::setTab, vm::toggleLevel, vm::clearLevels, onWord, onKanji))
}

/** S05(ui-design 6.5): 검색, 단어·한자 탭, 급수 칩, 목록. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(state: BrowseUiState, actions: BrowseActions = BrowseActions()) {
    Surface(Modifier.fillMaxSize().testTag("browse_screen"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            SearchField(state.query, actions.onQuery)
            TabRow(selectedTabIndex = state.tab.ordinal) {
                BrowseTab.entries.forEach { t ->
                    Tab(
                        selected = t == state.tab, onClick = { actions.onTab(t) }, modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(if (t == BrowseTab.VOCAB) R.string.dict_tab_words else R.string.dict_tab_kanji)) },
                    )
                }
            }
            if (state.availableLevels.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    JlptLevel.entries.filter { it in state.availableLevels }.forEach { l ->
                        FilterChip(
                            selected = l in state.levels, onClick = { actions.onToggleLevel(l) }, label = { Text(l.name) },
                            leadingIcon = if (l in state.levels) {
                                { Icon(painterResource(R.drawable.ic_ms_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = l in state.levels, borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.testTag("level_chip_${l.name}"),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.status) {
                    BrowseStatus.LOADING, BrowseStatus.ERROR -> StatusContent(stringResource(R.string.dict_loading), loading = true, modifier = Modifier.align(Alignment.Center))
                    BrowseStatus.NO_RESULT -> Empty(
                        buildAnnotatedString {
                            val parts = stringResource(R.string.dict_no_result_title, "\u0000").split("\u0000")
                            append(parts[0]); withStyle(JpSpan) { append(state.query.trim()) }; append(parts.getOrElse(1) { "" })
                        },
                        R.string.dict_no_result_body, R.string.dict_filter_clear,
                    ) { actions.onQuery(""); actions.onClearLevels() }
                    BrowseStatus.NO_CONTENT_FOR_FILTER -> Empty(AnnotatedString(stringResource(R.string.dict_no_content_title)), R.string.dict_no_content_body, R.string.dict_clear_levels, actions.onClearLevels)
                    BrowseStatus.RESULTS -> if (state.tab == BrowseTab.VOCAB) WordList(state.items, actions.onWord) else KanjiGrid(state.items, actions.onKanji)
                }
            }
        }
    }
}

@Composable
private fun Empty(title: AnnotatedString, body: Int, action: Int, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        StatusContent("", titleAnnotated = title, body = stringResource(body), takeFocus = false, actions = listOf(StatusAction(stringResource(action), onAction)))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    var text by remember { mutableStateOf(query) }
    LaunchedEffect(query) { if (query != text) text = query } // 결과 없음의 '지우기' 같은 밖의 변경을 따른다
    OutlinedTextField(
        value = text, onValueChange = { text = it; onQuery(it) }, singleLine = true,
        textStyle = LocalJpTypography.current.body,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("search_field"),
        label = { Text(stringResource(R.string.dict_search_hint), style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_ms_search), contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) IconButton({ text = ""; onQuery("") }, Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_ms_close), contentDescription = stringResource(R.string.dict_search_clear))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, hintLocales = JaKoHintLocales),
    )
}

@Composable
private fun WordList(items: List<BrowseItem>, onWord: (String) -> Unit) {
    val jp = LocalJpTypography.current
    LazyColumn(Modifier.fillMaxSize().testTag("word_list")) {
        items(items, key = { it.id }) { item ->
            val w = item as BrowseItem.Word
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onWord(w.id) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.Center) {
                            JpText(w.written ?: w.reading, style = jp.body)
                            if (w.written != null) JpText(w.reading, style = jp.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        w.meaningKo?.let {
                            // 목록이므로 한 줄 말줄임을 허용한다(상세에서 전체)
                            Text(JpSpans.annotate(it), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    LevelChip(w.level)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun KanjiGrid(items: List<BrowseItem>, onKanji: (String) -> Unit) {
    val jp = LocalJpTypography.current
    // 칸 최소 폭 = max(64 dp, 한자 글자 크기 × 1.5): 글꼴 200%면 72 dp
    val minCell: Dp = with(LocalDensity.current) { maxOf(64.dp, (jp.title.fontSize * 1.5f).toDp()) }
    LazyVerticalGrid(
        GridCells.Adaptive(minCell), Modifier.fillMaxSize().testTag("kanji_grid"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        gridItems(items, key = { it.id }) { item ->
            val k = item as BrowseItem.Kanji
            Column(
                Modifier.heightIn(min = 64.dp).clickable(role = Role.Button) { onKanji(k.literal) }.padding(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                JpText(k.literal, style = jp.title)
                k.koreanReading?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
