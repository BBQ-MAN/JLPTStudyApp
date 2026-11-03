package com.jlptstudy.app.ui.grammar

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomGrammarRepository
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.ui.detail.DetailStatus
import com.jlptstudy.app.ui.review.MemStudy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GrammarViewModelTest {
    private lateinit var db: ContentDatabase

    @Before fun setUp() { Dispatchers.setMain(Dispatchers.Unconfined); db = newContentDb() }
    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private val repo get() = RoomGrammarRepository(db.contentDao())
    private fun study(vararg levels: JlptLevel) = MemStudy(UserSettings(onboardingDone = true, targetLevels = levels.toSet()))

    private fun list(vm: GrammarListViewModel, until: (GrammarListUiState) -> Boolean = { it.status != GrammarListStatus.LOADING }) =
        runBlocking { withTimeout(5_000) { vm.state.first(until) } }

    private fun detail(id: String): GrammarDetailUiState = runBlocking {
        val vm = GrammarDetailViewModel(id, repo, MemStudy())
        withTimeout(5_000) { vm.state.first { it.status != DetailStatus.LOADING } }
    }

    @Test fun `번들 에셋의 문법 목록은 빈 집합으로 불러도 예외 없이 동작한다`() = runBlocking {
        val asset = ContentDatabase.open(RuntimeEnvironment.getApplication())
        try {
            val r = RoomGrammarRepository(asset.contentDao())
            r.list(emptySet()); r.levels(); assertNull(r.detail("없는-id"))
        } finally { asset.close() }
    }

    @Test fun `문법 0행이면 EMPTY이고 칩은 없다`() {
        val s = list(GrammarListViewModel(repo, study(JlptLevel.N5)))
        assertEquals(GrammarListStatus.EMPTY, s.status)
        assertTrue(s.availableLevels.isEmpty())
    }

    @Test fun `급수 필터 - 학습 급수 교집합이 처음 값이고 칩과 지우기로 바뀐다`() {
        db.grammar("gr-n5-0002", 5, 4); db.grammar("gr-n5-0001", 5, 2); db.grammar("gr-n4-0001", 4, 0)
        val vm = GrammarListViewModel(repo, study(JlptLevel.N5, JlptLevel.N1))
        var s = list(vm)
        assertEquals(setOf(JlptLevel.N5), s.levels)
        assertEquals(setOf(JlptLevel.N5, JlptLevel.N4), s.availableLevels)
        assertEquals(listOf("gr-n5-0001", "gr-n5-0002"), s.rows.map { it.id }) // sort_key 순
        vm.toggleLevel(JlptLevel.N4)
        s = list(vm) { it.levels.size == 2 }
        assertEquals(listOf("gr-n5-0001", "gr-n5-0002", "gr-n4-0001"), s.rows.map { it.id }) // 쉬운 급수(N5)부터
    }

    @Test fun `교집합이 비면 문법이 있는 급수 전부이고 목록은 쉬운 급수(N5)부터다`() {
        db.grammar("gr-n5-0001", 5, 0); db.grammar("gr-n4-0001", 4, 0)
        val vm = GrammarListViewModel(repo, study(JlptLevel.N1))
        val s = list(vm)
        assertTrue(s.levels.isEmpty())
        assertEquals(listOf(JlptLevel.N5, JlptLevel.N4), s.rows.map { it.level })
        vm.toggleLevel(JlptLevel.N4)
        assertEquals(listOf("gr-n4-0001"), list(vm) { it.levels.isNotEmpty() }.rows.map { it.id })
        vm.clearLevels()
        assertEquals(2, list(vm) { it.levels.isEmpty() && it.rows.size == 2 }.rows.size)
    }

    @Test fun `JSON이 깨진 행은 목록에서 빠지고 앱은 죽지 않는다`() {
        db.grammar("gr-n5-0001", sortKey = 0)
        db.grammar("gr-n5-0002", sortKey = 1, connections = "{깨짐")
        db.grammar("gr-n5-0003", sortKey = 2, contrasts = "not json")
        db.grammar("gr-n5-0004", sortKey = 3, contrasts = """[{"grammarId":"gr-n5-0001","pair":[{"ja":"a","ko":"b"}]}]""") // pair가 1개
        db.grammar("gr-n5-0005", sortKey = 4)
        db.retire("gr-n5-0005")
        assertEquals(listOf("gr-n5-0001"), list(GrammarListViewModel(repo, study(JlptLevel.N5))).rows.map { it.id })
    }

    @Test fun `없는 ID와 retired ID와 깨진 JSON의 상세는 RETIRED이다`() {
        db.grammar("gr-n5-0001", connections = "[깨짐"); db.grammar("gr-n5-0002", contrasts = "{}"); db.grammar("gr-n5-0003"); db.retire("gr-n5-0003")
        for (id in listOf("nope", "gr-n5-0001", "gr-n5-0002", "gr-n5-0003")) {
            val s = detail(id)
            assertEquals(id, DetailStatus.RETIRED, s.status)
            assertNull(s.detail)
        }
    }

    @Test fun `상세의 출처는 grammar 행 ko 열과 같고 칩 문구가 붙는다`() {
        db.grammar("gr-n5-0001", koSource = "authored", koMethod = "llm:test-model@2026-11-03", status = "reviewed", reviewedAt = "2026-11-03", reviewer = "human")
        val p = detail("gr-n5-0001").detail!!.provenance
        assertEquals("authored", p.koSource); assertEquals("llm:test-model@2026-11-03", p.koMethod)
        assertEquals("reviewed", p.reviewStatus); assertEquals("2026-11-03", p.reviewedAt); assertEquals("human", p.reviewerKind)
        assertEquals("시험용 첫 문단", p.textKo)
        assertEquals("AI 작성 · 사람 검수 완료", ProvenanceLabel.of(p))
    }

    @Test fun `needs_review 1이면 검토 중 표시이고 0이면 없다`() {
        db.grammar("gr-n5-0001", needsReview = true); db.grammar("gr-n5-0002", needsReview = false)
        assertTrue(detail("gr-n5-0001").detail!!.needsReview)
        assertFalse(detail("gr-n5-0002").detail!!.needsReview)
    }

    @Test fun `접속 표는 세 값으로 나뉘고 예문은 밑줄 구간을 가진다`() {
        db.grammar("gr-n5-0001")
        val d = detail("gr-n5-0001").detail!!
        assertEquals(listOf("동사", "い형용사"), d.connections.map { it.posKo })
        assertEquals(listOf(RubySegment("て形")), d.connections[0].form)
        assertEquals(listOf(RubySegment("食", "た"), RubySegment("べて")), d.connections[0].example)
        assertEquals(3, d.examples.size)
        assertEquals(listOf("시험 번역 1", "시험 번역 2", "시험 번역 3"), d.examples.map { it.ko })
        assertTrue(d.examples[0].ja.any { it.emphasis })
        assertTrue(d.contrasts.isEmpty()) // []이면 비슷한 문법 절이 없다
    }

    @Test fun `비슷한 문법은 grammarId와 두 예문으로 채워지고 번들에 없는 항목만 빠진다`() {
        db.grammar("gr-n5-0002", title = "〜なさい")
        val both = """[{"grammarId":"gr-n5-0002","pair":[{"ja":"가","ko":"ㄱ"},{"ja":"나","ko":"ㄴ"}]},{"grammarId":"gr-n5-9999","pair":[{"ja":"다","ko":"ㄷ"},{"ja":"라","ko":"ㄹ"}]}]"""
        db.grammar("gr-n5-0001", contrasts = both)
        val c = detail("gr-n5-0001").detail!!.contrasts
        assertEquals(1, c.size)
        assertEquals("gr-n5-0002", c[0].grammarId); assertEquals("〜なさい", c[0].title)
        assertEquals(listOf(RubySegment("가")), c[0].mine.ja); assertEquals("ㄱ", c[0].mine.ko)
        assertEquals(listOf(RubySegment("나")), c[0].other.ja); assertEquals("ㄴ", c[0].other.ko)
        db.retire("gr-n5-0002")
        assertTrue(detail("gr-n5-0001").detail!!.contrasts.isEmpty())
    }

    @Test fun `본문 후리가나 설정을 따른다`() = runBlocking {
        db.grammar("gr-n5-0001")
        val vm = GrammarDetailViewModel("gr-n5-0001", repo, MemStudy(UserSettings(onboardingDone = true, bodyFurigana = false)))
        assertFalse(withTimeout(5_000) { vm.state.first { it.status == DetailStatus.LOADED } }.showRuby)
        assertNotNull(vm)
    }
}
