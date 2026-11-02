package com.jlptstudy.app.data

import androidx.room.Room
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.ScheduleResult
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class StudyRepositoryTest {
    private lateinit var db: StudyDatabase
    private lateinit var repo: RoomStudyRepository
    private val scheduler = Fsrs6Scheduler()
    private val config = SrsConfig(studyDay = StudyDayRule(ZoneOffset.UTC, 240), enableFuzz = false)
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StudyDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RoomStudyRepository(db.studyDao())
    }

    @After fun tearDown() = db.close()

    private fun rate(cardId: String, at: Instant, current: CardState? = null, rating: Rating = Rating.GOOD): ScheduleResult =
        scheduler.schedule(current ?: CardState.new(cardId, at, scheduler.version), rating, at, config)

    private fun sql(q: String) = db.openHelper.writableDatabase.execSQL(q)

    private fun <T> query(q: String, read: (android.database.Cursor) -> T): List<T> =
        db.openHelper.readableDatabase.query(q).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    @Test fun `applyReview는 card_state와 review_log를 함께 쓴다`() = runBlocking {
        val r = rate("vr:jm:1", t0)
        repo.applyReview(r)
        assertEquals(setOf("vr:jm:1"), repo.startedCardIds())
        assertEquals(r.state, repo.states(listOf("vr:jm:1")).getValue("vr:jm:1"))
        assertEquals(1L, repo.maxLogId())
        assertEquals(listOf("vr" to "jm:1"), query("SELECT kind, content_id FROM card_state") { it.getString(0) to it.getString(1) })
    }

    @Test fun `트랜잭션 중 예외면 둘 다 쓰이지 않고 예외가 전달된다`() = runBlocking {
        sql("CREATE TRIGGER boom BEFORE INSERT ON review_log BEGIN SELECT RAISE(ABORT, 'boom'); END")
        val thrown = runCatching { repo.applyReview(rate("vr:jm:1", t0)) }.exceptionOrNull()
        assertNotNull("예외가 호출자에게 전달된다", thrown)
        assertEquals(emptySet<String>(), repo.startedCardIds())
        assertEquals(0L, repo.maxLogId())
    }

    @Test fun `undoLast는 id 최대 로그의 이전 상태를 복원하고 로그를 남긴다`() = runBlocking {
        // 세션 시작 전 로그: 시각이 미래여도 되돌리지 않는다.
        repo.applyReview(rate("vr:jm:0", t0.plusSeconds(86_400 * 30)))
        val after = repo.maxLogId()
        // 시계를 과거로 돌린 뒤 평가해도 대상은 id 순서다.
        val first = rate("vr:jm:1", t0)
        repo.applyReview(first)
        val second = rate("vr:jm:1", t0.minusSeconds(3600), current = first.state, rating = Rating.EASY)
        repo.applyReview(second)

        val u1 = repo.undoLast(after)!!
        assertEquals("vr:jm:1", u1.cardId)
        assertEquals(first.state, u1.restored)
        assertEquals(first.state, repo.states(listOf("vr:jm:1")).getValue("vr:jm:1"))

        val u2 = repo.undoLast(after)!!
        assertNull("새 카드였으면 restored == null", u2.restored)
        assertEquals(emptyMap<String, CardState>(), repo.states(listOf("vr:jm:1")))

        assertNull("세션 시작 전 로그는 되돌리지 않는다", repo.undoLast(after))
        assertEquals(listOf(1L to 0, 2L to 1, 3L to 1), query("SELECT id, undone FROM review_log ORDER BY id") { it.getLong(0) to it.getInt(1) })
        assertEquals(setOf("vr:jm:0"), repo.startedCardIds())
    }

    @Test fun `introducedToday는 undone 0, phase_before 0, 학습일 구간 안의 로그만 낸다`() = runBlocking {
        val day = config.studyDay
        val today = day.studyDayOf(t0)
        val from = day.startOfStudyDay(today)
        val to = day.startOfStudyDay(today + 1)
        val a1 = rate("vr:jm:A", from.plusSeconds(3600))
        repo.applyReview(a1)
        repo.applyReview(rate("vr:jm:A", from.plusSeconds(3700), current = a1.state)) // phase_before != 0
        repo.applyReview(rate("vr:jm:B", from.minusSeconds(1)))                       // 어제
        repo.applyReview(rate("vr:jm:D", to))                                        // 내일 구간(끝 값은 포함하지 않는다)
        repo.applyReview(rate("vr:jm:C", from.plusSeconds(7200)))
        repo.undoLast(0) // C 되돌림
        assertEquals(listOf("vr:jm:A"), repo.introducedToday(from.toEpochMilli(), to.toEpochMilli()))
    }

    @Test fun `user_settings 기본값은 5절 표와 같다`() = runBlocking {
        assertEquals("행이 없어도 기본값", UserSettings(), repo.settings().first())
        sql("INSERT INTO user_settings(id) VALUES (1)")
        val s = repo.settings().first()
        assertEquals(UserSettings(), s)
        assertEquals(240, s.dayBoundaryMinutes); assertEquals(10, s.newCardsPerDay)
        assertEquals(setOf(JlptLevel.N5), s.targetLevels); assertEquals(false, s.listeningScriptFirst)
        val defaults = query("PRAGMA table_info(user_settings)") { it.getString(1) to it.getString(4) }.toMap()
        assertEquals(
            mapOf(
                "id" to null, "day_boundary_minutes" to "240", "new_cards_per_day" to "10", "extra_new_cards" to "0",
                "extra_new_cards_study_day" to null, "target_levels" to "'5'", "desired_retention" to "0.9",
                "max_interval_days" to "36500", "learn_ahead_minutes" to "20", "vocab_recognition_enabled" to "1",
                "kanji_cards_enabled" to "1", "vocab_recall_enabled" to "0", "grammar_cards_enabled" to "1",
                "front_furigana" to "0", "body_furigana" to "1", "listening_script_first" to "0",
                "theme" to "'system'", "onboarding_done" to "0", "first_notice_dismissed" to "0",
            ),
            defaults,
        )
    }

    @Test fun `updateSettings는 범위 밖 값을 거부하고 저장 값을 바꾸지 않는다`() = runBlocking {
        for (bad in listOf(0, 51, 7)) {
            assertThrows("new_cards_per_day=$bad", IllegalArgumentException::class.java) {
                runBlocking { repo.updateSettings { it.copy(newCardsPerDay = bad) } }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.updateSettings {
                    it.copy(vocabRecognitionEnabled = false, kanjiCardsEnabled = false, vocabRecallEnabled = false, grammarCardsEnabled = false)
                }
            }
        }
        // 보이는 종류(단어 인식·한자·단어 회상)가 모두 꺼지면 숨은 문법 스위치가 켜져 있어도 거부한다.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.updateSettings {
                    it.copy(vocabRecognitionEnabled = false, kanjiCardsEnabled = false, vocabRecallEnabled = false, grammarCardsEnabled = true)
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateSettings { it.copy(targetLevels = emptySet()) } } }
        assertEquals(UserSettings(), repo.settings().first())

        repo.updateSettings { it.copy(newCardsPerDay = 15, targetLevels = setOf(JlptLevel.N5, JlptLevel.N4), onboardingDone = true) }
        val s = repo.settings().first()
        assertEquals(15, s.newCardsPerDay); assertEquals(setOf(JlptLevel.N5, JlptLevel.N4), s.targetLevels); assertTrue(s.onboardingDone)
    }

    @Test fun `저장된 target_levels의 1에서 5 밖 값은 읽을 때 버린다`() = runBlocking {
        sql("INSERT INTO user_settings(id, target_levels) VALUES (1, '5,9,0,x,3')")
        assertEquals(setOf(JlptLevel.N5, JlptLevel.N3), repo.settings().first().targetLevels)
        sql("UPDATE user_settings SET target_levels = '9'")
        assertEquals(emptySet<JlptLevel>(), repo.settings().first().targetLevels)
        repo.updateSettings { it.copy(onboardingDone = true) } // 손상된 값이어도 다른 설정은 저장된다
        assertTrue(repo.settings().first().onboardingDone)
    }
}
