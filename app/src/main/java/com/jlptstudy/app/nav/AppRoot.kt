package com.jlptstudy.app.nav

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.AppState
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.StatusScreen
import com.jlptstudy.app.ui.common.startExternal
import com.jlptstudy.app.ui.error.AppErrorScreen
import com.jlptstudy.app.ui.theme.LoadingDelayMs
import com.jlptstudy.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import com.jlptstudy.app.ui.detail.DetailActions
import com.jlptstudy.app.ui.detail.KanjiDetailRoute
import com.jlptstudy.app.ui.detail.KanjiDetailViewModel
import com.jlptstudy.app.ui.detail.VocabDetailRoute
import com.jlptstudy.app.ui.detail.VocabDetailViewModel
import com.jlptstudy.app.ui.dictionary.BrowseRoute
import com.jlptstudy.app.ui.dictionary.BrowseScreen
import com.jlptstudy.app.ui.dictionary.BrowseUiState
import com.jlptstudy.app.ui.dictionary.BrowseViewModel
import com.jlptstudy.app.ui.grammar.GrammarDetailActions
import com.jlptstudy.app.ui.grammar.GrammarDetailRoute
import com.jlptstudy.app.ui.grammar.GrammarDetailViewModel
import com.jlptstudy.app.ui.grammar.GrammarListRoute
import com.jlptstudy.app.ui.grammar.GrammarListScreen
import com.jlptstudy.app.ui.grammar.GrammarListUiState
import com.jlptstudy.app.ui.grammar.GrammarListViewModel
import com.jlptstudy.app.ui.home.HomeActions
import com.jlptstudy.app.ui.home.HomeRoute
import com.jlptstudy.app.ui.home.HomeScreen
import com.jlptstudy.app.ui.home.HomeUiState
import com.jlptstudy.app.ui.home.HomeViewModel
import com.jlptstudy.app.ui.level.LevelSelectRoute
import com.jlptstudy.app.ui.level.LevelSelectViewModel
import com.jlptstudy.app.ui.about.AboutRoute
import com.jlptstudy.app.ui.about.AboutScreen
import com.jlptstudy.app.ui.about.AboutUiState
import com.jlptstudy.app.ui.about.AboutViewModel
import com.jlptstudy.app.ui.about.rememberMeaningSourceTexts
import com.jlptstudy.app.ui.about.rememberProvenanceSheet
import com.jlptstudy.app.ui.settings.SettingsRoute
import com.jlptstudy.app.ui.settings.SettingsScreen
import com.jlptstudy.app.ui.settings.SettingsUiState
import com.jlptstudy.app.ui.settings.SettingsViewModel
import com.jlptstudy.app.ui.review.ReviewRoute
import com.jlptstudy.app.ui.review.ReviewViewModel
import kotlin.reflect.KClass

@Composable
fun AppRoot(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val state by container.state.collectAsStateWithLifecycle()
    AppContent(state, navController, container::retry, container)
}

/** AppState 하나로 그린다. Opening 동안에는 NavHost를 만들지 않는다(architecture.md 7절 "시작"). */
@Composable
fun AppContent(state: AppState, navController: NavHostController, onRetry: () -> Unit, container: AppContainer? = null) {
    // 프로세스가 죽었다 살아나면 저장된 back stack이 새 시작 라우트를 덮어쓴다. 상태 종류가 바뀌었으면 시작 라우트로 되돌린다.
    var lastReady by rememberSaveable { mutableStateOf<Boolean?>(null) }
    if (state is AppState.Opening) {
        OpeningScreen()
        return
    }
    val start: Any = when {
        state is AppState.Ready && state.needsOnboarding -> LevelSelect
        state is AppState.Ready -> Home
        else -> AppError
    }
    val ready = state is AppState.Ready
    val reset = lastReady != null && lastReady != ready
    key(state) { AppShell(state, start, navController, onRetry, reset, container) { lastReady = ready } }
}

@Composable
fun OpeningScreen() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LoadingDelayMs); show = true } // 300 ms 안에 끝나면 빈 surface만
    if (show) {
        StatusScreen(
            title = stringResource(R.string.opening_title),
            body = stringResource(R.string.opening_body),
            loading = true,
        )
    } else {
        Surface(Modifier.fillMaxSize().testTag("opening_blank"), color = MaterialTheme.colorScheme.surface) {}
    }
}

private enum class Tab(val route: Any, val type: KClass<*>, val label: Int, val icon: Int, val iconFill: Int) {
    HOME(Home, Home::class, R.string.tab_home, R.drawable.ic_ms_home, R.drawable.ic_ms_home_fill),
    DICTIONARY(Dictionary, Dictionary::class, R.string.tab_dictionary, R.drawable.ic_ms_menu_book, R.drawable.ic_ms_menu_book_fill),
    GRAMMAR(Grammar, Grammar::class, R.string.tab_grammar, R.drawable.ic_ms_school, R.drawable.ic_ms_school_fill),
    SETTINGS(Settings(), Settings::class, R.string.tab_settings, R.drawable.ic_ms_settings, R.drawable.ic_ms_settings_fill),
}

