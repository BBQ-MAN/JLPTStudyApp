package com.jlptstudy.app.ui.home

import app.cash.turbine.test
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.attribution.AttributionData
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.attribution.LinkUi
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.review.MutableClock
import com.jlptstudy.app.ui.review.ReviewStatus
import com.jlptstudy.app.ui.review.ReviewViewModel
import com.jlptstudy.app.ui.review.reviewState
import com.jlptstudy.app.ui.review.vocab
import com.jlptstudy.app.ui.review.kanji
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.SessionPlanner
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")
    private val clock = MutableClock(t0)
    private val scheduler = Fsrs6Scheduler()
    private val dispatcher = UnconfinedTestDispatcher()
    private val failingAttribution = object : AttributionRepository {
        override suspend fun load(): AttributionData = throw java.io.IOException("broken")
        override suspend fun openSourceLicenses() = emptyList<com.jlptstudy.app.data.attribution.LicenseUi>()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun rule(b: Int = 240) = StudyDayRule(ZoneOffset.UTC, b)
    private fun words(n: Int, level: JlptLevel = JlptLevel.N5) = (1..n).map { vocab("w$it", "語$it", "ご$it", level = level) }
    private fun home(study: MemStudy, content: FakeContent, attribution: AttributionRepository = failingAttribution) =
        HomeViewModel(study, content, attribution, clock) { ZoneOffset.UTC }
    private fun review(study: MemStudy, content: FakeContent) = ReviewViewModel(study, content, scheduler, clock) { ZoneOffset.UTC }

    /** 새 카드로 시작해 EASY로 평가한 상태(오늘 도입으로 기록, 다음 기한은 며칠 뒤)를 만든다. */
    private suspend fun MemStudy.learn(cardId: String, rating: Rating = Rating.EASY) {
        applyReview(scheduler.schedule(CardState.new(cardId, t0, scheduler.version), rating, t0, SrsConfig(studyDay = rule())))
    }

    private suspend fun HomeViewModel.settled(): HomeUiState {
        var out = HomeUiState()
        state.test { out = expectMostRecentItem() }
        return out
    }

    @Test fun `dueCount와 newTodayCount는 SessionPlanner 결과와 같다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, onboardingDone = true))
        val ids = listOf("vr:w1", "vr:w2", "vr:w3")
        ids.forEachIndexed { i, id ->
            study.states[id] = reviewState(id, t0.plus(Duration.ofHours(i * 20L - 10))) // 이미 지남, 오늘 안, 내일
        }
        val content = FakeContent(words(30))
        val s = home(study, content).settled()
        val expectedDue = SessionPlanner.plan(study.states.values.toList(), emptyList(), t0, 20, rule())
        assertEquals(HomeStatus.READY, s.status)
        assertEquals(expectedDue.size, s.dueCount)
        val cands = content.newCardOrder(JlptLevel.entries.toSet(), setOf(com.jlptstudy.app.CardKind.VR, com.jlptstudy.app.CardKind.KR)).map { it.cardId }
        assertEquals(SessionPlanner.selectNew(cands, study.states.keys, emptyList(), 5).size, s.newTodayCount)
        assertEquals(5, s.newTodayCount); assertEquals(5, s.newLimit)
    }

    @Test fun `설정 변경 급수 새 카드 한도 경계가 홈 값에 반영된다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, targetLevels = setOf(JlptLevel.N5), onboardingDone = true))
        // 내일 05:00Z 기한 복습 카드: 경계 04:00이면 아직 오늘 기한이 아니고, 06:00이면 오늘 기한이다.
        study.states["vr:w1"] = reviewState("vr:w1", Instant.parse("2026-11-03T05:00:00Z"))
        val content = FakeContent(words(30) + (1..4).map { vocab("n4_$it", "語", "ご", level = JlptLevel.N4) })
        home(study, content).state.test {
            var s = expectMostRecentItem()
            assertEquals(0, s.dueCount); assertEquals(5, s.newTodayCount)
            study.settingsFlow.value = study.settingsFlow.value.copy(newCardsPerDay = 10); s = expectMostRecentItem()
            assertEquals(10, s.newTodayCount); assertEquals(10, s.newLimit)
            study.settingsFlow.value = study.settingsFlow.value.copy(dayBoundaryMinutes = 360); s = expectMostRecentItem()
            assertEquals(1, s.dueCount)
            study.settingsFlow.value = study.settingsFlow.value.copy(targetLevels = setOf(JlptLevel.N4)); s = expectMostRecentItem()
            assertEquals(0, s.dueCount) // 뺀 급수의 복습은 멈춘다
            assertEquals(setOf(JlptLevel.N4), s.targetLevels)
            assertEquals(listOf(JlptLevel.N4), s.levelProgress.map { it.level })
        }
    }

    @Test fun `retired 없는 카드 꺼진 종류 대상 밖 급수는 홈 수치에 안 들어가고 Review 대기열과 같다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, targetLevels = setOf(JlptLevel.N5), kanjiCardsEnabled = false, onboardingDone = true))
        val overdue = t0.minus(Duration.ofDays(1))
        val later = t0.plus(Duration.ofDays(3))
        val content = FakeContent(
            listOf(vocab("ok1", "語", "ご"), vocab("ok2", "語", "ご"), vocab("gone", "語", "ご"), vocab("n4", "語", "ご", level = JlptLevel.N4), kanji("直")),
            retired = mutableSetOf("vr:gone"),
        )
        study.states["vr:ok1"] = reviewState("vr:ok1", overdue)
        study.states["vr:ok2"] = reviewState("vr:ok2", later)
        study.states["vr:gone"] = reviewState("vr:gone", overdue) // retired
        study.states["vr:missing"] = reviewState("vr:missing", overdue) // 콘텐츠에 없음
        study.states["vr:n4"] = reviewState("vr:n4", overdue) // 대상 급수 밖
        study.states["kr:直"] = reviewState("kr:直", overdue) // 꺼진 카드 종류
        val h = home(study, content).settled()
        assertEquals(1, h.dueCount)
        // 기한이 아직 안 된 카드 중 포함 대상은 ok2뿐이다(다른 기한 지난 카드는 제외 대상이라 nextDue 후보가 아니다)
        val r = review(study, content).state.value
        assertEquals(ReviewStatus.READY, r.status)
        assertEquals(h.dueCount, r.remainingReview)
        assertEquals(h.newTodayCount, r.remainingNew)

        // 새 카드가 모두 소진된 ALL_DONE에서 nextDue는 포함 대상만으로 정해진다
        val study2 = MemStudy(UserSettings(newCardsPerDay = 5, targetLevels = setOf(JlptLevel.N5), kanjiCardsEnabled = false, onboardingDone = true))
        val content2 = FakeContent(listOf(vocab("ok2", "語", "ご"), vocab("x", "語", "ご", level = JlptLevel.N4), kanji("直")), retired = mutableSetOf())
        study2.states["vr:ok2"] = reviewState("vr:ok2", later)
        study2.states["vr:x"] = reviewState("vr:x", t0.plus(Duration.ofHours(30)))
        study2.states["kr:直"] = reviewState("kr:直", t0.plus(Duration.ofHours(30)))
        val h2 = home(study2, content2).settled()
        assertEquals(HomeStatus.ALL_DONE, h2.status)
        assertEquals(later.toEpochMilli(), h2.nextDueAtEpochMs); assertEquals(1, h2.nextDueCount)
    }

    @Test fun `HomeStatus 여섯 상태 판정`() = runTest(dispatcher) {
        val base = UserSettings(newCardsPerDay = 5, onboardingDone = true)
        // LOADING은 계산 전 초기값
        assertEquals(HomeStatus.LOADING, HomeUiState().status)
        // 급수 0개
        assertEquals(HomeStatus.NO_LEVEL_SELECTED, home(MemStudy(base.copy(targetLevels = emptySet())), FakeContent(words(3))).settled().status)
        // 고른 급수가 콘텐츠 밖
        assertEquals(
            HomeStatus.NO_CONTENT_FOR_LEVELS,
            home(MemStudy(base.copy(targetLevels = setOf(JlptLevel.N3))), FakeContent(words(3), metaLevels = setOf(JlptLevel.N5))).settled().status,
        )
        // 할 것 있음
        assertEquals(HomeStatus.READY, home(MemStudy(base), FakeContent(words(3))).settled().status)
        // 한도만 다 씀
        val limit = MemStudy(base)
        (1..5).forEach { limit.learn("vr:w$it") }
        val l = home(limit, FakeContent(words(8))).settled()
        assertEquals(HomeStatus.NEW_LIMIT_REACHED, l.status)
        assertNotNull(l.nextDueAtEpochMs); assert(l.nextDueCount > 0)
        // 남은 새 카드 없음
        val done = MemStudy(base)
        (1..3).forEach { done.learn("vr:w$it") }
        val d = home(done, FakeContent(words(3))).settled()
        assertEquals(HomeStatus.ALL_DONE, d.status)
        assertNotNull(d.nextDueAtEpochMs); assert(d.nextDueCount > 0)
        assertEquals(done.states.values.minOf { it.dueAtEpochMs }, d.nextDueAtEpochMs)
        // READY에서는 안내용 다음 복습 값이 비어 있다
        assertNull(home(MemStudy(base), FakeContent(words(3))).settled().nextDueAtEpochMs)
    }

    @Test fun `새 카드 5장 더는 오늘 한도만 5 늘리고 설정값은 그대로이며 다음 학습일에 되돌아간다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, onboardingDone = true))
        (1..5).forEach { study.learn("vr:w$it") }
        val vm = home(study, FakeContent(words(30)))
        vm.state.test {
            var s = expectMostRecentItem()
            assertEquals(HomeStatus.NEW_LIMIT_REACHED, s.status); assertEquals(5, s.newLimit)
            vm.moreNewCards(); s = expectMostRecentItem()
            assertEquals(10, s.newLimit); assertEquals(5, s.newTodayCount); assertEquals(HomeStatus.READY, s.status)
            assertEquals(5, study.settingsFlow.value.newCardsPerDay); assertEquals(5, study.settingsFlow.value.extraNewCards)
            clock.advance(Duration.ofDays(1)); vm.refresh(); s = expectMostRecentItem()
            assertEquals(5, s.newLimit)
        }
    }

    @Test fun `샘플 콘텐츠 첫 안내 카드는 불러온 값을 그대로 쓰고 닫으면 저장한다`() = runTest(dispatcher) {
        val notice = NoticeUi("firstNotice", "제목", "본문")
        val ok = object : AttributionRepository {
            override suspend fun load() = AttributionData("v", "sample", LinkUi("x", "u"), null, emptyList(), null, emptyList(), emptyList(), notice, emptyMap())
            override suspend fun openSourceLicenses() = emptyList<com.jlptstudy.app.data.attribution.LicenseUi>()
        }
        val study = MemStudy(UserSettings(onboardingDone = true))
        val vm = home(study, FakeContent(words(2), kind = "sample"), ok)
        vm.state.test {
            var s = expectMostRecentItem()
            assertEquals(notice, s.firstNotice); assertEquals(true, s.showFirstNotice); assertEquals(true, s.isSampleContent)
            vm.dismissFirstNotice(); s = expectMostRecentItem()
            assertEquals(false, s.showFirstNotice); assertEquals(true, study.settingsFlow.value.firstNoticeDismissed)
        }
        val release = home(MemStudy(UserSettings(onboardingDone = true)), FakeContent(words(2), kind = "release"), ok).settled()
        assertEquals(false, release.isSampleContent)
    }

    @Test fun `attribution 읽기가 실패하면 firstNotice는 null이고 홈 상태는 정상이다`() = runTest(dispatcher) {
        val s = home(MemStudy(UserSettings(onboardingDone = true)), FakeContent(words(3))).settled()
        assertNull(s.firstNotice); assertEquals(HomeStatus.READY, s.status); assertEquals(true, s.showFirstNotice)
    }

    @Test fun `급수별 진도는 vr 카드 시작 수와 번들 어휘 수다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, targetLevels = setOf(JlptLevel.N5, JlptLevel.N4), onboardingDone = true))
        study.learn("vr:w1"); study.learn("vr:w2")
        val content = FakeContent(words(6) + listOf(vocab("m1", "語", "ご", level = JlptLevel.N4)))
        val s = home(study, content).settled()
        assertEquals(listOf(LevelProgress(JlptLevel.N5, 2, 6), LevelProgress(JlptLevel.N4, 0, 1)), s.levelProgress)
    }
}
