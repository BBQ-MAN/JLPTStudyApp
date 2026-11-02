package com.jlptstudy.app

import com.jlptstudy.app.data.content.ContentMeta
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.KanjiDetail
import com.jlptstudy.app.data.content.LevelCounts
import com.jlptstudy.app.data.content.VocabDetail
import com.jlptstudy.app.ui.dictionary.BrowseItem
import com.jlptstudy.app.ui.dictionary.BrowseTab
import com.jlptstudy.app.data.content.NewCardCandidate
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.data.study.UndoResult
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.ReviewCardUi
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.ScheduleResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object FakeContentRepository : ContentRepository {
    override suspend fun meta() = ContentMeta("sample", "test", setOf(JlptLevel.N5))
    override suspend fun levelCounts(): Map<JlptLevel, LevelCounts> = emptyMap()
    override suspend fun newCardOrder(levels: Set<JlptLevel>, kinds: Set<CardKind>): List<NewCardCandidate> = emptyList()
    override suspend fun cardContents(cardIds: List<String>): Map<String, ReviewCardUi> = emptyMap()
    override suspend fun cardLevels(cardIds: List<String>): Map<String, JlptLevel> = emptyMap()
    override suspend fun browse(tab: BrowseTab, levels: Set<JlptLevel>, query: String): List<BrowseItem> = emptyList()
    override suspend fun vocabDetail(id: String): VocabDetail? = null
    override suspend fun kanjiDetail(literal: String): KanjiDetail? = null
    override suspend fun meaningReviewCounts() = com.jlptstudy.app.data.content.ReviewCounts(0, 0, 0, 0, 0, 0)
}

class FakeStudyRepository(private val settings: UserSettings = UserSettings(onboardingDone = true)) : StudyRepository {
    override suspend fun applyReview(result: ScheduleResult) = Unit
    override suspend fun maxLogId() = 0L
    override suspend fun undoLast(afterLogId: Long): UndoResult? = null
    override suspend fun startedCardIds(): Set<String> = emptySet()
    override suspend fun states(cardIds: Collection<String>): Map<String, CardState> = emptyMap()
    override suspend fun statesOfKinds(kinds: Set<CardKind>): List<CardState> = emptyList()
    override suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long): List<String> = emptyList()
    override fun settings(): Flow<UserSettings> = flowOf(settings)
    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) = Unit
}
