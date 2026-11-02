package com.jlptstudy.app.ui.detail

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.review.kanji
import com.jlptstudy.app.ui.review.meaning
import com.jlptstudy.app.ui.review.reviewState
import com.jlptstudy.app.ui.review.vocab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val word = vocab("a", "食べる", "たべる", listOf(meaning("먹다")), pos = listOf("v1", "vt", "zzz"))
    private val content = FakeContent(listOf(word, kanji("直")))

    @Test fun `단어 상세는 품사를 한국어 이름표로, 모르는 코드는 그대로 보인다`() = runTest(dispatcher) {
        val vm = VocabDetailViewModel("a", content, MemStudy())
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        runCurrent()
        val s = vm.state.value
        assertEquals(DetailStatus.LOADED, s.status)
        assertEquals(listOf("1단 동사", "타동사", "zzz"), s.pos)
        assertEquals(JlptLevel.N5, s.level)
        assertEquals(listOf("食"), s.kanji)
        assertEquals("食", s.ruby.first().base)
        assertEquals(listOf(CardStateSummary(CardKind.VR, CardProgress.NEW)), s.cardStates) // 회상 카드는 꺼져 있다
    }

    @Test fun `카드 상태 요약은 복습 단계면 기한을 담는다`() = runTest(dispatcher) {
        val study = MemStudy()
        val due = Instant.parse("2026-11-10T00:00:00Z")
        study.states["vr:a"] = reviewState("vr:a", due)
        val vm = VocabDetailViewModel("a", content, study)
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        runCurrent()
        assertEquals(CardStateSummary(CardKind.VR, CardProgress.REVIEW, due.toEpochMilli()), vm.state.value.cardStates.single())
    }

    @Test fun `없는 ID와 retired ID의 상세는 RETIRED이다`() = runTest(dispatcher) {
        content.retired += "vr:a"
        content.retired += "kr:直"
        for (id in listOf("a", "nope")) {
            val vm = VocabDetailViewModel(id, content, MemStudy())
            backgroundScope.launch(dispatcher) { vm.state.collect {} }
            runCurrent()
            assertEquals(DetailStatus.RETIRED, vm.state.value.status)
        }
        for (lit in listOf("直", "骨")) {
            val vm = KanjiDetailViewModel(lit, content)
            backgroundScope.launch(dispatcher) { vm.state.collect {} }
            runCurrent()
            assertEquals(DetailStatus.RETIRED, vm.state.value.status)
        }
    }

    @Test fun `한자 상세는 필순 데이터가 없으면 strokes가 null이다`() = runTest(dispatcher) {
        val vm = KanjiDetailViewModel("直", content)
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        runCurrent()
        val s = vm.state.value
        assertEquals(DetailStatus.LOADED, s.status)
        assertNull(s.strokes)
        assertEquals(9, s.strokeCount)
        assertEquals(listOf("직"), s.koreanReadings)
        assertEquals(1, s.words.size)
    }
}
