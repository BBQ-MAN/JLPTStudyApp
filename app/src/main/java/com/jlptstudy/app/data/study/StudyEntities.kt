package com.jlptstudy.app.data.study

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// architecture.md 5절.

@Entity(
    tableName = "card_state",
    indices = [Index("due_at_epoch_ms"), Index("kind", "content_id")],
)
data class CardStateEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val cardId: String,
    val kind: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    val phase: Int,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    @ColumnInfo(name = "due_at_epoch_ms") val dueAtEpochMs: Long,
    @ColumnInfo(name = "last_review_at_epoch_ms") val lastReviewAtEpochMs: Long?,
    val reps: Int,
    val lapses: Int,
    @ColumnInfo(name = "scheduler_version") val schedulerVersion: String,
)

@Entity(
    tableName = "review_log",
    indices = [Index("card_id"), Index("reviewed_at_epoch_ms")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "reviewed_at_epoch_ms") val reviewedAtEpochMs: Long,
    val rating: Int,
    @ColumnInfo(name = "phase_before") val phaseBefore: Int,
    @ColumnInfo(name = "elapsed_days") val elapsedDays: Int,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Int,
    @ColumnInfo(name = "state_before_json") val stateBeforeJson: String,
    @ColumnInfo(name = "state_after_json") val stateAfterJson: String,
    @ColumnInfo(name = "scheduler_version") val schedulerVersion: String,
    @ColumnInfo(defaultValue = "0") val undone: Boolean = false,
)

/** 단일 행(id = 1). 기본값은 5절 표와 같고 SQL DEFAULT로도 둔다. */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "day_boundary_minutes", defaultValue = "240") val dayBoundaryMinutes: Int = 240,
    @ColumnInfo(name = "new_cards_per_day", defaultValue = "10") val newCardsPerDay: Int = 10,
    @ColumnInfo(name = "extra_new_cards", defaultValue = "0") val extraNewCards: Int = 0,
    @ColumnInfo(name = "extra_new_cards_study_day") val extraNewCardsStudyDay: Long? = null,
    @ColumnInfo(name = "target_levels", defaultValue = "'5'") val targetLevels: String = "5",
    @ColumnInfo(name = "desired_retention", defaultValue = "0.9") val desiredRetention: Double = 0.9,
    @ColumnInfo(name = "max_interval_days", defaultValue = "36500") val maxIntervalDays: Int = 36500,
    @ColumnInfo(name = "learn_ahead_minutes", defaultValue = "20") val learnAheadMinutes: Int = 20,
    @ColumnInfo(name = "vocab_recognition_enabled", defaultValue = "1") val vocabRecognitionEnabled: Boolean = true,
    @ColumnInfo(name = "kanji_cards_enabled", defaultValue = "1") val kanjiCardsEnabled: Boolean = true,
    @ColumnInfo(name = "vocab_recall_enabled", defaultValue = "0") val vocabRecallEnabled: Boolean = false,
    @ColumnInfo(name = "grammar_cards_enabled", defaultValue = "1") val grammarCardsEnabled: Boolean = true,
    @ColumnInfo(name = "front_furigana", defaultValue = "0") val frontFurigana: Boolean = false,
    @ColumnInfo(name = "body_furigana", defaultValue = "1") val bodyFurigana: Boolean = true,
    @ColumnInfo(name = "listening_script_first", defaultValue = "0") val listeningScriptFirst: Boolean = false,
    @ColumnInfo(defaultValue = "'system'") val theme: String = "system",
    @ColumnInfo(name = "onboarding_done", defaultValue = "0") val onboardingDone: Boolean = false,
    @ColumnInfo(name = "first_notice_dismissed", defaultValue = "0") val firstNoticeDismissed: Boolean = false,
)

// architecture.md 9.2.4(v2). 외래 키 없음 — 삭제는 저장소가 한 트랜잭션으로 한다.
@Entity(tableName = "quiz_attempt")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exam_id") val examId: String?,
    val seed: Long,
    @ColumnInfo(name = "question_order_json") val questionOrderJson: String,
    @ColumnInfo(name = "current_index", defaultValue = "0") val currentIndex: Int = 0,
    @ColumnInfo(name = "elapsed_ms", defaultValue = "0") val elapsedMs: Long = 0,
    @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @ColumnInfo(name = "finished_at_epoch_ms") val finishedAtEpochMs: Long? = null,
)

@Entity(tableName = "quiz_answer", primaryKeys = ["attempt_id", "question_id"])
data class QuizAnswerEntity(
    @ColumnInfo(name = "attempt_id") val attemptId: Long,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "chosen_index") val chosenIndex: Int,
    val correct: Boolean,
    @ColumnInfo(name = "answered_at_epoch_ms") val answeredAtEpochMs: Long,
    @ColumnInfo(name = "script_shown", defaultValue = "0") val scriptShown: Boolean = false,
)
