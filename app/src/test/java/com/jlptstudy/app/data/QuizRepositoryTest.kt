package com.jlptstudy.app.data

import androidx.room.Room
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.RoomQuizContentRepository
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.RoomQuizRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.app.data.study.QuizAttemptEntity
import com.jlptstudy.app.ui.grammar.newContentDb
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QuizRepositoryTest {
    private lateinit var db: StudyDatabase
    private lateinit var repo: RoomQuizRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StudyDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomQuizRepository(db.studyDao())
    }

    @After fun tearDown() = db.close()

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun `DB 문항을 읽어 섞고 섞은 화면의 색인 2를 고르면 원래 색인으로 저장하고 correct를 계산한다`() = runBlocking {
        val content = newContentDb()
        val q = try {
            content.question("q-n5-vocab-0001", answer = 1)
            RoomQuizContentRepository(content.contentDao()).contents(listOf("q-n5-vocab-0001")).getValue("q-n5-vocab-0001").toQuestion()
        } finally { content.close() }
        assertEquals(1, q.answerIndex) // DB의 answer_index가 그대로 옮겨졌다
        val shuffled = Quiz.shuffle(q, 7L)
        val chosen = shuffled.toOriginal(2)
        val id = repo.startAttempt(null, 7L, listOf(q.id), 1000L)
        repo.saveAnswer(QuizAnswerRecord(id, q.id, chosen, chosen == q.answerIndex, 2000L))
        val a = repo.answers(id).single()
        assertEquals(chosen, a.chosenIndex)
        assertEquals(chosen == q.answerIndex, a.correct)
        assertEquals(1, count("quiz_answer"))
    }

    @Test fun `같은 문항을 두 번 저장하면 행은 1개이고 나중 값이다`() = runBlocking {
        val id = repo.startAttempt(null, 1L, listOf("q-1"), 1L)
        repo.saveAnswer(QuizAnswerRecord(id, "q-1", 0, false, 10L))
        repo.saveAnswer(QuizAnswerRecord(id, "q-1", 3, true, 20L))
        val a = repo.answers(id).single()
        assertEquals(3, a.chosenIndex); assertTrue(a.correct); assertEquals(20L, a.answeredAtEpochMs)
    }

    @Test fun `새 모의고사를 시작하면 이전 미완료 시도는 startedAt으로 닫히고 답은 남는다`() = runBlocking {
        val old = repo.startAttempt("exam-n5", 1L, listOf("q-1"), 100L)
        repo.saveAnswer(QuizAnswerRecord(old, "q-1", 0, true, 150L))
        assertEquals(old, repo.unfinishedExam()!!.id)
        val new = repo.startAttempt("exam-n5", 2L, listOf("q-1"), 500L)
        assertEquals(500L, repo.attempt(old)!!.finishedAtEpochMs)
        assertEquals(1, repo.answers(old).size)
        assertEquals(new, repo.unfinishedExam()!!.id)
    }

    @Test fun `연습 시도는 미완료 모의고사를 닫지 않는다`() = runBlocking {
        val exam = repo.startAttempt("exam-n5", 1L, listOf("q-1"), 100L)
        repo.startAttempt(null, 2L, listOf("q-1"), 200L)
        assertNull(repo.attempt(exam)!!.finishedAtEpochMs)
    }

    @Test fun `포기 = finish는 행을 지우지 않는다`() = runBlocking {
        val id = repo.startAttempt("exam-n4", 1L, listOf("q-1"), 100L)
        repo.saveAnswer(QuizAnswerRecord(id, "q-1", 0, true, 150L))
        repo.saveProgress(id, 1, 900L)
        repo.finish(id, 777L)
        assertEquals(777L, repo.attempt(id)!!.finishedAtEpochMs)
        assertEquals(1, count("quiz_attempt")); assertEquals(1, count("quiz_answer"))
        assertNull(repo.unfinishedExam())
        assertEquals(900L, repo.attempt(id)!!.elapsedMs)
    }

    @Test fun `finish는 멱등이다 - 이미 끝난 시도의 종료 시각은 그대로`() = runBlocking {
        val id = repo.startAttempt(null, 1L, listOf("q-1"), 100L)
        repo.finish(id, 200L)
        repo.finish(id, 999L)
        assertEquals(200L, repo.attempt(id)!!.finishedAtEpochMs)
    }

    @Test fun `끝난 시도에는 답도 진행도 기록되지 않는다`() = runBlocking {
        val id = repo.startAttempt(null, 1L, listOf("q-1", "q-2"), 100L)
        repo.saveAnswer(QuizAnswerRecord(id, "q-1", 0, true, 110L))
        repo.saveProgress(id, 1, 500L)
        repo.finish(id, 200L)
        repo.saveAnswer(QuizAnswerRecord(id, "q-1", 3, false, 300L)) // 덮어쓰지 않는다
        repo.saveAnswer(QuizAnswerRecord(id, "q-2", 1, true, 300L)) // 새 행도 없다
        repo.saveProgress(id, 2, 9_000L)
        assertEquals(listOf(0), repo.answers(id).map { it.chosenIndex })
        assertEquals(1, repo.attempt(id)!!.currentIndex)
        assertEquals(500L, repo.attempt(id)!!.elapsedMs)
    }

    @Test fun `없는 시도는 null`() = runBlocking { assertNull(repo.attempt(99L)) }
}

@RunWith(RobolectricTestRunner::class)
class QuizContentDbTest {
    private val db = newContentDb()
    private val repo = RoomQuizContentRepository(db.contentDao())

    @After fun tearDown() = db.close()

    @Test fun `exam_template은 없으면 null, 깨졌으면 예외, 정상이면 변환`() = runBlocking {
        assertNull(repo.examTemplate(JlptLevel.N5))
        db.examTemplate(5, "{broken")
        try { repo.examTemplate(JlptLevel.N5); fail("예외여야 한다") } catch (e: Exception) { assertNotNull(e) }
        db.examTemplate(4, """{"sections":[{"id":"vocab","titleKo":"어휘","minutes":20,"parts":[{"section":"vocab","type":"kanji-reading","count":7}]}]}""")
        val t = repo.examTemplate(JlptLevel.N4)!!
        assertEquals(4, t.level); assertEquals(Section.VOCAB, t.sections.single().parts.single().section)
        assertEquals(setOf(JlptLevel.N5, JlptLevel.N4), repo.examLevels())
    }

    @Test fun `pool과 contents는 retired·없는 ID·깨진 행·모르는 section을 뺀다`() = runBlocking {
        db.question("q-1"); db.question("q-2"); db.question("q-bad", choices = "{x"); db.question("q-sec", section = "weird")
        db.question("q-three", choices = """["a","b","c"]"""); db.question("q-4", level = 4)
        db.openHelper.writableDatabase.execSQL("INSERT INTO retired(id, retired_in) VALUES ('q-2','t')")
        // pool은 contents가 버릴 행(깨진 JSON·선택지 4개 아님·모르는 section)과 retired를 처음부터 넣지 않는다
        assertEquals(listOf("q-1"), repo.pool(JlptLevel.N5).map { it.id }.sorted())
        val got = repo.contents(listOf("q-1", "q-2", "q-bad", "q-sec", "q-three", "q-nope", "q-1"))
        assertEquals(setOf("q-1"), got.keys)
        assertEquals(Section.VOCAB, got.getValue("q-1").section)
        assertFalse(got.isEmpty())
    }
}
