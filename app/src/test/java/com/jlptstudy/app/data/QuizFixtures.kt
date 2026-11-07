package com.jlptstudy.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.jlptstudy.app.data.content.ContentDatabase

// 합성 문항(실제 콘텐츠가 아니다).
fun ContentDatabase.question(
    id: String, level: Int = 5, section: String = "vocab", type: String = "kanji-reading", choices: String = """["가","나","다","라"]""",
    answer: Int = 1,
) {
    openHelper.writableDatabase.insert("question", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("id", id); put("level", level); put("section", section); put("type", type); put("sort_key", 1); put("stem", "시험 문항")
        put("choices_json", choices); put("answer_index", answer); put("explanation_ko", "시험 해설\n\n둘째"); put("why_wrong_ko_json", """[null,"x","y","z"]""")
        put("targets_json", "[]"); put("source_id", "authored"); put("ko_source", "authored"); put("ko_method", "human")
        put("ko_review_status", "unreviewed"); put("needs_review", 0)
    })
}

fun ContentDatabase.examTemplate(level: Int, json: String) {
    openHelper.writableDatabase.insert("exam_template", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("level", level); put("template_json", json); put("source_url", "https://example.invalid"); put("checked_at", "2026-11-03"); put("source_id", "authored")
    })
}

// 합성 청해 행(실제 콘텐츠가 아니다). linesJson을 깨뜨려 읽기 실패를 시험할 수 있다.
fun ContentDatabase.listening(
    id: String, level: Int = 5, type: String = "task", linesJson: String = """[{"speaker":"M","text":"{今日|きょう}は","ttsText":"きょうは"},{"speaker":"F","text":"はい"}]""",
    question: String? = "どうしますか。", choices: String = """["가","나","다","라"]""", answer: Int = 1,
) {
    openHelper.writableDatabase.insert("listening", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("id", id); put("level", level); put("sort_key", id.takeLast(4).toInt()); put("type", type); put("lines_json", linesJson); put("question", question)
        put("choices_json", choices); put("answer_index", answer); put("explanation_ko", "시험 해설\n\n둘째"); put("why_wrong_ko_json", """[null,"x","y","z"]""")
        put("targets_json", "[]"); put("source_id", "authored"); put("ko_source", "authored"); put("ko_method", "human")
        put("ko_review_status", "unreviewed"); put("needs_review", 0)
    })
}
