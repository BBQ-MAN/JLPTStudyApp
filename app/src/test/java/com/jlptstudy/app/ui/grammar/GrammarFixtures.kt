package com.jlptstudy.app.ui.grammar

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.jlptstudy.app.data.content.ContentDatabase
import org.robolectric.RuntimeEnvironment

// 시험용 합성 문법 행(실제 콘텐츠가 아니다). 인메모리 콘텐츠 DB에 넣어 RoomGrammarRepository를 그대로 쓴다.
const val CONN = """[{"posKo":"동사","form":"て形","example":"{食|た}べて"},{"posKo":"い형용사","form":"〜くて","example":"{高|たか}くて"}]"""
fun contrast(id: String) =
    """[{"grammarId":"$id","pair":[{"ja":"これを[[{見|み}てください]]","ko":"시험 번역 가"},{"ja":"これを[[{見|み}なさい]]","ko":"시험 번역 나"}]}]"""

fun newContentDb(): ContentDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ContentDatabase::class.java).allowMainThreadQueries().build()

fun ContentDatabase.grammar(
    id: String, level: Int = 5, sortKey: Int = 0, title: String = "〜テスト形", meaning: String = "시험용 대응 표현",
    explanation: String = "시험용 첫 문단\n\n시험용 둘째 문단", connections: String = CONN, contrasts: String = "[]",
    koSource: String = "authored", koMethod: String = "llm:test-model@2026-11-03", status: String = "unreviewed",
    reviewedAt: String? = null, reviewer: String? = null, needsReview: Boolean = false,
    examples: List<Pair<String, String>> = listOf("これは[[{本|ほん}です]]" to "시험 번역 1", "あれも[[{本|ほん}です]]" to "시험 번역 2", "それも[[{本|ほん}です]]" to "시험 번역 3"),
) {
    val w = openHelper.writableDatabase
    w.insert("grammar", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("id", id); put("level", level); put("sort_key", sortKey); put("title", title); put("meaning_ko", meaning)
        put("explanation_ko", explanation); put("connections_json", connections); put("contrasts_json", contrasts)
        put("source_id", "authored"); put("ko_source", koSource); put("ko_method", koMethod); put("ko_review_status", status)
        put("ko_reviewed_at", reviewedAt); put("ko_reviewer_kind", reviewer); put("needs_review", if (needsReview) 1 else 0)
    })
    examples.forEachIndexed { i, (ja, ko) ->
        w.insert("grammar_example", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
            put("grammar_id", id); put("ord", i); put("ja", ja); put("ko", ko); put("source_id", "authored")
        })
    }
}

fun ContentDatabase.retire(id: String) {
    openHelper.writableDatabase.insert("retired", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply { put("id", id); put("retired_in", "test") })
}
