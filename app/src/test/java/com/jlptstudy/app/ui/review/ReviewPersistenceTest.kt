package com.jlptstudy.app.ui.review

import androidx.room.Room
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** 평가 직후(세션 종료 전) 실제 Room에 card_state·review_log 행이 있다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewPersistenceTest {
    private lateinit var db: StudyDatabase

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StudyDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun `onRate 직후 세션이 끝나기 전에 card_state와 review_log에 행이 있다`() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-11-02T10:00:00Z"))
        val vm = ReviewViewModel(
            RoomStudyRepository(db.studyDao()), FakeContent((1..3).map { vocab("w$it", "語$it", "ご$it") }),
            Fsrs6Scheduler(), clock,
        ) { ZoneOffset.UTC }
        withTimeout(5_000) { vm.state.first { it.card != null } }
        clock.advance(Duration.ofMillis(300)); vm.reveal()
        clock.advance(Duration.ofMillis(300)); vm.rate(Rating.GOOD)
        val s = withTimeout(5_000) { vm.state.first { it.reviewedInSession + it.newInSession == 1 } }
        assertEquals(false, s.finished)
        assertEquals(1, count("card_state")); assertEquals(1, count("review_log"))
        assertEquals(1, db.studyDao().maxLogId().toInt())
    }
}
