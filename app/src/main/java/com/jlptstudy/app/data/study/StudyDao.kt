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
}
