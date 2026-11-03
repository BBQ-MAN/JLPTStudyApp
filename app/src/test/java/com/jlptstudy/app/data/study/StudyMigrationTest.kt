package com.jlptstudy.app.data.study

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Phase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

/** architecture.md 9.2.4 마이그레이션 테스트 장치. v3도 이 파일에 더한다. */
@RunWith(RobolectricTestRunner::class)
class StudyMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), StudyDatabase::class.java)

    private val ctx get() = RuntimeEnvironment.getApplication()
    private val v1Tables = mapOf("card_state" to "card_id", "review_log" to "id", "user_settings" to "id")

    private fun createV1(): SupportSQLiteDatabase {
        val db = helper.createDatabase(NAME, 1)
        javaClass.getResource("/migration/study_v1_fixture.sql")!!.readText().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("--") }
            .forEach { db.execSQL(it.removeSuffix(";")) }
        return db
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }

    /** 각 열을 quote()로 문자열화해 |로 잇고 행을 \n으로 이은 SHA-256. */
    private fun checksum(db: SupportSQLiteDatabase, table: String, pk: String): String {
        val cols = db.query("PRAGMA table_info($table)").use { c -> buildList { while (c.moveToNext()) add(c.getString(1)) } }
        val rows = db.query("SELECT ${cols.joinToString(" || '|' || ") { "quote(`$it`)" }} FROM $table ORDER BY $pk")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun snapshot(db: SupportSQLiteDatabase) = v1Tables.map { (t, pk) -> Triple(t, count(db, t), checksum(db, t, pk)) }

    private fun migratedFile(): List<Triple<String, Long, String>> {
        val before = createV1().use { snapshot(it) }
        helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2).close()
        return before
    }

    @Test fun `1·2단계 - v1 세 테이블은 행 수와 체크섬이 그대로이고 퀴즈 테이블이 빈 채로 생긴다`() {
        val before = createV1().use { db ->
            snapshot(db).also { s ->
                val counts = s.associate { it.first to it.second }
                assertTrue("픽스처 card_state 4행 이상", counts.getValue("card_state") >= 4)
                assertTrue("픽스처 review_log 6행 이상", counts.getValue("review_log") >= 6)
                assertEquals(1L, counts.getValue("user_settings"))
            }
        }
        helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2).use { db ->
            assertEquals(before, snapshot(db))
            assertEquals(0L, count(db, "quiz_attempt"))
            assertEquals(0L, count(db, "quiz_answer"))

            db.execSQL("INSERT INTO quiz_answer (attempt_id, question_id, chosen_index, correct, answered_at_epoch_ms) VALUES (1, 'q-n5-vocab-0001', 2, 1, 1793700000000)")
            db.query("SELECT script_shown FROM quiz_answer WHERE attempt_id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun `2_json의 v1 테이블 createSql·indices는 1_json과 같다`() {
        fun entities(v: Int) = JSONObject(File(System.getProperty("app.dir"), "schemas/${StudyDatabase::class.java.name}/$v.json").readText())
            .getJSONObject("database").getJSONArray("entities").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .associateBy { it.getString("tableName") }
        val v1 = entities(1)
        val v2 = entities(2)
        assertEquals(v1Tables.keys, v1.keys)
        assertEquals(v1Tables.keys + setOf("quiz_attempt", "quiz_answer"), v2.keys)
        for (t in v1Tables.keys) {
            assertEquals(t, v1.getValue(t).getString("createSql"), v2.getValue(t).getString("createSql"))
            assertEquals(t, v1.getValue(t).optJSONArray("indices")?.toString(), v2.getValue(t).optJSONArray("indices")?.toString())
        }
    }

    @Test fun `3단계 - 마이그레이션한 파일을 Room으로 열면 설정과 카드 상태가 픽스처와 같다`() = runBlocking {
        migratedFile()
        val db = Room.databaseBuilder(ctx, StudyDatabase::class.java, NAME).addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries().build()
        try {
            val repo = RoomStudyRepository(db.studyDao())
            assertEquals(
                UserSettings(
                    dayBoundaryMinutes = 300, newCardsPerDay = 25, extraNewCards = 5, extraNewCardsStudyDay = 20394,
                    targetLevels = setOf(JlptLevel.N5, JlptLevel.N4), desiredRetention = 0.85, maxIntervalDays = 3650,
                    learnAheadMinutes = 15, vocabRecognitionEnabled = true, kanjiCardsEnabled = false,
                    vocabRecallEnabled = true, grammarCardsEnabled = false, frontFurigana = true, bodyFurigana = false,
                    listeningScriptFirst = true, theme = ThemeMode.DARK, onboardingDone = true, firstNoticeDismissed = true,
                ),
                repo.settings().first(),
            )
            assertEquals(FIXTURE_STATES.associateBy { it.cardId }, repo.states(FIXTURE_STATES.map { it.cardId }))
            // 픽스처의 로그 JSON이 실제 CardState 직렬화 형식인지 확인한다.
            db.openHelper.readableDatabase.query("SELECT state_before_json, state_after_json FROM review_log").use { c ->
                while (c.moveToNext()) for (i in 0..1) StateJson.decodeFromString(CardState.serializer(), c.getString(i))
            }
        } finally {
            db.close()
        }
    }

    @Test fun `StudyDatabase_open은 v1 파일을 v2로 올린다`() = runBlocking {
        val before = createV1().use { snapshot(it) }
        ctx.getDatabasePath(NAME).copyTo(ctx.getDatabasePath(StudyDatabase.FILE_NAME), overwrite = true)
        val db = StudyDatabase.open(ctx)
        try {
            val sdb = db.openHelper.readableDatabase
            assertEquals(2, sdb.version)
            assertEquals(before, snapshot(sdb))
        } finally {
            db.close()
        }
    }

    @Test fun `4단계 - addMigrations 없이 v1 파일을 v2로 열면 예외가 나고 기록이 남는다`() {
        val rows = createV1().use { count(it, "card_state") }
        val db = Room.databaseBuilder(ctx, StudyDatabase::class.java, NAME).allowMainThreadQueries().build()
        val thrown = runCatching { db.openHelper.writableDatabase }.exceptionOrNull()
        db.close()
        assertTrue("예외가 나야 한다", thrown is IllegalStateException)
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(1, raw.version)
            raw.rawQuery("SELECT COUNT(*) FROM card_state", null).use { it.moveToFirst(); assertEquals(rows, it.getLong(0)) }
        }
    }

    companion object {
        private const val NAME = "migration-test.db"
        private const val V = "fsrs6/py-fsrs-6.3.2"

        // study_v1_fixture.sql의 card_state 행.
        private val FIXTURE_STATES = listOf(
            CardState("vr:jm:1358280", Phase.LEARNING, 1, 2.3065, 2.118104, 1793700000000, 1793699400000, 2, 0, V),
            CardState("vp:jm:1358280", Phase.REVIEW, null, 15.25, 4.5, 1795000000000, 1793600000000, 4, 0, V),
            CardState("kr:水", Phase.RELEARNING, 0, 1.75, 7.25, 1793700600000, 1793700000000, 7, 2, V),
            CardState("vr:jm:1000220", Phase.REVIEW, null, 30.5, 3.3, 1796000000000, null, 3, 0, V),
        )
    }
}
