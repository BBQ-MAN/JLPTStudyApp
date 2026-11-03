package com.jlptstudy.app.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.ui.common.LocalFrozenProgress
import com.jlptstudy.app.ui.level.LevelOption
import com.jlptstudy.app.ui.level.LevelSelectScreen
import com.jlptstudy.app.ui.level.LevelStatus
import com.jlptstudy.app.ui.level.LevelUiState
import com.jlptstudy.app.ui.level.levelOptions
import com.jlptstudy.app.ui.settings.SettingsScreen
import com.jlptstudy.app.ui.settings.SettingsUiState
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** S01·S02·S08 스크린샷(ui-design 12절 이름). 라이트·다크 한 쌍씩 screenshots/에 쓴다. */
abstract class HomeShots {
    @get:Rule val rule = createComposeRule()
    private val dir = System.getProperty("screenshots.dir")!!

    @org.junit.Before fun fixZone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")) // 다음 복습 시각 문구가 기기 시간대에 따라 달라지지 않게

    protected fun shots(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalFrozenProgress provides true) {
                JlptTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(600) // 300 ms 로딩 지연을 지난 모습
        val suffix = if (fontScale > 1f) "_font200" else ""
        rule.onRoot().captureRoboImage("$dir/${name}_light$suffix.png")
        dark = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onRoot().captureRoboImage("$dir/${name}_dark$suffix.png")
    }

    // 고정 시각(시험 픽스처, 앱 콘텐츠 아님): 2026-11-03 04:30 KST 기준 다음 복습
    protected val nextDue = Instant.parse("2026-11-03T05:00:00Z").toEpochMilli()
    protected val notice = NoticeUi("firstNotice", "샘플 콘텐츠 안내", "개발용 샘플 콘텐츠입니다. 한국어 뜻은 이 앱이 직접 작성했습니다. 뜻마다 만든 방법과 검수 상태를 표시합니다.")
    protected val progress = listOf(LevelProgress(JlptLevel.N5, 35, 120), LevelProgress(JlptLevel.N4, 0, 80))
    protected val ready = HomeUiState(
        status = HomeStatus.READY, dueCount = 18, newTodayCount = 10, newLimit = 10,
        targetLevels = setOf(JlptLevel.N5, JlptLevel.N4), levelProgress = progress,
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HomeScreenshotTest : HomeShots() {
    private val allAvailable = levelOptions.map { LevelOption(it.level, 120 - it.level.n * 10, 80 - it.level.n * 5, it.level.n >= 4) }

    @Test fun level_loading() = shots("level_loading") { LevelSelectScreen(LevelUiState(LevelStatus.PREPARING), {}, {}, {}, {}, {}) }
    @Test fun level_empty_selection() = shots("level_empty_selection") {
        LevelSelectScreen(LevelUiState(LevelStatus.READY, allAvailable, emptySet()), {}, {}, {}, {}, {})
    }
    @Test fun level_loaded() = shots("level_loaded") {
        LevelSelectScreen(LevelUiState(LevelStatus.READY, allAvailable, setOf(JlptLevel.N5, JlptLevel.N4), newCardsPerDay = 15), {}, {}, {}, {}, {})
    }
    @Test fun level_unavailable() = shots("level_unavailable") {
        LevelSelectScreen(LevelUiState(LevelStatus.READY, levelOptions, setOf(JlptLevel.N5)), {}, {}, {}, {}, {})
    }

    @Test fun home_loading() = shots("home_loading") { HomeScreen(HomeUiState()) }
    @Test fun home_ready() = shots("home_ready") { HomeScreen(ready) }
    @Test fun home_no_level() = shots("home_no_level") { HomeScreen(HomeUiState(status = HomeStatus.NO_LEVEL_SELECTED)) }
    @Test fun home_no_content() = shots("home_no_content") {
        HomeScreen(HomeUiState(status = HomeStatus.NO_CONTENT_FOR_LEVELS, targetLevels = setOf(JlptLevel.N3)))
    }
    @Test fun home_all_done() = shots("home_all_done") {
        HomeScreen(ready.copy(status = HomeStatus.ALL_DONE, dueCount = 0, newTodayCount = 0, nextDueAtEpochMs = nextDue, nextDueCount = 23))
    }
    @Test fun home_new_limit() = shots("home_new_limit") {
        HomeScreen(ready.copy(status = HomeStatus.NEW_LIMIT_REACHED, dueCount = 0, newTodayCount = 0, nextDueAtEpochMs = nextDue, nextDueCount = 23))
    }
    @Test fun home_new_limit_font200() = shots("home_new_limit", fontScale = 2f) {
        HomeScreen(ready.copy(status = HomeStatus.NEW_LIMIT_REACHED, dueCount = 0, newTodayCount = 0, nextDueAtEpochMs = nextDue, nextDueCount = 23))
    }
    @Test fun home_first_notice() = shots("home_first_notice") { HomeScreen(ready.copy(showFirstNotice = true, firstNotice = notice)) }
    @Test fun home_sample_banner() = shots("home_sample_banner") { HomeScreen(ready.copy(isSampleContent = true)) }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h960dp-xhdpi")
class SettingsScreenshotTest : HomeShots() {
    @Test fun settings_loaded() = shots("settings_loaded") {
        SettingsScreen(
            SettingsUiState(
                loading = false, targetLevels = setOf(JlptLevel.N5, JlptLevel.N4), newCardsPerDay = 15, dayBoundaryHour = 4,
                vocabRecallEnabled = false, contentVersion = "v1abdec2bac5e", levelOptions = levelOptions,
            ),
            SnackbarHostState(),
        )
    }
    @Test fun settings_with_grammar() = shots("settings_with_grammar") {
        SettingsScreen(
            SettingsUiState(
                loading = false, targetLevels = setOf(JlptLevel.N5, JlptLevel.N4), newCardsPerDay = 15, dayBoundaryHour = 4,
                vocabRecallEnabled = false, grammarAvailable = true, contentVersion = "v1abdec2bac5e", levelOptions = levelOptions,
            ),
            SnackbarHostState(),
        )
    }
}
