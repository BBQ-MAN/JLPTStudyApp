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

/** app/schemas/.../3.json 의 새 표 둘(jm_form·vocab_conflict)을 docs/spec/quizgen.md 3.3 기대값과 대조하고, 1·2.json 이 그대로임을 본다. */
class ContentSchemaV3Test {
    private val dir = "schemas/com.jlptstudy.app.data.content.ContentDatabase"
    private val db: JsonObject = Json.parseToJsonElement(File("$dir/3.json").readText()).jsonObject["database"]!!.jsonObject

    private fun tables(d: JsonObject) = d["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }
    private fun entity(table: String) = db["entities"]!!.jsonArray.map { it.jsonObject }.first { it["tableName"]!!.jsonPrimitive.content == table }

    @Test fun `버전 3과 엔티티 14개는 2_json 12개에 두 표를 더한 것`() {
        assertEquals(3, db["version"]!!.jsonPrimitive.int)
        val v2 = Json.parseToJsonElement(File("$dir/2.json").readText()).jsonObject["database"]!!.jsonObject
        assertEquals(12, tables(v2).size)
        assertEquals(14, tables(db).size)
        assertEquals(tables(v2).toSet() + setOf("jm_form", "vocab_conflict"), tables(db).toSet())
    }

    private class Expect(val cols: List<String>, val pk: List<String>, val indices: List<List<String>>)

    // 열 표기 "이름:affinity", 끝에 '?'가 있으면 NULL 허용. 두 표 모두 전 열 NOT NULL.
    private val expected = mapOf(
        "jm_form" to Expect(listOf("written:TEXT", "reading:TEXT"), listOf("written", "reading"), listOf(listOf("reading"))),
        "vocab_conflict" to Expect(listOf("vocab_id:TEXT", "conflict_ids:TEXT"), listOf("vocab_id"), emptyList()),
    )

    @Test fun `새 두 표의 열 pk 인덱스 notNull이 표와 같다`() {
        for ((table, exp) in expected) {
            val e = entity(table)
            val cols = e["fields"]!!.jsonArray.map {
                val f = it.jsonObject
                f["columnName"]!!.jsonPrimitive.content + ":" + f["affinity"]!!.jsonPrimitive.content +
                    if (f["notNull"]?.jsonPrimitive?.boolean == true) "" else "?"
            }
            assertEquals("$table columns", exp.cols, cols)
            assertEquals("$table pk", exp.pk, (e["primaryKey"] as JsonObject)["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content })
            val idx = (e["indices"] as? JsonArray).orEmpty().map { i -> i.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content } }
            assertEquals("$table indices", exp.indices, idx)
        }
    }

    @Test fun `스키마 파일은 1_2_3 셋이다`() {
        assertEquals(listOf("1.json", "2.json", "3.json"), File(dir).listFiles()!!.map { it.name }.sorted())
    }
}
