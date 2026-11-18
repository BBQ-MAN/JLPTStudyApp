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

// choices_json·answer_index는 contents()가 버릴 행을 pool()에서도 빼려고 함께 읽는다(RoomQuizContentRepository).
data class QuizPoolRow(
    val id: String, val section: String, val type: String,
    @ColumnInfo(name = "choices_json") val choicesJson: String, @ColumnInfo(name = "answer_index") val answerIndex: Int,
)
data class ListeningPoolRow(
    val id: String, val type: String,
    @ColumnInfo(name = "choices_json") val choicesJson: String, @ColumnInfo(name = "answer_index") val answerIndex: Int,
)

data class GrammarTitleRow(val id: String, val title: String)

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

    @Query("SELECT id, level, sort_key FROM grammar WHERE level IN (:levels) AND id NOT IN (SELECT id FROM retired)")
    suspend fun grammarOrder(levels: List<Int>): List<OrderRow>

    @Query("SELECT id, level FROM grammar WHERE id IN (:ids)")
    suspend fun grammarLevelsOf(ids: List<String>): List<IdLevelRow>

    @Query("SELECT * FROM grammar WHERE id IN (:ids)")
    suspend fun grammarByIds(ids: List<String>): List<GrammarEntity>

    @Query("SELECT * FROM grammar_example WHERE ord = 0 AND grammar_id IN (:ids)")
    suspend fun grammarFirstExamples(ids: List<String>): List<GrammarExampleEntity>

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

    @Query("SELECT paths_json FROM kanji_stroke WHERE literal = :literal")
    suspend fun strokePaths(literal: String): String?

    @Query("SELECT * FROM grammar WHERE level IN (:levels) AND id NOT IN (SELECT id FROM retired) ORDER BY level DESC, sort_key, id")
    suspend fun grammarRows(levels: List<Int>): List<GrammarEntity>

    @Query("SELECT DISTINCT level FROM grammar WHERE id NOT IN (SELECT id FROM retired)")
    suspend fun grammarLevels(): List<Int>

    @Query("SELECT * FROM grammar WHERE id = :id AND id NOT IN (SELECT id FROM retired)")
    suspend fun grammarById(id: String): GrammarEntity?

    @Query("SELECT * FROM grammar_example WHERE grammar_id = :id ORDER BY ord")
    suspend fun grammarExamples(id: String): List<GrammarExampleEntity>

    @Query("SELECT id, title FROM grammar WHERE id IN (:ids) AND id NOT IN (SELECT id FROM retired)")
    suspend fun grammarTitles(ids: List<String>): List<GrammarTitleRow>

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

    @Query("SELECT id, section, type, choices_json, answer_index FROM question WHERE level = :level AND id NOT IN (SELECT id FROM retired) ORDER BY sort_key, id")
    suspend fun questionPool(level: Int): List<QuizPoolRow>

    @Query("SELECT id, type, choices_json, answer_index FROM listening WHERE level = :level AND id NOT IN (SELECT id FROM retired) ORDER BY sort_key, id")
    suspend fun listeningPool(level: Int): List<ListeningPoolRow>

    @Query("SELECT * FROM question WHERE id IN (:ids)")
    suspend fun questionsByIds(ids: List<String>): List<QuestionEntity>

    @Query("SELECT * FROM listening WHERE id IN (:ids)")
    suspend fun listeningByIds(ids: List<String>): List<ListeningEntity>

    @Query("SELECT * FROM exam_template WHERE level = :level")
    suspend fun examTemplateRow(level: Int): ExamTemplateEntity?

    @Query("SELECT level FROM exam_template")
    suspend fun examTemplateLevels(): List<Int>

    // quizgen.md 6.2: 자동 생성 연습 문제의 급수별 은행. 급수 하나라 바인드 변수 한도 문제가 없다.
    @Query("SELECT * FROM vocab WHERE level = :level AND id LIKE 'jm:%' AND id NOT IN (SELECT id FROM retired) ORDER BY id")
    suspend fun quizVocab(level: Int): List<VocabEntity>

    @Query("SELECT m.* FROM vocab_meaning m JOIN vocab v ON v.id = m.vocab_id WHERE v.level = :level AND m.ord = 0")
    suspend fun quizMeanings(level: Int): List<VocabMeaningEntity>

    @Query("SELECT * FROM kanji WHERE level >= :level AND literal NOT IN (SELECT id FROM retired)")
    suspend fun quizKanji(level: Int): List<KanjiEntity>

    @Query(
        "SELECT * FROM jm_form WHERE written IN (SELECT written FROM vocab WHERE level = :level AND written IS NOT NULL) " +
            "OR reading IN (SELECT reading FROM vocab WHERE level = :level AND written IS NOT NULL)",
    )
    suspend fun quizForms(level: Int): List<JmFormEntity>

    @Query("SELECT c.* FROM vocab_conflict c JOIN vocab v ON v.id = c.vocab_id WHERE v.level = :level")
    suspend fun quizConflicts(level: Int): List<VocabConflictEntity>

    @Query("SELECT level FROM vocab WHERE id = :id")
    suspend fun vocabLevel(id: String): Int?
}
