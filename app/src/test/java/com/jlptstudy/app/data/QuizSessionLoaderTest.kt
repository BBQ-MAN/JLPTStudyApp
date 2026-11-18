package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDao
import com.jlptstudy.app.data.content.ListeningEntity
import com.jlptstudy.app.data.content.ListeningLine
import com.jlptstudy.app.data.content.QuestionEntity
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.content.RoomQuizContentRepository
import com.jlptstudy.app.data.content.toPoolItem
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.content.toQuizContent
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizAttemptRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.quiz.ExamTemplate
import com.jlptstudy.quiz.PoolItem
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

private fun content(id: String, level: JlptLevel = JlptLevel.N3, section: Section = Section.GRAMMAR, type: String = "t", lines: List<ListeningLine>? = null) =
    QuizContent(id, level, section, type, "", null, null, lines, listOf("a", "b", "c", "d"), 2, "", listOf(null, null, null, null), emptyList(),
        MeaningUi("", "authored", "human", "unreviewed", null, null), false)

private class FakeQuiz(val record: QuizAttemptRecord, val answers: List<QuizAnswerRecord>) : QuizRepository {
    override suspend fun startAttempt(examId: String?, seed: Long, questionOrder: List<String>, startedAtEpochMs: Long) = TODO()
    override suspend fun saveAnswer(answer: QuizAnswerRecord) = TODO()
    override suspend fun saveProgress(attemptId: Long, currentIndex: Int, elapsedMs: Long) = TODO()
    override suspend fun finish(attemptId: Long, finishedAtEpochMs: Long) = TODO()
    override suspend fun attempt(attemptId: Long) = record.takeIf { it.id == attemptId }
    override suspend fun answers(attemptId: Long) = answers
    override suspend fun unfinishedExam() = TODO()
}

private class FakeContent(val items: Map<String, QuizContent>) : QuizContentRepository {
    override suspend fun pool(level: JlptLevel): List<PoolItem> = TODO()
    override suspend fun contents(ids: List<String>) = items.filterKeys { it in ids }
    override suspend fun examTemplate(level: JlptLevel): ExamTemplate? = TODO()
    override suspend fun examLevels(): Set<JlptLevel> = TODO()
}

class QuizSessionLoaderTest {
    private fun record(order: List<String>, examId: String? = null) = QuizAttemptRecord(1, examId, 5L, order, 0, 0, 0, null)
    private fun ans(id: String) = QuizAnswerRecord(1, id, 0, true, 0)

    @Test fun `빠진 문항은 items·answers에서 빠지고 retiredCount로 센다`() = runBlocking {
        val order = listOf("q-1", "q-2", "q-3", "q-4", "q-5")
        val items = listOf("q-1", "q-3", "q-5").associateWith { content(it) }
        val loaded = QuizSessionLoader(FakeQuiz(record(order), order.map(::ans)), FakeContent(items)).load(1)!!
        assertEquals(listOf("q-1", "q-3", "q-5"), loaded.items.map { it.id })
        assertEquals(2, loaded.retiredCount)
        assertEquals(setOf("q-1", "q-3", "q-5"), loaded.answers.keys)
    }

    @Test fun `시도가 없으면 null`() = runBlocking {
        assertNull(QuizSessionLoader(FakeQuiz(record(emptyList()), emptyList()), FakeContent(emptyMap())).load(9))
    }

    @Test fun `AttemptMeta 유도`() = runBlocking {
        suspend fun meta(examId: String?, vararg c: QuizContent) = QuizSessionLoader(
            FakeQuiz(record(c.map { it.id }, examId), emptyList()), FakeContent(c.associateBy { it.id }),
        ).load(1)!!.meta
        assertEquals(AttemptMeta(QuizMode.EXAM, JlptLevel.N4, null, null), meta("exam-n4", content("q-1")))
        val ls = content("ls-1", JlptLevel.N2, Section.LISTENING, "task", lines = listOf(ListeningLine("M", "x", null)))
        assertEquals(AttemptMeta(QuizMode.LISTENING, JlptLevel.N2, Section.LISTENING, "task"), meta(null, ls))
        assertEquals(AttemptMeta(QuizMode.PRACTICE, JlptLevel.N3, Section.GRAMMAR, "t"), meta(null, content("q-1")))
        assertEquals(AttemptMeta(QuizMode.EXAM, JlptLevel.N4, null, null), meta("exam-n4"))
        assertNull(meta(null))
    }
}

class QuizContentConversionTest {
    private fun q(id: String, section: String) = QuestionEntity(
        id, 5, section, "kanji-reading", 1, "s", null, null, """["a","b","c","d"]""", 3, "e", """[null,"x","y","z"]""", "[]",
        "authored", "authored", "human", "unreviewed", null, null, false,
    )

    @Test fun `변환은 choices·answerIndex·section을 그대로 옮기고 모르는 section은 뺀다`() {
        val c = q("q-1", "grammar").toQuizContent()!!
        val question = c.toQuestion()
        assertEquals(Section.GRAMMAR, question.section); assertEquals(listOf("a", "b", "c", "d"), question.choices); assertEquals(3, question.answerIndex)
        assertEquals(Section.GRAMMAR, c.toPoolItem().section)
        assertNull(q("q-2", "weird").toQuizContent())
    }

    @Test fun `listening 행은 LISTENING이고 lines를 가진다`() {
        val e = ListeningEntity("ls-1", 5, 1, "task", """[{"speaker":"M","text":"あ"},{"speaker":"F","text":"い","ttsText":"い"}]""", null,
            """["a","b","c","d"]""", 0, "e", """[null,"x","y","z"]""", "[]", "authored", "authored", "human", "unreviewed", null, null, false)
        val c = e.toQuizContent()!!
        assertEquals(Section.LISTENING, c.section); assertEquals(2, c.lines!!.size); assertEquals("い", c.lines!![1].ttsText)
    }

    @Test fun `contents는 2000개 ID를 900개 이하로 나눠 DAO를 부른다`() = runBlocking {
        val sizes = mutableListOf<Int>()
        val todo = Proxy.newProxyInstance(ContentDao::class.java.classLoader, arrayOf(ContentDao::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as ContentDao
        val dao = object : ContentDao by todo {
            override suspend fun retiredAmong(ids: List<String>): List<String> { sizes += ids.size; return emptyList() }
            override suspend fun questionsByIds(ids: List<String>): List<QuestionEntity> {
                sizes += ids.size
                return ids.mapIndexed { i, id -> if (i == 0) q(id, "weird") else q(id, "vocab") }
            }
            override suspend fun listeningByIds(ids: List<String>): List<ListeningEntity> { sizes += ids.size; return emptyList() }
        }
        val got = RoomQuizContentRepository(dao).contents((1..2000).map { "q-$it" })
        assertTrue(sizes.toString(), sizes.all { it <= 900 })
        assertEquals(6000, sizes.sum()) // retired 2000 + question 2000 + listening 2000(접두사에 기대지 않고 두 테이블을 모두 읽는다)
        assertEquals(1997, got.size)  // 청크마다 첫 행(모르는 section)이 빠진다
    }
}
