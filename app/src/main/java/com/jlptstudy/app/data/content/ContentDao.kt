package com.jlptstudy.app.data.content

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

data class LevelCountRow(val level: Int, val n: Int)
data class OrderRow(val id: String, val level: Int, @ColumnInfo(name = "sort_key") val sortKey: Int)
data class IdLevelRow(val id: String, val level: Int)
data class BrowseVocabRow(val id: String, val written: String?, val reading: String, val level: Int, val textKo: String?)
data class BrowseKanjiRow(val literal: String, val level: Int, @ColumnInfo(name = "korean_readings") val koreanReadings: String)
data class KanjiWordRow(val kanji: String, val vocabId: String, val written: String?, val reading: String, val textKo: String?)

data class ReviewCountRow(val status: String?, val kind: String?, val n: Int)

/** SELECT만 둔다(architecture.md 4.5). ID 목록을 받는 함수는 저장소가 900개 이하로 나눠 부른다. */
@Dao
interface ContentDao {
    @Query("SELECT * FROM content_meta")
    suspend fun meta(): List<ContentMetaEntity>

    @Query("SELECT level, COUNT(*) AS n FROM vocab WHERE id NOT IN (SELECT id FROM retired) GROUP BY level")
    suspend fun vocabCounts(): List<LevelCountRow>

    @Query("SELECT level, COUNT(*) AS n FROM kanji WHERE literal NOT IN (SELECT id FROM retired) GROUP BY level")
    suspend fun kanjiCounts(): List<LevelCountRow>

    @Query("SELECT id, level, sort_key FROM vocab WHERE level IN (:levels) AND id NOT IN (SELECT id FROM retired)")
    suspend fun vocabOrder(levels: List<Int>): List<OrderRow>

    @Query("SELECT literal AS id, level, sort_key FROM kanji WHERE level IN (:levels) AND literal NOT IN (SELECT id FROM retired)")
    suspend fun kanjiOrder(levels: List<Int>): List<OrderRow>

    @Query("SELECT * FROM vocab WHERE id IN (:ids)")
    suspend fun vocabByIds(ids: List<String>): List<VocabEntity>

    @Query("SELECT * FROM vocab_meaning WHERE vocab_id IN (:ids) ORDER BY vocab_id, ord")
    suspend fun meaningsByIds(ids: List<String>): List<VocabMeaningEntity>

    @Query("SELECT * FROM kanji WHERE literal IN (:literals)")
    suspend fun kanjiByIds(literals: List<String>): List<KanjiEntity>

    @Query(
        "SELECT ko_review_status AS status, ko_reviewer_kind AS kind, COUNT(*) AS n FROM vocab_meaning " +
            "WHERE vocab_id NOT IN (SELECT id FROM retired) GROUP BY ko_review_status, ko_reviewer_kind",
    )
    suspend fun meaningReviewRows(): List<ReviewCountRow>

    @Query("SELECT id FROM retired WHERE id IN (:ids)")
    suspend fun retiredAmong(ids: List<String>): List<String>

    @Query("SELECT id, level FROM vocab WHERE id IN (:ids)")
    suspend fun vocabLevels(ids: List<String>): List<IdLevelRow>

    @Query("SELECT literal AS id, level FROM kanji WHERE literal IN (:literals)")
    suspend fun kanjiLevels(literals: List<String>): List<IdLevelRow>

    @Query(
        "SELECT vk.kanji AS kanji, v.id AS vocabId, v.written AS written, v.reading AS reading, " +
            "(SELECT m.text_ko FROM vocab_meaning m WHERE m.vocab_id = v.id ORDER BY m.ord LIMIT 1) AS textKo " +
            "FROM vocab_kanji vk JOIN vocab v ON v.id = vk.vocab_id " +
            "WHERE vk.kanji IN (:literals) AND v.id NOT IN (SELECT id FROM retired) " +
            "ORDER BY vk.kanji, v.level DESC, v.sort_key, v.id",
    )
    suspend fun kanjiWords(literals: List<String>): List<KanjiWordRow>

    // :q는 LIKE 이스케이프를 마친 부분 문자열('\\' 이스케이프). 빈 문자열이면 조건 없음.
    @Query(
        "SELECT v.id AS id, v.written AS written, v.reading AS reading, v.level AS level, " +
            "(SELECT m.text_ko FROM vocab_meaning m WHERE m.vocab_id = v.id ORDER BY m.ord LIMIT 1) AS textKo " +
            "FROM vocab v WHERE v.level IN (:levels) AND v.id NOT IN (SELECT id FROM retired) AND (:q = '' " +
            "OR v.written LIKE '%' || :q || '%' ESCAPE '\\' OR v.reading LIKE '%' || :q || '%' ESCAPE '\\' " +
            "OR EXISTS (SELECT 1 FROM vocab_meaning m WHERE m.vocab_id = v.id AND m.text_ko LIKE '%' || :q || '%' ESCAPE '\\')) " +
            "ORDER BY v.level DESC, v.sort_key, v.id",
    )
    suspend fun browseVocab(levels: List<Int>, q: String): List<BrowseVocabRow>

    @Query(
        "SELECT k.literal AS literal, k.level AS level, k.korean_readings AS korean_readings FROM kanji k " +
            "WHERE k.level IN (:levels) AND k.literal NOT IN (SELECT id FROM retired) AND (:q = '' " +
            "OR k.literal LIKE '%' || :q || '%' ESCAPE '\\' OR k.korean_readings LIKE '%' || :q || '%' ESCAPE '\\' " +
            "OR k.on_readings LIKE '%' || :q || '%' ESCAPE '\\' OR k.kun_readings LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY k.level DESC, k.sort_key, k.literal",
    )
    suspend fun browseKanji(levels: List<Int>, q: String): List<BrowseKanjiRow>

    @Query("SELECT kanji FROM vocab_kanji WHERE vocab_id = :id AND kanji NOT IN (SELECT id FROM retired)")
    suspend fun vocabKanji(id: String): List<String>

    @Query("SELECT COUNT(*) FROM kanji_stroke")
    suspend fun countKanjiStroke(): Int

    @Query("SELECT COUNT(*) FROM grammar")
    suspend fun countGrammar(): Int

    @Query("SELECT COUNT(*) FROM grammar_example")
    suspend fun countGrammarExample(): Int

    @Query("SELECT COUNT(*) FROM question")
    suspend fun countQuestion(): Int

    @Query("SELECT COUNT(*) FROM exam_template")
    suspend fun countExamTemplate(): Int

    @Query("SELECT COUNT(*) FROM listening")
    suspend fun countListening(): Int
}
