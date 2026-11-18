package com.jlptstudy.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.grammar.grammar
import com.jlptstudy.app.ui.grammar.newContentDb
import com.jlptstudy.app.ui.grammar.retire
import com.jlptstudy.app.ui.review.ReviewCardUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 인메모리 ContentDatabase(합성 행)로 gr 카드의 도입 순서·내용 조회를 확인한다(architecture.md 8.2·9.1). */
@RunWith(RobolectricTestRunner::class)
class GrammarCardContentTest {
    private lateinit var db: ContentDatabase

    @Before fun setUp() { db = newContentDb() }
    @After fun tearDown() = db.close()

    private fun vocab(id: String, level: Int, sortKey: Int) = db.openHelper.writableDatabase.insert("vocab", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("id", id); put("written", "語"); put("reading", "ご"); put("level", level); put("sort_key", sortKey); put("pos", "n")
        put("source_id", "S-01"); put("level_source_id", "S-02")
    })
    private fun kanji(literal: String, level: Int, sortKey: Int) = db.openHelper.writableDatabase.insert("kanji", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("literal", literal); put("level", level); put("stroke_count", 3); put("on_readings", "[]"); put("kun_readings", "[]")
        put("korean_readings", "[]"); put("sort_key", sortKey); put("source_id", "S-03")
    })
    private val repo get() = RoomContentRepository(db.contentDao())

    @Test fun `같은 level과 sort_key의 vr kr gr 후보는 vr kr gr 순이다`() = runBlocking {
        vocab("v1", 5, 3); vocab("v0", 5, 0); kanji("日", 5, 3); db.grammar("gr-n5-0001", level = 5, sortKey = 3)
        db.grammar("gr-n4-0001", level = 4, sortKey = 0)
        val all = setOf(CardKind.VR, CardKind.KR, CardKind.GR)
        assertEquals(listOf("vr:v0", "vr:v1", "kr:日", "gr:gr-n5-0001", "gr:gr-n4-0001"), repo.newCardOrder(setOf(JlptLevel.N5, JlptLevel.N4), all).map { it.cardId })
        assertEquals(listOf("vr:v0", "vr:v1", "kr:日"), repo.newCardOrder(setOf(JlptLevel.N5), setOf(CardKind.VR, CardKind.KR)).map { it.cardId })
        db.retire("gr-n5-0001")
        assertEquals(emptyList<String>(), repo.newCardOrder(setOf(JlptLevel.N5), setOf(CardKind.GR)).map { it.cardId })
    }

    @Test fun `gr 카드의 example과 exampleKo는 ord 0 행이고 summaryKo와 provenance는 grammar 행에서 온다`() = runBlocking {
        db.grammar(
            "gr-n5-0002", level = 5, title = "〜ませんか", meaning = "시험 대응", explanation = "첫 문단 〔시험〕\n\n둘째 문단",
            koSource = "authored", koMethod = "llm:test-model@2026-11-03", status = "reviewed", reviewedAt = "2026-11-03", reviewer = "human", needsReview = true,
            examples = listOf("一番目[[{行|い}きませんか]]" to "첫째 번역", "二番目[[です]]" to "둘째 번역"),
        )
        val card = repo.cardContents(listOf("gr:gr-n5-0002", "gr:gr-n5-9999"))["gr:gr-n5-0002"] as ReviewCardUi.Grammar
        assertEquals("〜ませんか", card.title)
        assertEquals("一番目行きませんか", card.example.joinToString("") { it.base })
        assertEquals(listOf(false, true, true), card.example.map { it.emphasis }) // 一番目 | 行(い) | きませんか
        assertEquals("첫째 번역", card.exampleKo)
        assertEquals("시험 대응", card.meaningKo)
        assertEquals("첫 문단 〔시험〕", card.summaryKo)
        assertEquals(JlptLevel.N5, card.level)
        assertTrue(card.needsReview)
        val p = card.provenance
        assertEquals(listOf("authored", "llm:test-model@2026-11-03", "reviewed", "2026-11-03", "human"), listOf(p.koSource, p.koMethod, p.reviewStatus, p.reviewedAt, p.reviewerKind))
        assertTrue(ProvenanceLabel.of(p).isNotEmpty())
        assertEquals(mapOf("gr:gr-n5-0002" to JlptLevel.N5), repo.cardLevels(listOf("gr:gr-n5-0002", "gr:gr-n5-9999")))
        db.retire("gr-n5-0002")
        assertTrue(repo.cardContents(listOf("gr:gr-n5-0002")).isEmpty())
        assertTrue(repo.cardLevels(listOf("gr:gr-n5-0002")).isEmpty())
    }
}
