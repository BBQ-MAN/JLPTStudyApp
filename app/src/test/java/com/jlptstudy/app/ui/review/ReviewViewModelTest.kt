package com.jlptstudy.app.ui.review

import app.cash.turbine.test
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.home.HomeStatus
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val scheduler = Fsrs6Scheduler()
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")
    private val clock = MutableClock(t0)
    private val guard = Duration.ofMillis(300)
    private val rule = StudyDayRule(ZoneOffset.UTC, 240)
    private val cfg = SrsConfig(studyDay = rule)
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun words(n: Int) = (1..n).map { vocab("w$it", "語$it", "ご$it") }
    private fun vm(study: MemStudy, content: FakeContent) = ReviewViewModel(study, content, scheduler, clock) { ZoneOffset.UTC }
    private fun ReviewViewModel.flip() { clock.advance(guard); reveal() }
    private fun ReviewViewModel.rateNow(r: Rating) { clock.advance(guard); rate(r) }

    @Test fun `뒤집기 평가 다음 카드 그리고 마지막 카드 뒤 finished`() = runTest(dispatcher) {
        val study = MemStudy(); val vm = vm(study, FakeContent(words(2)))
        vm.state.test {
            var s = expectMostRecentItem()
            assertEquals(ReviewStatus.READY, s.status)
            assertEquals("vr:w1", s.card?.cardId); assertFalse(s.revealed); assertEquals(2, s.remainingNew)
            vm.flip(); s = expectMostRecentItem()
            assertTrue(s.revealed)
            vm.rateNow(Rating.EASY); s = expectMostRecentItem()
            assertEquals("vr:w2", s.card?.cardId); assertFalse(s.revealed); assertEquals(1, s.newInSession); assertTrue(s.canUndo)
            vm.flip(); vm.rateNow(Rating.EASY); s = expectMostRecentItem()
            assertTrue(s.finished); assertNull(s.card); assertEquals(2, s.newInSession); assertEquals(0, s.reviewedInSession)
            assertEquals(2, study.logs.size)
        }
    }

    @Test fun `대기열이 없으면 EMPTY 이유 ALL_DONE`() = runTest(dispatcher) {
        val s = vm(MemStudy(), FakeContent(emptyList())).state.value
        assertEquals(ReviewStatus.EMPTY, s.status); assertEquals(HomeStatus.ALL_DONE, s.emptyReason)
    }

    @Test fun `한도를 다 쓰고 새 카드가 남았으면 EMPTY 이유 NEW_LIMIT_REACHED`() = runTest(dispatcher) {
        val study = MemStudy(); val content = FakeContent(words(8))
        val first = vm(study, content)
        repeat(5) { first.flip(); first.rateNow(Rating.EASY) }
        assertTrue(first.state.value.finished)
        val s = vm(study, content).state.value
        assertEquals(ReviewStatus.EMPTY, s.status); assertEquals(HomeStatus.NEW_LIMIT_REACHED, s.emptyReason)
    }

    @Test fun `새 카드 수는 설정 한도에 오늘 추가분을 더한 값에서 오늘 도입 수를 뺀 값을 넘지 않는다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, extraNewCards = 5, extraNewCardsStudyDay = rule.studyDayOf(t0)))
        val vm = vm(study, FakeContent(words(30)))
        var rated = 0
        while (!vm.state.value.finished) { vm.flip(); vm.rateNow(Rating.EASY); rated++; assertTrue(rated <= 10) }
        assertEquals(10, rated)
        // 어제의 추가분은 쓰이지 않는다
        val old = MemStudy(UserSettings(newCardsPerDay = 5, extraNewCards = 5, extraNewCardsStudyDay = rule.studyDayOf(t0) - 1))
        assertEquals(5, vm(old, FakeContent(words(30))).state.value.remainingNew)
    }

    @Test fun `finished일 때 newCardsLeftAfterSession은 시작하지 않은 새 카드 수이고 더 학습하면 한도가 5 늘고 새 세션이 시작된다`() = runTest(dispatcher) {
        val study = MemStudy(); val vm = vm(study, FakeContent(words(8)))
        repeat(5) { vm.flip(); vm.rateNow(Rating.EASY) }
        var s = vm.state.value
        assertTrue(s.finished); assertEquals(3, s.newCardsLeftAfterSession)
        vm.studyMore()
        assertEquals(5, study.settingsFlow.value.extraNewCards)
        assertEquals(rule.studyDayOf(t0), study.settingsFlow.value.extraNewCardsStudyDay)
        s = vm.state.value
        assertFalse(s.finished); assertEquals("vr:w6", s.card?.cardId); assertEquals(3, s.remainingNew); assertEquals(0, s.newInSession)
    }

    @Test fun `남은 새 카드가 없으면 newCardsLeftAfterSession은 0`() = runTest(dispatcher) {
        val vm = vm(MemStudy(), FakeContent(words(2)))
        repeat(2) { vm.flip(); vm.rateNow(Rating.EASY) }
        assertTrue(vm.state.value.finished); assertEquals(0, vm.state.value.newCardsLeftAfterSession)
    }

    @Test fun `새 카드에서 GOOD이면 다음은 다른 새 카드이고 10분 뒤에 그 카드가 다시 나온다`() = runTest(dispatcher) {
        val vm = vm(MemStudy(), FakeContent(words(2)))
        vm.flip(); vm.rateNow(Rating.GOOD)
        assertEquals("vr:w2", vm.state.value.card?.cardId)
        vm.flip(); vm.rateNow(Rating.EASY)
        assertEquals("vr:w1", vm.state.value.card?.cardId) // 남은 카드가 그것뿐이면 learn-ahead로 앞당긴다
    }

    @Test fun `재시도가 또 실패하면 saveFailed가 다시 null을 거쳐 설정된다`() = runTest(dispatcher) {
        val study = MemStudy().apply { failApply = IllegalStateException("disk") }
        val vm = vm(study, FakeContent(words(2)))
        vm.flip(); vm.rateNow(Rating.EASY)
        study.failApply = IllegalStateException("disk")
        vm.state.test {
            assertEquals(Rating.EASY, awaitItem().saveFailed)
            vm.retry()
            assertNull(awaitItem().saveFailed)
            assertEquals(Rating.EASY, awaitItem().saveFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `AGAIN으로 재학습에 들어간 카드는 remaining에 포함되고 learnAhead 밖이면 10분 뒤 새 세션에 다시 나온다`() = runTest(dispatcher) {
        val seed = reviewState("vr:w1", t0.minus(Duration.ofHours(1)))
        val content = FakeContent(words(1))
        // learnAhead 20분: 재학습 10분 카드가 바로 다시 나온다
        val a = MemStudy().apply { states[seed.cardId] = seed }
        val va = vm(a, content); va.flip(); va.rateNow(Rating.AGAIN)
        assertEquals("vr:w1", va.state.value.card?.cardId); assertEquals(1, va.state.value.remainingReview)
        // learnAhead 5분: 세션이 끝나고, 시계를 10분 옮긴 새 세션에 다시 나온다
        val b = MemStudy(UserSettings(newCardsPerDay = 5, learnAheadMinutes = 5)).apply { states[seed.cardId] = seed }
        val vb = vm(b, content); vb.flip(); vb.rateNow(Rating.AGAIN)
        assertTrue(vb.state.value.finished)
        clock.advance(Duration.ofMinutes(10))
        val again = vm(b, content).state.value
        assertEquals("vr:w1", again.card?.cardId); assertEquals(1, again.remainingReview)
    }

    @Test fun `학습일 경계를 넘으면 다음 평가 뒤 새 카드 한도가 새 학습일 기준으로 다시 계산된다`() = runTest(dispatcher) {
        clock.now = Instant.parse("2026-11-02T03:50:00Z") // 경계(04:00) 직전
        val vm = vm(MemStudy(), FakeContent(words(8)))
        assertEquals(5, vm.state.value.remainingNew)
        vm.flip(); vm.rateNow(Rating.EASY)
        assertEquals(4, vm.state.value.remainingNew) // 같은 학습일: 한도 5 - 도입 1
        clock.now = Instant.parse("2026-11-02T04:01:00Z")
        vm.flip(); vm.rateNow(Rating.EASY)
        // 새 학습일 기준: 오늘 도입 1(방금 카드)뿐이므로 한도 5 - 1 = 4가 남는다(옛 학습일 기준이면 3)
        assertEquals(4, vm.state.value.remainingNew)
    }

    @Test fun `버튼별 previews는 Scheduler preview 결과와 같다`() = runTest(dispatcher) {
        val vm = vm(MemStudy(), FakeContent(words(1)))
        val shownAt = clock.instant()
        assertEquals(scheduler.preview(CardState.new("vr:w1", shownAt, scheduler.version), shownAt, cfg), vm.state.value.previews)
        vm.flip()
        assertEquals(scheduler.preview(CardState.new("vr:w1", shownAt, scheduler.version), clock.instant(), cfg), vm.state.value.previews)
        assertEquals(Rating.entries.toSet(), vm.state.value.previews.keys)
    }

    @Test fun `평가 직후 300 ms 안의 reveal은 무시되고 뒤에는 동작한다`() = runTest(dispatcher) {
        val vm = vm(MemStudy(), FakeContent(words(2)))
        vm.flip(); vm.rateNow(Rating.EASY)
        vm.reveal(); assertFalse(vm.state.value.revealed)
        clock.advance(Duration.ofMillis(299)); vm.reveal(); assertFalse(vm.state.value.revealed)
        clock.advance(Duration.ofMillis(1)); vm.reveal(); assertTrue(vm.state.value.revealed)
    }

    @Test fun `뒤집기 직후 300 ms 안의 rate는 무시되고 뒤에는 저장된다`() = runTest(dispatcher) {
        val study = MemStudy(); val vm = vm(study, FakeContent(words(2)))
        vm.flip()
        vm.rate(Rating.EASY); assertEquals(0, study.logs.size)
        clock.advance(Duration.ofMillis(299)); vm.rate(Rating.EASY); assertEquals(0, study.logs.size)
        clock.advance(Duration.ofMillis(1)); vm.rate(Rating.EASY); assertEquals(1, study.logs.size)
    }

    @Test fun `저장 실패면 같은 카드가 남고 재시도하면 처음 계산한 결과가 저장되고 다음 카드로 간다`() = runTest(dispatcher) {
        val study = MemStudy().apply { failApply = IllegalStateException("disk") }
        val vm = vm(study, FakeContent(words(2)))
        vm.flip(); vm.rateNow(Rating.EASY)
        val expected = scheduler.schedule(CardState.new("vr:w1", t0, scheduler.version), Rating.EASY, clock.instant(), cfg)
        var s = vm.state.value
        assertEquals("vr:w1", s.card?.cardId); assertEquals(Rating.EASY, s.saveFailed); assertTrue(s.revealed)
        assertEquals(0, study.logs.size)
        clock.advance(Duration.ofSeconds(5)) // 시간이 흘러도 처음 계산한 결과를 저장한다
        vm.retry()
        assertEquals(listOf(expected), study.logs.map { it.result })
        s = vm.state.value
        assertNull(s.saveFailed); assertEquals("vr:w2", s.card?.cardId); assertFalse(s.revealed)
    }

    @Test fun `저장이 300 ms를 넘으면 saving이 켜지고 끝나면 꺼진다`() = runTest(dispatcher) {
        val study = MemStudy().apply { applyGate = CompletableDeferred() }
        val vm = vm(study, FakeContent(words(2)))
        vm.flip(); vm.rateNow(Rating.EASY)
        assertFalse(vm.state.value.saving)
        advanceTimeBy(301); assertTrue(vm.state.value.saving)
        study.applyGate!!.complete(Unit)
        assertFalse(vm.state.value.saving); assertEquals("vr:w2", vm.state.value.card?.cardId)
    }

    @Test fun `되돌리기 후 그 카드가 바로 다음 카드 뒷면으로 나오고 저장 상태가 평가 전과 같다`() = runTest(dispatcher) {
        val seed = reviewState("vr:w2", t0.minus(Duration.ofHours(1)))
        val study = MemStudy().apply { states[seed.cardId] = seed }
        val vm = vm(study, FakeContent(words(3)))
        assertEquals("vr:w2", vm.state.value.card?.cardId) // 기한 카드가 새 카드보다 앞
        vm.flip(); vm.rateNow(Rating.GOOD)
        assertEquals("vr:w1", vm.state.value.card?.cardId)
        vm.flip(); vm.rateNow(Rating.EASY) // 새 카드도 평가
        vm.undo()
        var s = vm.state.value
        assertEquals("vr:w1", s.card?.cardId); assertTrue(s.revealed); assertFalse(study.states.containsKey("vr:w1"))
        vm.undo()
        s = vm.state.value
        assertEquals("vr:w2", s.card?.cardId); assertTrue(s.revealed); assertFalse(s.canUndo)
        assertEquals(seed, study.states["vr:w2"]); assertTrue(study.logs.all { it.undone })
        assertEquals(Rating.entries.toSet(), s.previews.keys)
        vm.undo() // 되돌릴 평가가 없으면 아무 일도 없다
        assertEquals("vr:w2", vm.state.value.card?.cardId)
    }

    @Test fun `콘텐츠에 없거나 retired인 카드는 대기열에서 빠지고 학습 기록은 남는다`() = runTest(dispatcher) {
        val ghost = reviewState("vr:ghost", t0.minus(Duration.ofHours(2)))
        val gone = reviewState("vr:w2", t0.minus(Duration.ofHours(1)))
        val study = MemStudy().apply { states[ghost.cardId] = ghost; states[gone.cardId] = gone }
        val content = FakeContent(words(2), retired = mutableSetOf("vr:w2"))
        val vm = vm(study, content)
        val s = vm.state.value
        assertEquals("vr:w1", s.card?.cardId); assertEquals(1, s.remainingNew); assertEquals(0, s.remainingReview)
        assertEquals(setOf("vr:ghost", "vr:w2"), study.states.keys)
        assertNotNull(study.states["vr:ghost"])
    }
}
