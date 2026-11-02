package com.jlptstudy.app.data

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.review.reviewState
import com.jlptstudy.app.ui.review.vocab
import com.jlptstudy.app.ui.review.kanji
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class SessionCardsTest {
    private val t = Instant.parse("2026-11-02T10:00:00Z")

    @Test fun `꺼진 종류 대상 급수 밖 콘텐츠에 없는 retired 카드를 빼고 다시 켜면 원래 dueAt으로 돌아온다`() = runBlocking {
        val study = MemStudy()
        val content = FakeContent(
            listOf(
                vocab("a", "語", "ご"), vocab("b", "語", "ご", direction = com.jlptstudy.app.ui.review.VocabDirection.KO_TO_JA),
                vocab("c", "語", "ご", level = JlptLevel.N4), vocab("r", "語", "ご"), kanji("直"),
            ),
            retired = mutableSetOf("vr:r"),
        )
        val dues = mapOf("vr:a" to 1L, "vp:b" to 2L, "vr:c" to 3L, "vr:r" to 4L, "vr:ghost" to 5L, "kr:直" to 6L)
        dues.forEach { (id, d) -> study.states[id] = reviewState(id, t.plusSeconds(d)) }
        val sc = SessionCards(study, content)

        val ids = { kinds: Set<CardKind>, levels: Set<JlptLevel> -> runBlocking { sc.load(kinds, levels) }.map { it.cardId }.toSet() }
        assertEquals(setOf("vr:a", "kr:直"), ids(setOf(CardKind.VR, CardKind.KR), setOf(JlptLevel.N5))) // vp 꺼짐, N4 밖, retired, 없음
        // 다시 켜면 원래 dueAt 그대로
        val back = sc.load(setOf(CardKind.VR, CardKind.VP, CardKind.KR), setOf(JlptLevel.N5, JlptLevel.N4))
        assertEquals(setOf("vr:a", "vp:b", "vr:c", "kr:直"), back.map { it.cardId }.toSet())
        back.forEach { assertEquals(reviewState(it.cardId, t.plusSeconds(dues.getValue(it.cardId))), it) }
        assertEquals(6, study.states.size) // 기록은 지워지지 않는다
        assertTrue(sc.load(emptySet(), setOf(JlptLevel.N5)).isEmpty())
        assertTrue(sc.load(setOf(CardKind.VR), emptySet()).isEmpty())
    }

    @Test fun `statesOfKinds 호출은 SessionCards 밖에 없고 평가 이름표는 RatingLabels 한 곳에만 있다`() {
        val src = File(System.getProperty("app.dir")!!, "src/main/java")
        val files = src.walkTopDown().filter { it.extension == "kt" }.toList()
        val callers = files.filter { f -> f.readLines().any { "statesOfKinds(" in it && "fun statesOfKinds" !in it } }.map { it.name }
        assertEquals(listOf("SessionCards.kt"), callers.sorted().filter { it != "StudyRepository.kt" })
        val label = files.filter { "알맞음" in it.readText() }.map { it.name }
        assertEquals(listOf("RatingLabels.kt"), label)
        val res = File(System.getProperty("app.dir")!!, "src/main/res").walkTopDown().filter { it.isFile && "알맞음" in it.readText() }.toList()
        assertTrue(res.isEmpty())
    }
}
