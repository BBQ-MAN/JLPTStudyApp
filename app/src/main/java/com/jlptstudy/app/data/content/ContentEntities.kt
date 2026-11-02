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
