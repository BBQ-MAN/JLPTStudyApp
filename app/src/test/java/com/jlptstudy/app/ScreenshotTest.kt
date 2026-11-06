package com.jlptstudy.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.jlptstudy.app.nav.AppContent
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.common.JpSpans
import com.jlptstudy.app.ui.common.JpText
import com.jlptstudy.app.ui.common.RubyText
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusScreen
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.app.ui.theme.LocalJpTypography
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ui-design 12절 이름의 스크린샷. 실행하면 프로젝트 루트 screenshots/에 라이트·다크 한 쌍씩 쓴다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val dir = System.getProperty("screenshots.dir")!!

    /** 같은 화면을 라이트·다크로 두 번 캡처한다. 시계는 직접 돌려(300 ms 로딩 지연 포함) 결정적으로 만든다. */
    private fun shots(name: String, fontScale: Float = 1f, advanceMs: Long = 400, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), com.jlptstudy.app.ui.common.LocalFrozenProgress provides true) {
                JlptTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(advanceMs)
        val suffix = if (fontScale > 1f) "_font200" else ""
        rule.onRoot().captureRoboImage("$dir/${name}_light$suffix.png")
        dark = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(6_660) // 로딩 표시의 반복 주기(1332 ms x 5)와 같아 라이트와 같은 프레임이다. 내비 색 전환도 끝난다
        rule.onRoot().captureRoboImage("$dir/${name}_dark$suffix.png")
    }

    @Composable private fun Shell(state: AppState) = AppContent(state, rememberNavController(), onRetry = {})

    @Test fun nav_shell_home() = shots("nav_shell_home") { Shell(AppState.Ready(needsOnboarding = false)) }
    @Test fun nav_shell_home_font200() = shots("nav_shell_home", fontScale = 2f) { Shell(AppState.Ready(false)) }
    @Test fun nav_shell_grammar() = shots("nav_shell_grammar") {
        val nav = rememberNavController()
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.navigate(com.jlptstudy.app.nav.Grammar) }
        AppContent(AppState.Ready(needsOnboarding = false), nav, onRetry = {})
    }
    @Test fun nav_shell_all_tabs() = shots("nav_shell_all_tabs") { Shell(AppState.Ready(needsOnboarding = false)) }
    @Test fun nav_shell_all_tabs_font200() = shots("nav_shell_all_tabs", fontScale = 2f) { Shell(AppState.Ready(false)) }
    @Test fun app_opening() = shots("app_opening") { Shell(AppState.Opening) }
    @Test fun error_content_db() = shots("error_content_db") { Shell(AppState.ContentDbFailed) }
    @Test fun error_record_db() = shots("error_record_db") { Shell(AppState.RecordDbFailed) }
    @Test fun error_content_db_font200() = shots("error_content_db", fontScale = 2f) { Shell(AppState.ContentDbFailed) }

    @Test fun status_loading() = shots("status_loading") {
        StatusScreen(title = "불러오는 중입니다", body = "잠시만 기다려 주세요.", loading = true)
    }

    @Test fun status_empty() = shots("status_empty") {
        StatusScreen(
            title = "표시할 항목이 없습니다", body = "필터를 바꾸거나 다른 급수를 골라 보세요.",
            icon = painterResource(R.drawable.ic_ms_info), actions = listOf(StatusAction("필터 지우기") {}),
        )
    }

    @Test fun status_error() = shots("status_error") {
        StatusScreen(
            title = "문제가 생겼습니다", body = "잠시 뒤에 다시 시도해 주세요.",
            icon = painterResource(R.drawable.ic_ms_error), actions = listOf(StatusAction("다시 시도") {}, StatusAction("닫기") {}),
        )
    }

    /** 일본어 글자 확인용: 글리프(네모 아님), 한국식/일본식 자형이 다른 한자(直, 骨, 角), 후리가나, 밑줄, 혼용 문장. */
    @Test fun jp_display_sample() = shots("jp_display_sample") {
        val jp = LocalJpTypography.current
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JpText("直骨角", style = jp.kanjiHero)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RubyText(Furigana.fromHeadword("食べる", "たべる"), style = jp.headword)
                    RubyText(Furigana.fromHeadword("今日", "きょう"), style = jp.headword)
                }
                RubyText(Furigana.parse("あとで[[{食|た}べて]]ください"), style = jp.bodyLarge)
                RubyText(Furigana.parse("{日本|にほん}語を{勉強|べんきょう}します"), style = jp.bodyLarge, showRuby = false)
                RubyText(Furigana.parse("{私|わたし}は[[毎日学校へ行って、友達と一緒に勉強します。]]昨日は{図書館|としょかん}で本を読みました。"), style = jp.bodyLarge)
                Text(JpSpans.annotate("〜さん보다 정중한 호칭, 「食べる」의 정중형, コーヒー・紅茶 중에서"), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
