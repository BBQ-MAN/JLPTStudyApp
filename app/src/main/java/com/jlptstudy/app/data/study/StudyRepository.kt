package com.jlptstudy.app.data.study

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.data.content.MAX_SQL_VARIABLES
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Phase
import com.jlptstudy.srs.ScheduleResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserSettings(
    val dayBoundaryMinutes: Int = 240,
    val newCardsPerDay: Int = 10,
    val extraNewCards: Int = 0,
    val extraNewCardsStudyDay: Long? = null,
    val targetLevels: Set<JlptLevel> = setOf(JlptLevel.N5),
    val desiredRetention: Double = 0.9,
    val maxIntervalDays: Int = 36500,
    val learnAheadMinutes: Int = 20,
    val vocabRecognitionEnabled: Boolean = true,
    val kanjiCardsEnabled: Boolean = true,
    val vocabRecallEnabled: Boolean = false,
    val grammarCardsEnabled: Boolean = true,
    val frontFurigana: Boolean = false,
    val bodyFurigana: Boolean = true,
    val listeningScriptFirst: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = false,
    val firstNoticeDismissed: Boolean = false,
) {
    val enabledKinds: Set<CardKind>
        get() = buildSet {
            if (vocabRecognitionEnabled) add(CardKind.VR)
            if (vocabRecallEnabled) add(CardKind.VP)
            if (kanjiCardsEnabled) add(CardKind.KR)
            if (grammarCardsEnabled) add(CardKind.GR)
        }

    /** 그 학습일의 새 카드 한도 = 설정값 + (오늘 추가분이면) extra_new_cards. */
    fun newLimit(studyDay: Long): Int = newCardsPerDay + if (extraNewCardsStudyDay == studyDay) extraNewCards else 0
}

data class UndoResult(val cardId: String, val restored: CardState?) // restored == null: 새 카드였음(행 삭제)

interface StudyRepository {
    suspend fun applyReview(result: ScheduleResult)
    suspend fun maxLogId(): Long
    suspend fun undoLast(afterLogId: Long): UndoResult?
    suspend fun startedCardIds(): Set<String>
    suspend fun states(cardIds: Collection<String>): Map<String, CardState>
    suspend fun statesOfKinds(kinds: Set<CardKind>): List<CardState>
    suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long): List<String>
    fun settings(): Flow<UserSettings>
    /** 범위 밖 값·카드 종류 전부 끔·빈 target_levels는 IllegalArgumentException. */
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)
}