@Composable
private fun AppShell(
    state: AppState, start: Any, nav: NavHostController, onRetry: () -> Unit, reset: Boolean, container: AppContainer?, onShown: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (reset) nav.navigate(start) { popUpTo(0) { inclusive = true } }
        onShown()
    }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    // 탭 목적지에서만 내비게이션 바(큰 화면은 레일)를 그린다. S90·전체 화면 라우트에서는 그리지 않는다.
    val showTabs = state is AppState.Ready && if (dest == null) start != LevelSelect else Tab.entries.any { dest.hasRoute(it.type) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noAppInfo = stringResource(R.string.app_info_unavailable)
    val openAppInfo = {
        val opened = context.startExternal(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
        if (!opened) scope.launch { snackbar.showSnackbar(noAppInfo) }
    }
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primary, // 선택 표시 3:1 이상
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primary,
        ),
    )
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = dest?.hasRoute(tab.type) == true,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo<Home> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        val sel = dest?.hasRoute(tab.type) == true
                        Icon(painterResource(if (sel) tab.iconFill else tab.icon), contentDescription = null)
                    },
                    label = {
                        val sel = dest?.hasRoute(tab.type) == true
                        Text(stringResource(tab.label), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    },
                    alwaysShowLabel = true,
                    colors = itemColors,
                )
            }
        },
        layoutType = if (showTabs) {
            // 폭 기준(ui-design 1.2): 가로 폰은 높이가 Compact여도 레일
            if (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == androidx.window.core.layout.WindowWidthSizeClass.COMPACT) {
                NavigationSuiteType.NavigationBar
            } else {
                NavigationSuiteType.NavigationRail
            }
        } else {
            NavigationSuiteType.None
        },
    ) {
      Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = start) {
            composable<LevelSelect> {
                val c = container
                val content = c?.contentRepository
                val study = c?.studyRepository
                if (content != null && study != null) {
                    val vm: LevelSelectViewModel = viewModel { LevelSelectViewModel(study, content) }
                    LevelSelectRoute(
                        vm,
                        onStarted = { nav.navigate(Home) { popUpTo<LevelSelect> { inclusive = true } }; c.onboardingCompleted() },
                        onOpenNotice = { nav.navigate(About("N-09")) },
                        onOpenAppInfo = openAppInfo,
                    )
                }
            }
            composable<Home> {
                val c = container
                val content = c?.contentRepository
                val study = c?.studyRepository
                if (c != null && content != null && study != null) {
                    val vm: HomeViewModel = viewModel { HomeViewModel(study, content, c.attributionRepository, c.clock, c.zone) }
                    HomeRoute(
                        vm,
                        HomeActions(
                            onStart = { nav.navigate(Review) },
                            onPickLevels = { nav.navigate(Settings(openLevelPicker = true)) { popUpTo<Home> { saveState = false }; launchSingleTop = true } },
                            onChangeLimit = { nav.navigate(Settings()) { popUpTo<Home> { saveState = true }; launchSingleTop = true; restoreState = true } },
                            onNoticeMore = { nav.navigate(About("N-10")) },
                            onAbout = { nav.navigate(About()) },
                            onDictionary = { nav.navigate(Dictionary) { popUpTo<Home> { saveState = true }; launchSingleTop = true; restoreState = true } },
                        ),
                    )
                } else {
                    HomeScreen(HomeUiState()) // 컨테이너 없이 그리는 시험·미리보기: 로딩 자리표시
                }
            }
            composable<Dictionary> {
                val content = container?.contentRepository
                val study = container?.studyRepository
                if (content != null && study != null) {
                    val vm: BrowseViewModel = viewModel { BrowseViewModel(content, study) }
                    BrowseRoute(vm, onWord = { nav.navigate(VocabDetail(it)) }, onKanji = { nav.navigate(KanjiDetail(it)) }, onFailed = { container?.contentReadFailed() })
                } else {
                    BrowseScreen(BrowseUiState()) // 컨테이너 없이 그리는 시험·미리보기: 로딩 자리표시
                }
            }
            composable<Grammar> {
                val grammar = container?.grammarRepository
                val study = container?.studyRepository
                if (grammar != null && study != null) {
                    val vm: GrammarListViewModel = viewModel { GrammarListViewModel(grammar, study) }
                    GrammarListRoute(vm, onOpen = { nav.navigate(GrammarDetail(it)) }, onNotice = { nav.navigate(About("N-09")) }, onFailed = { container?.contentReadFailed() })
                } else {
                    GrammarListScreen(GrammarListUiState()) // 컨테이너 없이 그리는 시험·미리보기: 로딩 자리표시
                }
            }
            composable<GrammarDetail> { entry ->
                val grammar = container?.grammarRepository
                val study = container?.studyRepository
                if (grammar != null && study != null) {
                    val id = entry.toRoute<GrammarDetail>().id
                    val vm: GrammarDetailViewModel = viewModel(key = "grammar-$id") { GrammarDetailViewModel(id, grammar, study) }
                    val onChip = rememberProvenanceSheet(rememberMeaningSourceTexts(container?.attributionRepository)) { nav.navigate(About()) }
                    GrammarDetailRoute(
                        vm,
                        GrammarDetailActions(
                            onFailed = { container?.contentReadFailed() }, onToggleFurigana = vm::toggleFurigana, onBack = { nav.popBackStack() },
                            onAboutLevel = { nav.navigate(About("N-09")) }, onNotice = { nav.navigate(About("N-11")) },
                            onGrammar = { nav.navigate(GrammarDetail(it)) }, onChipClick = onChip,
                        ),
                    )
                }
            }
            composable<VocabDetail> { entry ->
                val content = container?.contentRepository
                val study = container?.studyRepository
                if (content != null && study != null) {
                    val id = entry.toRoute<VocabDetail>().id
                    val vm: VocabDetailViewModel = viewModel { VocabDetailViewModel(id, content, study) }
                    val onChip = rememberProvenanceSheet(rememberMeaningSourceTexts(container?.attributionRepository)) { nav.navigate(About()) }
                    VocabDetailRoute(vm, detailActions(nav, vm::toggleFurigana, container, onChip))
                }
            }
            composable<KanjiDetail> { entry ->
                val content = container?.contentRepository
                if (content != null) {
                    val literal = entry.toRoute<KanjiDetail>().literal
                    val vm: KanjiDetailViewModel = viewModel { KanjiDetailViewModel(literal, content) {
                        container?.attributionRepository?.load()?.sources?.firstOrNull { it.id == "S-03" }?.name
                    } }
                    KanjiDetailRoute(vm, detailActions(nav, container = container))
                }
            }
            composable<Settings> { entry ->
                val c = container
                val content = c?.contentRepository
                val study = c?.studyRepository
                if (content != null && study != null) {
                    val vm: SettingsViewModel = viewModel { SettingsViewModel(study, content) }
                    SettingsRoute(
                        vm, openLevelPicker = entry.toRoute<Settings>().openLevelPicker,
                        onAbout = { nav.navigate(About()) }, onLevelNotice = { nav.navigate(About("N-09")) },
                    )
                } else {
                    SettingsScreen(SettingsUiState(), remember { SnackbarHostState() })
                }
            }
            composable<Review> {
                val c = container
                val content = c?.contentRepository
                val study = c?.studyRepository
                if (c != null && content != null && study != null) {
                    val vm: ReviewViewModel = viewModel { ReviewViewModel(study, content, c.scheduler, c.clock, c.zone) }
                    val onChip = rememberProvenanceSheet(rememberMeaningSourceTexts(c.attributionRepository)) { nav.navigate(About()) }
                    ReviewRoute(vm, onExit = { nav.popBackStack() }, onChipClick = onChip, onGrammarDetail = { nav.navigate(GrammarDetail(it)) })
                }
            }
            composable<About> { entry ->
                val c = container
                if (c != null) {
                    // S90에서 온 경우 contentRepository는 null이다. 집계 줄만 빠지고 나머지는 그대로 보인다.
                    val vm: AboutViewModel = viewModel { AboutViewModel(c.attributionRepository, c.contentRepository) }
                    AboutRoute(vm, entry.toRoute<About>().anchor, onBack = { nav.popBackStack() })
                } else {
                    AboutScreen(AboutUiState(), entry.toRoute<About>().anchor, onBack = { nav.popBackStack() })
                }
            }
            composable<AppError> {
                AppErrorScreen(
                    state = state,
                    onRetry = onRetry,
                    onOpenAppInfo = openAppInfo,
                    onOpenSources = { nav.navigate(About()) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
      }
    }
}

private fun detailActions(
    nav: NavHostController, onToggleFurigana: () -> Unit = {}, container: AppContainer? = null,
    onChipClick: ((com.jlptstudy.app.ui.review.MeaningUi) -> Unit)? = null,
) = DetailActions(
    onChipClick = onChipClick,
    onFailed = { container?.contentReadFailed() },
    onToggleFurigana = onToggleFurigana,
    onBack = { nav.popBackStack() }, onAboutLevel = { nav.navigate(About("N-09")) },
    onKanji = { nav.navigate(KanjiDetail(it)) }, onStrokeSource = { nav.navigate(About("S-03")) }, onWord = { nav.navigate(VocabDetail(it)) },
)
