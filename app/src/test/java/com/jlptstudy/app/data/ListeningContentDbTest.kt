package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomQuizContentRepository
import com.jlptstudy.app.ui.grammar.newContentDb
import com.jlptstudy.app.ui.listening.ListeningStatus
import com.jlptstudy.app.ui.listening.ListeningViewModel
import com.jlptstudy.app.ui.listening.attributionWith
import com.jlptstudy.app.speech.FakeSpeaker
import com.jlptstudy.app.ui.practice.FakeQuiz
import com.jlptstudy.app.ui.practice.PracticeHomeStatus
import com.jlptstudy.app.ui.practice.PracticeHomeViewModel
import com.jlptstudy.app.ui.practice.TickClock
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListeningContentDbTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `번들 에셋을 createFromAsset으로 열어 listening 조회가 예외 없이 동작하고 빈 테이블이면 S12에 청해 유형이 없다`() = runBlocking {
        val db = ContentDatabase.open(RuntimeEnvironment.getApplication())
        try {
            val repo = RoomQuizContentRepository(db.contentDao())
            JlptLevel.entries.forEach { repo.pool(it) } // 예외 없이
            // 번들에 청해가 있으면(M25) 그 행이 읽히고, 없으면 빈 맵이다
            val got = repo.contents(listOf("ls-n5-0001"))
            if (db.contentDao().countListening() > 0) assertTrue(got.containsKey("ls-n5-0001")) else assertEquals(emptyMap<String, Any>(), got)
            assertEquals(db.contentDao().countListening() > 0, repo.hasListening())
            val home = PracticeHomeViewModel(repo, FakeQuiz(), MemStudy(), TickClock()).state.value
            if (!repo.hasListening()) assertTrue(home.types.none { it.section == Section.LISTENING })
        } finally { db.close() }
    }

    @Test fun `listening 행을 읽어 lines와 question NULL을 옮기고 hasListening이 참이다`() = runBlocking {
        val db = newContentDb()
        try {
            db.listening("ls-n5-0001"); db.listening("ls-n5-0002", type = "quick-response", question = null)
            val repo = RoomQuizContentRepository(db.contentDao())
            assertTrue(repo.hasListening())
            val got = repo.contents(listOf("ls-n5-0001", "ls-n5-0002"))
            assertEquals("きょうは", got.getValue("ls-n5-0001").lines!![0].ttsText)
            assertEquals("", got.getValue("ls-n5-0002").stem)
            assertEquals(setOf("task", "quick-response"), repo.pool(JlptLevel.N5).map { it.type }.toSet())
            assertTrue(repo.pool(JlptLevel.N5).all { it.section == Section.LISTENING })
        } finally { db.close() }
    }

    @Test fun `lines_json이 깨진 listening 행은 시도의 문항에서 빠지고 앱이 죽지 않는다`() = runBlocking {
        val db = newContentDb()
        try {
            db.listening("ls-n5-0001", linesJson = "{깨짐"); db.listening("ls-n5-0002")
            val repo = RoomQuizContentRepository(db.contentDao())
            val quiz = FakeQuiz()
            val id = quiz.startAttempt(null, 1L, listOf("ls-n5-0001", "ls-n5-0002"), 1L)
            val vm = ListeningViewModel(id, QuizSessionLoader(quiz, repo), quiz, MemStudy(), attributionWith(), TickClock(), { FakeSpeaker() })
            val s = withTimeout(5_000) { vm.state.first { it.status != ListeningStatus.LOADING } }
            assertEquals(ListeningStatus.ANSWERING, s.status)
            assertEquals(1, s.total)
            assertEquals("ls-n5-0002", s.question!!.id)
            val only = quiz.startAttempt(null, 1L, listOf("ls-n5-0001"), 1L)
            val empty = ListeningViewModel(only, QuizSessionLoader(quiz, repo), quiz, MemStudy(), attributionWith(), TickClock(), { FakeSpeaker() })
            assertEquals(ListeningStatus.EMPTY, withTimeout(5_000) { empty.state.first { it.status != ListeningStatus.LOADING } }.status)
            assertFalse(empty.state.value.question != null)
        } finally { db.close() }
    }
}