/** 모든 쓰기는 실패 시 예외를 그대로 던진다(Result로 감싸지 않는다). */
class RoomStudyRepository(
    private val dao: StudyDao,
    /** 번들에 문법 행이 있는가. 있어야 문법(gr)만 켜 둔 설정이 유효하다. */
    private val grammarAvailable: suspend () -> Boolean = { false },
) : StudyRepository {
    override suspend fun applyReview(result: ScheduleResult) {
        val r = result.record
        dao.applyReview(
            CardStateEntity(result.state),
            ReviewLogEntity(
                cardId = r.cardId, reviewedAtEpochMs = r.reviewedAtEpochMs, rating = r.rating.value,
                phaseBefore = r.phaseBefore.value, elapsedDays = r.elapsedDays, scheduledDays = r.scheduledDays,
                stateBeforeJson = StateJson.encodeToString(CardState.serializer(), r.before),
                stateAfterJson = StateJson.encodeToString(CardState.serializer(), r.after),
                schedulerVersion = r.schedulerVersion,
            ),
        )
    }

    override suspend fun maxLogId(): Long = dao.maxLogId()

    override suspend fun undoLast(afterLogId: Long): UndoResult? {
        val log = dao.undoLast(afterLogId) ?: return null
        val before = StateJson.decodeFromString(CardState.serializer(), log.stateBeforeJson)
        return UndoResult(log.cardId, before.takeIf { it.phase != Phase.NEW })
    }

    override suspend fun startedCardIds(): Set<String> = dao.startedCardIds().toSet()

    override suspend fun states(cardIds: Collection<String>): Map<String, CardState> =
        cardIds.distinct().chunked(MAX_SQL_VARIABLES)
            .flatMap { dao.statesByIds(it) }
            .associate { it.cardId to it.toCardState() }

    override suspend fun statesOfKinds(kinds: Set<CardKind>): List<CardState> =
        if (kinds.isEmpty()) emptyList() else dao.statesOfKinds(kinds.map { it.code }).map { it.toCardState() }

    override suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long): List<String> =
        dao.introducedToday(fromEpochMs, toEpochMs)

    override fun settings(): Flow<UserSettings> = dao.observeSettings().map { (it ?: UserSettingsEntity()).toDomain() }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val gr = grammarAvailable()
        dao.updateSettings { old ->
            val oldDomain = old.toDomain()
            val new = transform(oldDomain)
            validate(new, oldDomain, gr)
            new.toEntity()
        }
    }

    private fun validate(s: UserSettings, old: UserSettings, grammarAvailable: Boolean) {
        require(s.dayBoundaryMinutes in 0..720) { "day_boundary_minutes" }
        require(s.newCardsPerDay in 5..50 && s.newCardsPerDay % 5 == 0) { "new_cards_per_day" }
        require(s.extraNewCards >= 0) { "extra_new_cards" }
        require(s.desiredRetention in 0.70..0.97) { "desired_retention" }
        require(s.maxIntervalDays >= 1) { "max_interval_days" }
        require(s.learnAheadMinutes >= 0) { "learn_ahead_minutes" }
        require((if (grammarAvailable) s.enabledKinds else s.enabledKinds - CardKind.GR).isNotEmpty()) { "at least one card kind must be enabled" }
        // 손상된 복원 값(빈 급수)에서도 다른 설정은 저장되도록, 급수는 바꿀 때만 검사한다.
        require(s.targetLevels.isNotEmpty() || s.targetLevels == old.targetLevels) { "target_levels must not be empty" }
    }
}

private fun UserSettingsEntity.toDomain() = UserSettings(
    dayBoundaryMinutes = dayBoundaryMinutes, newCardsPerDay = newCardsPerDay, extraNewCards = extraNewCards,
    extraNewCardsStudyDay = extraNewCardsStudyDay,
    // 읽을 때 1..5 밖 값은 버린다(5절).
    targetLevels = targetLevels.split(',').mapNotNull { it.trim().toIntOrNull() }.mapNotNull(JlptLevel::fromN).toSet(),
    desiredRetention = desiredRetention, maxIntervalDays = maxIntervalDays, learnAheadMinutes = learnAheadMinutes,
    vocabRecognitionEnabled = vocabRecognitionEnabled, kanjiCardsEnabled = kanjiCardsEnabled,
    vocabRecallEnabled = vocabRecallEnabled, grammarCardsEnabled = grammarCardsEnabled,
    frontFurigana = frontFurigana, bodyFurigana = bodyFurigana, listeningScriptFirst = listeningScriptFirst,
    theme = ThemeMode.fromCode(theme), onboardingDone = onboardingDone, firstNoticeDismissed = firstNoticeDismissed,
)

private fun UserSettings.toEntity() = UserSettingsEntity(
    dayBoundaryMinutes = dayBoundaryMinutes, newCardsPerDay = newCardsPerDay, extraNewCards = extraNewCards,
    extraNewCardsStudyDay = extraNewCardsStudyDay,
    targetLevels = targetLevels.map { it.n }.sortedDescending().joinToString(","),
    desiredRetention = desiredRetention, maxIntervalDays = maxIntervalDays, learnAheadMinutes = learnAheadMinutes,
    vocabRecognitionEnabled = vocabRecognitionEnabled, kanjiCardsEnabled = kanjiCardsEnabled,
    vocabRecallEnabled = vocabRecallEnabled, grammarCardsEnabled = grammarCardsEnabled,
    frontFurigana = frontFurigana, bodyFurigana = bodyFurigana, listeningScriptFirst = listeningScriptFirst,
    theme = theme.code, onboardingDone = onboardingDone, firstNoticeDismissed = firstNoticeDismissed,
)
