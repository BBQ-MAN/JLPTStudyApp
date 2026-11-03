package com.jlptstudy.app.data.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// architecture.md 4.2. 이 엔티티가 파이프라인 스키마의 원본이다(app/schemas/.../1.json).

@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "vocab", indices = [Index("level", "sort_key")])
data class VocabEntity(
    @PrimaryKey val id: String,
    val written: String?,
    val reading: String,
    val level: Int,
    @ColumnInfo(name = "sort_key") val sortKey: Int,
    val pos: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "level_source_id") val levelSourceId: String,
)

@Entity(tableName = "vocab_meaning", primaryKeys = ["vocab_id", "ord"])
data class VocabMeaningEntity(
    @ColumnInfo(name = "vocab_id") val vocabId: String,
    val ord: Int,
    @ColumnInfo(name = "text_ko") val textKo: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "ko_source") val koSource: String,
    @ColumnInfo(name = "ko_method") val koMethod: String,
    @ColumnInfo(name = "ko_review_status") val koReviewStatus: String,
    @ColumnInfo(name = "ko_reviewed_at") val koReviewedAt: String?,
    @ColumnInfo(name = "ko_reviewer_kind") val koReviewerKind: String?,
)

@Entity(tableName = "kanji", indices = [Index("level", "sort_key")])
data class KanjiEntity(
    @PrimaryKey val literal: String,
    val level: Int,
    @ColumnInfo(name = "stroke_count") val strokeCount: Int,
    val freq: Int?,
    @ColumnInfo(name = "on_readings") val onReadings: String,
    @ColumnInfo(name = "kun_readings") val kunReadings: String,
    @ColumnInfo(name = "korean_readings") val koreanReadings: String,
    @ColumnInfo(name = "sort_key") val sortKey: Int,
    @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(tableName = "vocab_kanji", primaryKeys = ["kanji", "vocab_id"], indices = [Index("kanji")])
data class VocabKanjiEntity(
    val kanji: String,
    @ColumnInfo(name = "vocab_id") val vocabId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(tableName = "retired")
data class RetiredEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "retired_in") val retiredIn: String,
)

// architecture.md 9.0~9.4 (콘텐츠 DB v2). 출처 열 묶음 P = 4.2 끝 7열.

@Entity(tableName = "kanji_stroke")
data class KanjiStrokeEntity(
    @PrimaryKey val literal: String,
    @ColumnInfo(name = "paths_json") val pathsJson: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(tableName = "grammar", indices = [Index("level", "sort_key")])
data class GrammarEntity(
    @PrimaryKey val id: String,
    val level: Int,
    @ColumnInfo(name = "sort_key") val sortKey: Int,
    val title: String,
    @ColumnInfo(name = "meaning_ko") val meaningKo: String,
    @ColumnInfo(name = "explanation_ko") val explanationKo: String,
    @ColumnInfo(name = "connections_json") val connectionsJson: String,
    @ColumnInfo(name = "contrasts_json") val contrastsJson: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "ko_source") val koSource: String,
    @ColumnInfo(name = "ko_method") val koMethod: String,
    @ColumnInfo(name = "ko_review_status") val koReviewStatus: String,
    @ColumnInfo(name = "ko_reviewed_at") val koReviewedAt: String?,
    @ColumnInfo(name = "ko_reviewer_kind") val koReviewerKind: String?,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
)

@Entity(tableName = "grammar_example", primaryKeys = ["grammar_id", "ord"])
data class GrammarExampleEntity(
    @ColumnInfo(name = "grammar_id") val grammarId: String,
    val ord: Int,
    val ja: String,
    val ko: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_ref") val sourceRef: String?,
    val author: String?,
    val license: String?,
)

@Entity(tableName = "question", indices = [Index("level", "section", "type", "sort_key")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    val level: Int,
    val section: String,
    val type: String,
    @ColumnInfo(name = "sort_key") val sortKey: Int,
    val stem: String,
    val passage: String?,
    val target: String?,
    @ColumnInfo(name = "choices_json") val choicesJson: String,
    @ColumnInfo(name = "answer_index") val answerIndex: Int,
    @ColumnInfo(name = "explanation_ko") val explanationKo: String,
    @ColumnInfo(name = "why_wrong_ko_json") val whyWrongKoJson: String,
    @ColumnInfo(name = "targets_json") val targetsJson: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "ko_source") val koSource: String,
    @ColumnInfo(name = "ko_method") val koMethod: String,
    @ColumnInfo(name = "ko_review_status") val koReviewStatus: String,
    @ColumnInfo(name = "ko_reviewed_at") val koReviewedAt: String?,
    @ColumnInfo(name = "ko_reviewer_kind") val koReviewerKind: String?,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
)

@Entity(tableName = "exam_template")
data class ExamTemplateEntity(
    @PrimaryKey val level: Int,
    @ColumnInfo(name = "template_json") val templateJson: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "checked_at") val checkedAt: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(tableName = "listening", indices = [Index("level", "type", "sort_key")])
data class ListeningEntity(
    @PrimaryKey val id: String,
    val level: Int,
    @ColumnInfo(name = "sort_key") val sortKey: Int,
    val type: String,
    @ColumnInfo(name = "lines_json") val linesJson: String,
    val question: String?,
    @ColumnInfo(name = "choices_json") val choicesJson: String,
    @ColumnInfo(name = "answer_index") val answerIndex: Int,
    @ColumnInfo(name = "explanation_ko") val explanationKo: String,
    @ColumnInfo(name = "why_wrong_ko_json") val whyWrongKoJson: String,
    @ColumnInfo(name = "targets_json") val targetsJson: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "ko_source") val koSource: String,
    @ColumnInfo(name = "ko_method") val koMethod: String,
    @ColumnInfo(name = "ko_review_status") val koReviewStatus: String,
    @ColumnInfo(name = "ko_reviewed_at") val koReviewedAt: String?,
    @ColumnInfo(name = "ko_reviewer_kind") val koReviewerKind: String?,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
)
