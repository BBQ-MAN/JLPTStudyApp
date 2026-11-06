package com.jlptstudy.app.data.study

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Phase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

internal val StateJson = Json { encodeDefaults = true }

internal fun CardStateEntity(state: CardState): CardStateEntity = CardStateEntity(
    cardId = state.cardId,
    kind = state.cardId.substringBefore(':'),
    contentId = state.cardId.substringAfter(':'),
    phase = state.phase.value, step = state.step, stability = state.stability, difficulty = state.difficulty,
    dueAtEpochMs = state.dueAtEpochMs, lastReviewAtEpochMs = state.lastReviewAtEpochMs,
    reps = state.reps, lapses = state.lapses, schedulerVersion = state.schedulerVersion,
)

internal fun CardStateEntity.toCardState(): CardState = CardState(
    cardId = cardId, phase = Phase.entries.first { it.value == phase }, step = step, stability = stability,
    difficulty = difficulty, dueAtEpochMs = dueAtEpochMs, lastReviewAtEpochMs = lastReviewAtEpochMs,
    reps = reps, lapses = lapses, schedulerVersion = schedulerVersion,
)

@Dao
abstract class StudyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertState(state: CardStateEntity)

    @Insert
    abstract suspend fun insertLog(log: ReviewLogEntity): Long

    @Query("DELETE FROM card_state WHERE card_id = :cardId")
    abstract suspend fun deleteState(cardId: String)

    @Query("SELECT * FROM review_log WHERE id > :afterLogId AND undone = 0 ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastUndoable(afterLogId: Long): ReviewLogEntity?

    @Query("UPDATE review_log SET undone = 1 WHERE id = :id")
    abstract suspend fun markUndone(id: Long)

    @Query("SELECT COALESCE(MAX(id), 0) FROM review_log")
    abstract suspend fun maxLogId(): Long

    @Query("SELECT card_id FROM card_state")
    abstract suspend fun startedCardIds(): List<String>

    @Query("SELECT * FROM card_state WHERE card_id IN (:cardIds)")
    abstract suspend fun statesByIds(cardIds: List<String>): List<CardStateEntity>

    @Query("SELECT * FROM card_state WHERE kind IN (:kinds)")
    abstract suspend fun statesOfKinds(kinds: List<String>): List<CardStateEntity>

    @Query(
        "SELECT card_id FROM review_log WHERE undone = 0 AND phase_before = 0 " +
            "AND reviewed_at_epoch_ms >= :fromEpochMs AND reviewed_at_epoch_ms < :toEpochMs ORDER BY id",
    )
    abstract suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long): List<String>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    abstract fun observeSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    abstract suspend fun settingsRow(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSettings(settings: UserSettingsEntity)

    /** 평가 한 번 = card_state upsert + review_log insert, 한 트랜잭션. 예외는 롤백 뒤 호출자에게 전달된다. */
    @Transaction
    open suspend fun applyReview(state: CardStateEntity, log: ReviewLogEntity) {
        upsertState(state)
        insertLog(log)
    }

    /** id > afterLogId, undone = 0 중 id 최대 로그의 이전 상태를 복원한다(새 카드였으면 행 삭제). 로그는 undone = 1로만 표시. */
    @Transaction
    open suspend fun undoLast(afterLogId: Long): ReviewLogEntity? {
        val log = lastUndoable(afterLogId) ?: return null
        val before = StateJson.decodeFromString(CardState.serializer(), log.stateBeforeJson)
        if (before.phase == Phase.NEW) deleteState(log.cardId) else upsertState(CardStateEntity(before))
        markUndone(log.id)
        return log
    }

    @Transaction
    open suspend fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        putSettings(transform(settingsRow() ?: UserSettingsEntity()))
    }

    // 시도·답 삭제 쿼리는 두지 않는다(포기 = finish, architecture.md 9.2.6).
    @Insert
    abstract suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAnswer(answer: QuizAnswerEntity)

    /** 끝난 시도(finished_at != null)에는 기록하지 않는다. 없는 시도는 FK 예외 그대로. */
    @Transaction
    open suspend fun upsertAnswer(answer: QuizAnswerEntity) {
        if (attemptById(answer.attemptId)?.finishedAtEpochMs != null) return
        insertAnswer(answer)
    }

    @Query("UPDATE quiz_attempt SET current_index = :currentIndex, elapsed_ms = :elapsedMs WHERE id = :id AND finished_at_epoch_ms IS NULL")
    abstract suspend fun updateProgress(id: Long, currentIndex: Int, elapsedMs: Long)

    @Query("UPDATE quiz_attempt SET finished_at_epoch_ms = :at WHERE id = :id AND finished_at_epoch_ms IS NULL")
    abstract suspend fun finishAttempt(id: Long, at: Long)

    @Query("UPDATE quiz_attempt SET finished_at_epoch_ms = :at WHERE exam_id IS NOT NULL AND finished_at_epoch_ms IS NULL")
    abstract suspend fun closeUnfinishedExams(at: Long)

    @Query("SELECT * FROM quiz_attempt WHERE id = :id")
    abstract suspend fun attemptById(id: Long): QuizAttemptEntity?

    @Query("SELECT * FROM quiz_answer WHERE attempt_id = :id")
    abstract suspend fun answersOf(id: Long): List<QuizAnswerEntity>

    @Query("SELECT * FROM quiz_attempt WHERE exam_id IS NOT NULL AND finished_at_epoch_ms IS NULL ORDER BY id DESC LIMIT 1")
    abstract suspend fun unfinishedExam(): QuizAttemptEntity?

    @Transaction
    open suspend fun startAttempt(attempt: QuizAttemptEntity): Long {
        if (attempt.examId != null) closeUnfinishedExams(attempt.startedAtEpochMs)
        return insertAttempt(attempt)
    }
}
