package com.jlptstudy.app.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** app/schemas/.../2.json 을 architecture.md 9.1·9.2.3·9.3.1·9.4 표의 기대값과 대조한다. */
class ContentSchemaV2Test {
    private val db: JsonObject = Json.parseToJsonElement(
        File("schemas/com.jlptstudy.app.data.content.ContentDatabase/2.json").readText(),
    ).jsonObject["database"]!!.jsonObject

    private fun entity(table: String) =
        db["entities"]!!.jsonArray.map { it.jsonObject }.first { it["tableName"]!!.jsonPrimitive.content == table }

    // 열 표기: "이름:affinity" 뒤에 '?' 가 있으면 NULL 허용
    private val provenance = listOf(
        "source_id:TEXT", "ko_source:TEXT", "ko_method:TEXT", "ko_review_status:TEXT",
        "ko_reviewed_at:TEXT?", "ko_reviewer_kind:TEXT?", "needs_review:INTEGER",
    )

    private class Expect(val cols: List<String>, val pk: List<String>, val indices: List<List<String>>)

    private val expected = mapOf(
        "kanji_stroke" to Expect(listOf("literal:TEXT", "paths_json:TEXT", "source_id:TEXT"), listOf("literal"), emptyList()),
        "grammar" to Expect(
            listOf(
                "id:TEXT", "level:INTEGER", "sort_key:INTEGER", "title:TEXT", "meaning_ko:TEXT", "explanation_ko:TEXT",
                "connections_json:TEXT", "contrasts_json:TEXT",
            ) + provenance,
            listOf("id"), listOf(listOf("level", "sort_key")),
        ),
        "grammar_example" to Expect(
            listOf(
                "grammar_id:TEXT", "ord:INTEGER", "ja:TEXT", "ko:TEXT", "source_id:TEXT",
                "source_ref:TEXT?", "author:TEXT?", "license:TEXT?",
            ),
            listOf("grammar_id", "ord"), emptyList(),
        ),
        "question" to Expect(
            listOf(
                "id:TEXT", "level:INTEGER", "section:TEXT", "type:TEXT", "sort_key:INTEGER", "stem:TEXT",
                "passage:TEXT?", "target:TEXT?", "choices_json:TEXT", "answer_index:INTEGER", "explanation_ko:TEXT",
                "why_wrong_ko_json:TEXT", "targets_json:TEXT",
            ) + provenance,
            listOf("id"), listOf(listOf("level", "section", "type", "sort_key")),
        ),
        "exam_template" to Expect(
            listOf("level:INTEGER", "template_json:TEXT", "source_url:TEXT", "checked_at:TEXT", "source_id:TEXT"),
            listOf("level"), emptyList(),
        ),
        "listening" to Expect(
            listOf(
                "id:TEXT", "level:INTEGER", "sort_key:INTEGER", "type:TEXT", "lines_json:TEXT", "question:TEXT?",
                "choices_json:TEXT", "answer_index:INTEGER", "explanation_ko:TEXT", "why_wrong_ko_json:TEXT",
                "targets_json:TEXT",
            ) + provenance,
            listOf("id"), listOf(listOf("level", "type", "sort_key")),
        ),
    )

    @Test fun `버전 2와 엔티티 12개`() {
        assertEquals(2, db["version"]!!.jsonPrimitive.int)
        assertEquals(
            setOf("content_meta", "vocab", "vocab_meaning", "kanji", "vocab_kanji", "retired") + expected.keys,
            db["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet(),
        )
        assertEquals(12, db["entities"]!!.jsonArray.size)
    }

    @Test fun `새 여섯 테이블의 열 pk 인덱스가 표와 같다`() {
        for ((table, exp) in expected) {
            val e = entity(table)
            val cols = e["fields"]!!.jsonArray.map {
                val f = it.jsonObject
                f["columnName"]!!.jsonPrimitive.content + ":" + f["affinity"]!!.jsonPrimitive.content +
                    if (f["notNull"]?.jsonPrimitive?.boolean == true) "" else "?"
            }
            assertEquals("$table columns", exp.cols, cols)
            assertEquals("$table pk", exp.pk, (e["primaryKey"] as JsonObject)["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content })
            val idx = (e["indices"] as? JsonArray).orEmpty().map { i ->
                i.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
            }
            assertEquals("$table indices", exp.indices, idx)
        }
    }
}
