package com.jlptstudy.app.data

import androidx.room.Room
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Rating
import com.jlptstudy.srs.SrsConfig
import com.jlptstudy.srs.StudyDayRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.ZoneOffset

/**
 * T10 보고서가 남긴 빈틈: 학습 기록이 있는 실제 ID가 retired가 되는 end-to-end 검사.
 * 실제 번들 에셋 콘텐츠 DB(createFromAsset) + 학습 기록 Room DB. 콘텐츠 업데이트로 사라진 ID는 retired 표에 올라가며,
 * 대기열(SessionCards)·사전·새 카드 순서에서는 빠지지만 card_state·review_log 행은 그대로 남는다.
 */
@RunWith(RobolectricTestRunner::class)
class RetiredEndToEndTest {
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")
    private val scheduler = Fsrs6Scheduler()
    private val config = SrsConfig(studyDay = StudyDayRule(ZoneOffset.UTC, 240), enableFuzz = false)

    private fun <T> query(db: androidx.sqlite.db.SupportSQLiteDatabase, q: String, read: (android.database.Cursor) -> T): List<T> =
        db.query(q).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    @Test fun `학습 기록이 있는 실제 ID가 retired가 되면 대기열에서 빠지고 기록은 남는다`() = runBlocking {
        val contentDb = ContentDatabase.open(RuntimeEnvironment.getApplication())
        val studyDb = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StudyDatabase::class.java).allowMainThreadQueries().build()
        try {
            val content = RoomContentRepository(contentDb.contentDao())
            val study = RoomStudyRepository(studyDb.studyDao())
            val raw = contentDb.openHelper.writableDatabase
            val vocabId = query(raw, "SELECT id FROM vocab WHERE level = 5 ORDER BY sort_key, id LIMIT 1") { it.getString(0) }.single()
            val kanji = query(raw, "SELECT literal FROM kanji WHERE level = 5 ORDER BY sort_key, literal LIMIT 1") { it.getString(0) }.single()
            val vr = "vr:$vocabId"; val kr = "kr:$kanji"

            // 두 카드를 실제로 공부한다(card_state·review_log 행이 생긴다)
            for (id in listOf(vr, kr)) study.applyReview(scheduler.schedule(CardState.new(id, t0, scheduler.version), Rating.GOOD, t0, config))
            val cards = SessionCards(study, content)
            val kinds = setOf(CardKind.VR, CardKind.KR); val levels = setOf(JlptLevel.N5)
            assertEquals(setOf(vr, kr), cards.load(kinds, levels).map { it.cardId }.toSet())
            assertTrue(content.cardContents(listOf(vr)).containsKey(vr))
            assertTrue(content.vocabDetail(vocabId) != null)

            // 콘텐츠 업데이트로 vocabId가 사라진다
            raw.execSQL("INSERT INTO retired(id, retired_in) VALUES ('$vocabId', 'test-update')")

            assertEquals("대기열에서는 빠지고 한자 카드는 남는다", setOf(kr), cards.load(kinds, levels).map { it.cardId }.toSet())
            assertTrue(content.cardContents(listOf(vr, kr)).keys == setOf(kr))
            assertNull(content.vocabDetail(vocabId))
            assertTrue(content.newCardOrder(levels, kinds).none { it.cardId == vr })
            // 학습 기록은 지워지지 않는다: 같은 ID를 되살리면(retired 행 삭제) 원래 상태로 돌아온다
            assertEquals(2, query(studyDb.openHelper.readableDatabase, "SELECT COUNT(*) FROM card_state") { it.getInt(0) }.single())
            assertEquals(2, query(studyDb.openHelper.readableDatabase, "SELECT COUNT(*) FROM review_log") { it.getInt(0) }.single())
            assertTrue(study.startedCardIds().contains(vr))
            raw.execSQL("DELETE FROM retired WHERE id = '$vocabId'")
            assertEquals(setOf(vr, kr), cards.load(kinds, levels).map { it.cardId }.toSet())
        } finally {
            studyDb.close(); contentDb.close()
        }
    }
}
