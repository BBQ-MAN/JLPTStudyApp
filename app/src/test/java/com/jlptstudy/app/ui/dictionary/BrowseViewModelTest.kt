package com.jlptstudy.app.ui.dictionary

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.review.kanji
import com.jlptstudy.app.ui.review.meaning
import com.jlptstudy.app.ui.review.vocab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // 시험 픽스처(앱 콘텐츠가 아니다)
    private val cards = listOf(
        vocab("a", "食べる", "たべる", listOf(meaning("먹다")), JlptLevel.N5),
        vocab("b", "飲む", "のむ", listOf(meaning("마시다"), meaning("삼키다")), JlptLevel.N4),
        vocab("c", "歩く", "あるく", listOf(meaning("걷다")), JlptLevel.N3),
        kanji("直", JlptLevel.N5), kanji("骨", JlptLevel.N4),
    )

    private fun vm(settings: UserSettings = UserSettings(targetLevels = setOf(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3)), metaLevels: Set<JlptLevel> = JlptLevel.entries.toSet()) =
        BrowseViewModel(FakeContent(cards, metaLevels = metaLevels), MemStudy(settings))

    /** 구독을 유지한 채 가상 시간을 돌린다. */
    private fun TestScope.observe(vm: BrowseViewModel, body: suspend (BrowseViewModel) -> Unit) {
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        runCurrent()
        launch { body(vm) }
        runCurrent()
    }

    @Test fun `처음 급수는 학습 급수와 번들 급수의 교집합이다`() = runTest(dispatcher) {
        val v = vm(UserSettings(targetLevels = setOf(JlptLevel.N5, JlptLevel.N1)), metaLevels = setOf(JlptLevel.N5, JlptLevel.N4))
        observe(v) {}
        assertEquals(setOf(JlptLevel.N5), v.state.value.levels)
        assertEquals(setOf(JlptLevel.N5, JlptLevel.N4), v.state.value.availableLevels)
    }

    @Test fun `교집합이 비면 빈 집합으로 번들 급수 전부를 보인다`() = runTest(dispatcher) {
        val v = vm(UserSettings(targetLevels = setOf(JlptLevel.N1)), metaLevels = setOf(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3))
        observe(v) {}
        assertEquals(emptySet<JlptLevel>(), v.state.value.levels)
        assertEquals(3, v.state.value.items.size)
    }

    @Test fun `급수 여러 개를 켜면 그 급수의 항목만 나온다`() = runTest(dispatcher) {
        val v = vm(UserSettings(targetLevels = setOf(JlptLevel.N5)))
        observe(v) {}
        assertEquals(listOf("a"), v.state.value.items.map { it.id })
        v.toggleLevel(JlptLevel.N3)
        runCurrent()
        assertEquals(setOf(JlptLevel.N5, JlptLevel.N3), v.state.value.levels)
        assertEquals(listOf("a", "c"), v.state.value.items.map { it.id })
        v.toggleLevel(JlptLevel.N5)
        runCurrent()
        assertEquals(listOf("c"), v.state.value.items.map { it.id })
    }

    @Test fun `한국어 뜻으로 검색하면 text_ko가 맞는 단어가 나온다 300ms 뒤에`() = runTest(dispatcher) {
        val v = vm()
        observe(v) {}
        v.setQuery("삼키")
        advanceTimeBy(299); runCurrent()
        assertEquals(3, v.state.value.items.size) // 아직 적용 전, 입력값은 바로 보인다
        assertEquals("삼키", v.state.value.query)
        advanceTimeBy(2); runCurrent()
        assertEquals(BrowseStatus.RESULTS, v.state.value.status)
        assertEquals(listOf("b"), v.state.value.items.map { it.id })
    }

    @Test fun `검색어 있음 결과 0은 NO_RESULT 검색어 없음 결과 0은 NO_CONTENT_FOR_FILTER`() = runTest(dispatcher) {
        val v = vm(UserSettings(targetLevels = setOf(JlptLevel.N5)))
        observe(v) {}
        v.setQuery("없는말")
        advanceTimeBy(301); runCurrent()
        assertEquals(BrowseStatus.NO_RESULT, v.state.value.status)
        v.setQuery("") // 지우면 바로 적용
        runCurrent()
        assertEquals(BrowseStatus.RESULTS, v.state.value.status)
        v.setTab(BrowseTab.KANJI); v.toggleLevel(JlptLevel.N5); v.toggleLevel(JlptLevel.N2)
        runCurrent()
        assertEquals(BrowseStatus.NO_CONTENT_FOR_FILTER, v.state.value.status)
        assertEquals("", v.state.value.query)
    }

    @Test fun `한자 탭은 한자 목록을 보인다`() = runTest(dispatcher) {
        val v = vm()
        observe(v) {}
        v.setTab(BrowseTab.KANJI)
        runCurrent()
        assertEquals(listOf("直", "骨"), v.state.value.items.map { it.id }.sortedBy { if (it == "直") 0 else 1 })
        assertEquals("직", (v.state.value.items.first { it.id == "直" } as BrowseItem.Kanji).koreanReading)
    }
}
