package com.jlptstudy.app.data.study

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

// architecture.md 9.2.6. 모든 쓰기는 실패 시 예외. 시도·답 삭제 함수는 없다(포기 = finish).
data class QuizAttemptRecord(
    val id: Long, val examId: String?, val seed: Long, val questionOrder: List<String>,
    val currentIndex: Int, val elapsedMs: Long, val startedAtEpochMs: Long, val finishedAtEpochMs: Long?,
)

data class QuizAnswerRecord(
    val attemptId: Long, val questionId: String, val chosenIndex: Int, // 섞기 전 원래 색인
    val correct: Boolean, val answeredAtEpochMs: Long, val scriptShown: Boolean = false,
)

interface QuizRepository {
    /** examId != null이면 같은 트랜잭션에서 다른 미완료 모의고사의 finished_at을 startedAt으로 채운다(답은 남김). */
    suspend fun startAttempt(examId: String?, seed: Long, questionOrder: List<String>, startedAtEpochMs: Long): Long
    /** 끝난 시도에는 기록하지 않고 조용히 무시한다(늦게 도착한 쓰기가 결과를 바꾸지 못하게). */
    suspend fun saveAnswer(answer: QuizAnswerRecord)
    /** 끝난 시도에는 무시한다. */
    suspend fun saveProgress(attemptId: Long, currentIndex: Int, elapsedMs: Long)
    /** 멱등: 이미 끝난 시도의 종료 시각은 바꾸지 않는다. */
    suspend fun finish(attemptId: Long, finishedAtEpochMs: Long)
    suspend fun attempt(attemptId: Long): QuizAttemptRecord?
    suspend fun answers(attemptId: Long): List<QuizAnswerRecord>
    suspend fun unfinishedExam(): QuizAttemptRecord?
}

private val orderSerializer = ListSerializer(String.serializer())

class RoomQuizRepository(private val dao: StudyDao) : QuizRepository {
    override suspend fun startAttempt(examId: String?, seed: Long, questionOrder: List<String>, startedAtEpochMs: Long): Long =
        dao.startAttempt(
            QuizAttemptEntity(
                examId = examId, seed = seed, questionOrderJson = StateJson.encodeToString(orderSerializer, questionOrder),
                startedAtEpochMs = startedAtEpochMs,
            ),
        )

    override suspend fun saveAnswer(answer: QuizAnswerRecord) = dao.upsertAnswer(
        QuizAnswerEntity(answer.attemptId, answer.questionId, answer.chosenIndex, answer.correct, answer.answeredAtEpochMs, answer.scriptShown),
    )

    override suspend fun saveProgress(attemptId: Long, currentIndex: Int, elapsedMs: Long) = dao.updateProgress(attemptId, currentIndex, elapsedMs)
    override suspend fun finish(attemptId: Long, finishedAtEpochMs: Long) = dao.finishAttempt(attemptId, finishedAtEpochMs)
    override suspend fun attempt(attemptId: Long): QuizAttemptRecord? = dao.attemptById(attemptId)?.toRecord()
    override suspend fun answers(attemptId: Long): List<QuizAnswerRecord> = dao.answersOf(attemptId).map {
        QuizAnswerRecord(it.attemptId, it.questionId, it.chosenIndex, it.correct, it.answeredAtEpochMs, it.scriptShown)
    }
    override suspend fun unfinishedExam(): QuizAttemptRecord? = dao.unfinishedExam()?.toRecord()

    private fun QuizAttemptEntity.toRecord() = QuizAttemptRecord(
        id, examId, seed, StateJson.decodeFromString(orderSerializer, questionOrderJson),
        currentIndex, elapsedMs, startedAtEpochMs, finishedAtEpochMs,
    )
}
