package com.jlptstudy.app.ui.review

import app.cash.turbine.test
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.home.HomeStatus
import com.jlptstudy.app.ui.home.HomeViewModel
import com.jlptstudy.app.data.attribution.AttributionData
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** M17: 문법 카드(gr)가 복습 대기열·평가 저장·홈 수치·켬/끔 정책을 단어 카드와 같은 경로로 지난다. */
@OptIn(ExperimentalCoroutinesApi::class)
class GrammarCardViewModelTest {
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")
    private val clock = MutableClock(t0)
    private val scheduler = Fsrs6Scheduler()
    private val dispatcher = UnconfinedTestDispatcher()
    private val noAttribution = object : AttributionRepository {
        override suspend fun load(): AttributionData = throw java.io.IOException("none")
        override suspend fun openSourceLicenses() = emptyList<com.jlptstudy.app.data.attribution.LicenseUi>()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun review(study: MemStudy, content: FakeContent) = ReviewViewModel(study, content, scheduler, clock) { ZoneOffset.UTC }
    private suspend fun dueCount(study: MemStudy, content: FakeContent): Int {
        var out = 0
        HomeViewModel(study, content, noAttribution, clock) { ZoneOffset.UTC }.state.test { out = expectMostRecentItem().dueCount }
        return out
    }
    private fun ReviewViewModel.flip() { clock.advance(Duration.ofMillis(300)); reveal() }
    private fun ReviewViewModel.rateNow(r: Rating) { clock.advance(Duration.ofMillis(300)); rate(r) }
    private fun content() = FakeContent(listOf(vocab("w", "語", "ご"), grammarCard("n5-1", title = "〜てください")))

    @Test fun `gr 카드가 대기열에 나오고 평가가 저장된다`() = runTest(dispatcher) {
        val study = MemStudy(); val vm = review(study, content())
        assertEquals("vr:w", vm.state.value.card?.cardId)
        vm.flip(); vm.rateNow(Rating.EASY)
        val gr = vm.state.value.card as ReviewCardUi.Grammar
        assertEquals("gr:n5-1", gr.cardId)
        vm.flip(); assertTrue(vm.state.value.revealed)
        vm.rateNow(Rating.EASY)
        assertTrue(vm.state.value.finished)
        assertEquals(setOf("vr:w", "gr:n5-1"), study.states.keys)
        assertEquals(listOf("vr:w", "gr:n5-1"), study.logs.map { it.result.record.cardId })
    }

    @Test fun `설정을 끄면 새 gr 카드도 기한이 된 기존 gr 카드도 빠지고 다시 켜면 원래 기한대로 돌아온다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, onboardingDone = true))
        val c = content()
        val due = reviewState("gr:n5-0", t0.minusSeconds(60))
        val content = FakeContent(c.all + grammarCard("n5-0"))
        study.states["gr:n5-0"] = due
        fun queue(): List<String?> {
            val vm = review(study, content); val out = mutableListOf<String?>()
            while (vm.state.value.card != null) {
                out += vm.state.value.card?.cardId; vm.flip(); vm.rateNow(Rating.EASY)
                if (out.size > 5) break
            }
            return out
        }
        assertEquals(1, dueCount(study, content))

        study.settingsFlow.value = study.settingsFlow.value.copy(grammarCardsEnabled = false)
        assertEquals(0, dueCount(study, content))
        val off = review(study, content)
        assertEquals("vr:w", off.state.value.card?.cardId) // 새 gr 카드도 기한 지난 gr:n5-0도 없다
        off.flip(); off.rateNow(Rating.EASY)
        assertTrue(off.state.value.finished)
        assertEquals(due, study.states["gr:n5-0"]) // 기록은 그대로

        study.settingsFlow.value = study.settingsFlow.value.copy(grammarCardsEnabled = true)
        assertEquals(1, dueCount(study, content))
        assertEquals(listOf<String?>("gr:n5-0", "gr:n5-1"), queue()) // 기한 카드 먼저, 새 카드 뒤
    }

    @Test fun `끄면 EMPTY 홈 상태도 ALL_DONE이다`() = runTest(dispatcher) {
        val study = MemStudy(UserSettings(newCardsPerDay = 5, onboardingDone = true, vocabRecognitionEnabled = false, grammarCardsEnabled = false, kanjiCardsEnabled = true))
        val vm = review(study, FakeContent(listOf(grammarCard("n5-1"))))
        assertEquals(ReviewStatus.EMPTY, vm.state.value.status); assertEquals(HomeStatus.ALL_DONE, vm.state.value.emptyReason)
    }

    @Test fun `문법 카드 앞면의 후리가나 토글은 body_furigana를 바꾼다`() = runTest(dispatcher) {
        val study = MemStudy(); val vm = review(study, FakeContent(listOf(grammarCard("n5-1"))))
        assertTrue(vm.state.value.card is ReviewCardUi.Grammar && !vm.state.value.revealed)
        val before = study.settingsFlow.value
        vm.toggleFurigana()
        val after = study.settingsFlow.value
        assertEquals(!before.bodyFurigana, after.bodyFurigana); assertEquals(before.frontFurigana, after.frontFurigana)
    }
}
