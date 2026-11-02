package com.jlptstudy.app.ui.home

import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.data.attribution.AssetAttributionRepository
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.review.MutableClock
import com.jlptstudy.app.ui.review.vocab
import com.jlptstudy.app.ui.settings.SettingsViewModel
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** 실제 Room(파일 DB)·실제 에셋을 쓰는 홈·설정 시험. "앱 재생성"은 DB를 닫고 다시 열어 새 ViewModel을 만드는 것으로 흉내 낸다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeRobolectricTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")
    private val clock = MutableClock(t0)
    private val scheduler = Fsrs6Scheduler()
    private var db: StudyDatabase? = null

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { db?.close(); Dispatchers.resetMain() }

    private suspend fun openStudy(): StudyRepository {
        db?.close()
        return RoomStudyRepository(StudyDatabase.open(context).also { db = it }.studyDao())
    }

    private val noAttribution = AssetAttributionRepository { throw FileNotFoundException("x") }
    private fun home(study: StudyRepository, content: FakeContent = FakeContent((1..30).map { vocab("w$it", "語", "ご") })) =
        HomeViewModel(study, content, noAttribution, clock) { ZoneOffset.UTC }

    private suspend fun HomeViewModel.await(pred: (HomeUiState) -> Boolean) = withTimeout(5_000) { state.first(pred) }

    // --- attribution.json ---

    private val assetJson get() = File(System.getProperty("app.dir")!!, "src/main/assets/content/attribution.json").readText()

    @Test fun `정상 에셋이면 firstNotice가 attribution_json과 같고 홈 상태에 실린다`() = runBlocking {
        val repo = AssetAttributionRepository.create(context)
        val data = repo.load()
        val expected = Json.parseToJsonElement(assetJson).jsonObject["firstNotice"]!!.jsonObject
        assertEquals(NoticeUi("firstNotice", expected["title"]!!.jsonPrimitive.content, expected["body"]!!.jsonPrimitive.content), data.firstNotice)
        assertTrue(data.notices.isNotEmpty() && data.sources.isNotEmpty())
        val vm = HomeViewModel(openStudy(), FakeContent((1..3).map { vocab("w$it", "語", "ご") }), repo, clock) { ZoneOffset.UTC }
        assertEquals(data.firstNotice, vm.await { it.status != HomeStatus.LOADING }.firstNotice)
    }

    @Test fun `에셋을 읽지 못하거나 깨졌으면 load가 예외를 던지고 홈은 첫 안내 카드 없이 정상이다`() = runBlocking {
        val bad = mapOf<String, () -> java.io.InputStream>(
            "없음" to { throw FileNotFoundException("attribution.json") },
            "깨진 JSON" to { ByteArrayInputStream("{ not json".toByteArray()) },
            "필수 필드 없음" to { ByteArrayInputStream("""{"contentVersion":"v","kind":"sample"}""".toByteArray()) },
        )
        for ((name, open) in bad) {
            val repo = AssetAttributionRepository(open = open)
            val thrown = runCatching { repo.load() }.exceptionOrNull()
            assertTrue("$name: 예외여야 한다", thrown != null)
            val vm = HomeViewModel(openStudy(), FakeContent((1..3).map { vocab("w$it", "語", "ご") }), repo, clock) { ZoneOffset.UTC }
            val s = vm.await { it.status != HomeStatus.LOADING }
            assertNull("$name", s.firstNotice)
            assertEquals("$name", HomeStatus.READY, s.status)
            assertTrue("$name: 카드는 없지만 dismissed 값은 그대로", s.showFirstNotice)
        }
    }

    // --- 새 카드 5장 더 ---

    @Test fun `새 카드 5장 더는 앱 재생성 뒤에도 같은 학습일에 유지되고 다음 학습일에는 설정값으로 돌아간다`() = runBlocking {
        var study = openStudy()
        study.updateSettings { it.copy(newCardsPerDay = 5, onboardingDone = true) }
        (1..5).forEach { // 오늘 새 카드 5장을 모두 도입
            study.applyReview(scheduler.schedule(CardState.new("vr:w$it", t0, scheduler.version), Rating.EASY, t0, SrsConfig(studyDay = StudyDayRule(ZoneOffset.UTC))))
        }
        var vm = home(study)
        assertEquals(HomeStatus.NEW_LIMIT_REACHED, vm.await { it.status != HomeStatus.LOADING }.status)
        vm.moreNewCards()
        val more = vm.await { it.newLimit == 10 }
        assertEquals(HomeStatus.READY, more.status); assertEquals(5, more.newTodayCount)
        assertEquals(5, study.settings().first().newCardsPerDay)

        study = openStudy() // 앱 재생성
        vm = home(study)
        assertEquals(10, vm.await { it.status != HomeStatus.LOADING }.newLimit)
        assertEquals(5, study.settings().first().newCardsPerDay)

        clock.advance(Duration.ofDays(1)) // 다음 학습일
        study = openStudy()
        assertEquals(5, home(study).await { it.status != HomeStatus.LOADING }.newLimit)
    }

    // --- 설정 저장 ---

    @Test fun `설정 값은 user_settings에 저장되고 앱 재생성 뒤에도 유지된다`() = runBlocking {
        var study = openStudy()
        val content = FakeContent(emptyList(), metaLevels = setOf(JlptLevel.N5, JlptLevel.N4))
        var vm = SettingsViewModel(study, content)
        vm.setLevels(setOf(JlptLevel.N5, JlptLevel.N4)); vm.setNewCards(25); vm.setDayBoundaryHour(6)
        vm.setKind(CardKind.KR, false); vm.setKind(CardKind.VP, true)
        vm.setFrontFurigana(true); vm.setBodyFurigana(false); vm.setTheme(ThemeMode.DARK)
        withTimeout(5_000) { // 쓰기는 Room 실행기에서 끝난다: 마지막 값이 보일 때까지 기다린 뒤 DB를 닫는다
            vm.state.first { it.theme == ThemeMode.DARK && it.newCardsPerDay == 25 && it.dayBoundaryHour == 6 && !it.kanjiCardsEnabled && it.vocabRecallEnabled && it.frontFurigana && !it.bodyFurigana && it.targetLevels.size == 2 }
        }

        study = openStudy()
        vm = SettingsViewModel(study, content)
        val s = withTimeout(5_000) { vm.state.first { !it.loading } }
        assertEquals(setOf(JlptLevel.N5, JlptLevel.N4), s.targetLevels)
        assertEquals(25, s.newCardsPerDay); assertEquals(6, s.dayBoundaryHour)
        assertFalse(s.kanjiCardsEnabled); assertTrue(s.vocabRecallEnabled); assertTrue(s.vocabRecognitionEnabled)
        assertTrue(s.frontFurigana); assertFalse(s.bodyFurigana); assertEquals(ThemeMode.DARK, s.theme)
        assertEquals("test", s.contentVersion)
        assertEquals(360, study.settings().first().dayBoundaryMinutes)
        assertEquals(listOf(true, true), s.levelOptions.filter { it.level in setOf(JlptLevel.N5, JlptLevel.N4) }.map { it.available })
        assertFalse(s.levelOptions.first { it.level == JlptLevel.N1 }.available)
    }

    @Test fun `범위 밖 값과 카드 종류 모두 끔은 저장되지 않는다`() = runBlocking {
        val study = openStudy()
        for (bad in listOf<(com.jlptstudy.app.data.study.UserSettings) -> com.jlptstudy.app.data.study.UserSettings>(
            { it.copy(newCardsPerDay = 4) }, { it.copy(newCardsPerDay = 55) }, { it.copy(newCardsPerDay = 12) },
            { it.copy(dayBoundaryMinutes = 780) }, { it.copy(dayBoundaryMinutes = -60) },
            { it.copy(vocabRecognitionEnabled = false, kanjiCardsEnabled = false, vocabRecallEnabled = false) },
            { it.copy(targetLevels = emptySet()) },
        )) assertThrows(IllegalArgumentException::class.java) { runBlocking { study.updateSettings(bad) } }
        val saved = study.settings().first()
        assertEquals(10, saved.newCardsPerDay); assertEquals(240, saved.dayBoundaryMinutes)
        assertEquals(setOf(JlptLevel.N5), saved.targetLevels); assertEquals(setOf(CardKind.VR, CardKind.KR, CardKind.GR), saved.enabledKinds)

        // ViewModel도 같다: 마지막 켜진 종류를 끄려 하면 저장하지 않고 경고만 올린다.
        val vm = SettingsViewModel(study, FakeContent(emptyList()))
        vm.state.first { !it.loading }
        vm.setKind(CardKind.KR, false)
        withTimeout(5_000) { vm.state.first { !it.kanjiCardsEnabled } }
        vm.setKind(CardKind.VR, false) // 이제 VR이 마지막
        val warned = withTimeout(5_000) { vm.state.first { it.kindsWarning } }
        assertTrue(warned.vocabRecognitionEnabled)
        assertTrue(study.settings().first().vocabRecognitionEnabled)
        vm.setNewCards(60); vm.setDayBoundaryHour(13); vm.setLevels(emptySet())
        vm.setNewCards(15); withTimeout(5_000) { vm.state.first { it.newCardsPerDay == 15 } } // 범위 안 값은 저장되므로, 위 세 호출이 먼저 끝났음을 보인다
        study.updateSettings { it.copy(newCardsPerDay = 10) }
        val after = study.settings().first()
        assertEquals(10, after.newCardsPerDay); assertEquals(240, after.dayBoundaryMinutes); assertEquals(setOf(JlptLevel.N5), after.targetLevels)
    }
}
