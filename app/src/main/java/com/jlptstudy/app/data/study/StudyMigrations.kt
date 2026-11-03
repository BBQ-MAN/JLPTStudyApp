package com.jlptstudy.app.data.study

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// architecture.md 9.2.4. SQL은 2.json createSql 문자열 그대로 — 엔티티·상수를 참조하지 않아 나중 스키마가 바뀌어도 v2를 만든다. 기존 테이블은 건드리지 않는다.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `quiz_attempt` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exam_id` TEXT, `seed` INTEGER NOT NULL, `question_order_json` TEXT NOT NULL, `current_index` INTEGER NOT NULL DEFAULT 0, `elapsed_ms` INTEGER NOT NULL DEFAULT 0, `started_at_epoch_ms` INTEGER NOT NULL, `finished_at_epoch_ms` INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `quiz_answer` (`attempt_id` INTEGER NOT NULL, `question_id` TEXT NOT NULL, `chosen_index` INTEGER NOT NULL, `correct` INTEGER NOT NULL, `answered_at_epoch_ms` INTEGER NOT NULL, `script_shown` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`attempt_id`, `question_id`))")
    }
}
