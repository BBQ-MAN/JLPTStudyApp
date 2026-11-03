package com.jlptstudy.app.data

import com.jlptstudy.app.data.content.ContentDao
import com.jlptstudy.app.data.content.GrammarEntity
import com.jlptstudy.app.data.content.GrammarExampleEntity
import com.jlptstudy.app.data.content.GrammarTitleRow
import com.jlptstudy.app.data.content.IdLevelRow
import com.jlptstudy.app.data.content.KanjiEntity
import com.jlptstudy.app.data.content.KanjiWordRow
import com.jlptstudy.app.data.content.LevelCountRow
import com.jlptstudy.app.data.content.ContentMetaEntity
import com.jlptstudy.app.data.content.OrderRow
import com.jlptstudy.app.data.content.ReviewCountRow
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.data.content.VocabEntity
import com.jlptstudy.app.data.content.VocabMeaningEntity
import com.jlptstudy.app.data.study.CardStateEntity
import com.jlptstudy.app.data.study.ReviewLogEntity
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDao
import com.jlptstudy.app.data.study.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가짜 DAO로 ID 목록 조회가 900개 이하 인자로만 불리는지 본다(architecture.md 4.5). */
class ChunkingTest {
    private val ids2000 = (1..2000).map { "vr:jm:$it" }

    @Test fun `states는 900개 이하씩 DAO를 부른다`() = runBlocking {
        val sizes = mutableListOf<Int>()
        val dao = object : StudyDao() {
            override suspend fun statesByIds(cardIds: List<String>): List<CardStateEntity> { sizes += cardIds.size; return emptyList() }
            override suspend fun upsertState(state: CardStateEntity) = TODO()
            override suspend fun insertLog(log: ReviewLogEntity): Long = TODO()
            override suspend fun deleteState(cardId: String) = TODO()
            override suspend fun lastUndoable(afterLogId: Long): ReviewLogEntity? = TODO()
            override suspend fun markUndone(id: Long) = TODO()
            override suspend fun maxLogId(): Long = TODO()
            override suspend fun startedCardIds(): List<String> = TODO()
            override suspend fun statesOfKinds(kinds: List<String>): List<CardStateEntity> = TODO()
            override suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long): List<String> = TODO()
            override fun observeSettings(): Flow<UserSettingsEntity?> = emptyFlow()
            override suspend fun settingsRow(): UserSettingsEntity? = TODO()
            override suspend fun putSettings(settings: UserSettingsEntity) = TODO()
        }
        RoomStudyRepository(dao).states(ids2000)
        assertEquals(2000, sizes.sum())
        assertTrue(sizes.toString(), sizes.all { it <= 900 })
    }

    @Test fun `cardContents는 900개 이하씩 DAO를 부른다`() = runBlocking {
        val calls = mutableMapOf<String, MutableList<Int>>()
        fun rec(name: String, n: Int) { calls.getOrPut(name) { mutableListOf() } += n }
        val dao = object : ContentDao {
            override suspend fun vocabByIds(ids: List<String>): List<VocabEntity> { rec("vocab", ids.size); return emptyList() }
            override suspend fun meaningsByIds(ids: List<String>): List<VocabMeaningEntity> { rec("meanings", ids.size); return emptyList() }
            override suspend fun retiredAmong(ids: List<String>): List<String> { rec("retired", ids.size); return emptyList() }
            override suspend fun kanjiByIds(literals: List<String>): List<KanjiEntity> { rec("kanji", literals.size); return emptyList() }
            override suspend fun kanjiWords(literals: List<String>): List<KanjiWordRow> { rec("words", literals.size); return emptyList() }
            override suspend fun meta(): List<ContentMetaEntity> = TODO()
            override suspend fun vocabCounts(): List<LevelCountRow> = TODO()
            override suspend fun kanjiCounts(): List<LevelCountRow> = TODO()
            override suspend fun vocabOrder(levels: List<Int>): List<OrderRow> = TODO()
            override suspend fun kanjiOrder(levels: List<Int>): List<OrderRow> = TODO()
            override suspend fun grammarOrder(levels: List<Int>): List<OrderRow> = TODO()
            override suspend fun grammarLevelsOf(ids: List<String>): List<IdLevelRow> = TODO()
            override suspend fun grammarByIds(ids: List<String>): List<GrammarEntity> = TODO()
            override suspend fun grammarFirstExamples(ids: List<String>): List<GrammarExampleEntity> = TODO()
            override suspend fun vocabLevels(ids: List<String>): List<IdLevelRow> = TODO()
            override suspend fun kanjiLevels(literals: List<String>): List<IdLevelRow> = TODO()
            override suspend fun browseVocab(levels: List<Int>, q: String) = TODO()
            override suspend fun browseKanji(levels: List<Int>, q: String) = TODO()
            override suspend fun vocabKanji(id: String): List<String> = TODO()
            override suspend fun meaningReviewRows(): List<ReviewCountRow> = TODO()
            override suspend fun countKanjiStroke(): Int = TODO()
            override suspend fun strokePaths(literal: String): String? = TODO()
            override suspend fun grammarRows(levels: List<Int>): List<GrammarEntity> = TODO()
            override suspend fun grammarLevels(): List<Int> = TODO()
            override suspend fun grammarById(id: String): GrammarEntity? = TODO()
            override suspend fun grammarExamples(id: String): List<GrammarExampleEntity> = TODO()
            override suspend fun grammarTitles(ids: List<String>): List<GrammarTitleRow> = TODO()
            override suspend fun countGrammar(): Int = TODO()
            override suspend fun countGrammarExample(): Int = TODO()
            override suspend fun countQuestion(): Int = TODO()
            override suspend fun countExamTemplate(): Int = TODO()
            override suspend fun countListening(): Int = TODO()
        }
        RoomContentRepository(dao).cardContents(ids2000 + (1..1000).map { "kr:字$it" })
        for (name in listOf("vocab", "meanings", "retired", "kanji", "words")) {
            val sizes = calls.getValue(name)
            assertTrue("$name $sizes", sizes.all { it <= 900 })
        }
        assertEquals(2000, calls.getValue("vocab").sum())
        assertEquals(1000, calls.getValue("kanji").sum())
        assertEquals(3000, calls.getValue("retired").sum())
    }
}
