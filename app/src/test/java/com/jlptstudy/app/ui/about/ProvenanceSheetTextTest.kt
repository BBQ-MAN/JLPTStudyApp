package com.jlptstudy.app.ui.about

import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.review.MeaningUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProvenanceSheetTextTest {
    private val texts = mapOf(
        "translated-llm" to "TL {model}", "translated-llm-nomodel" to "TLN", "translated-human" to "TH",
        "authored-llm" to "AL ({model})", "authored-llm-nomodel" to "ALN", "authored-human" to "AH", "unknown" to "U",
    )

    private fun m(method: String, source: String) = MeaningUi("뜻", source, method, "unreviewed", null, null)
    private fun text(method: String, source: String) = ProvenanceLabel.sheetText(m(method, source), texts)

    @Test fun `키 7개를 ko_method와 ko_source 조합대로 고른다`() {
        assertEquals("TL claude-x", text("llm:claude-x@2026-11-01", "jmdict-translated"))
        assertEquals("TLN", text("llm:@2026-11-01", "jmdict-translated"))
        assertEquals("TH", text("human", "jmdict-translated"))
        assertEquals("AL (claude-x)", text("llm:claude-x@2026-11-01", "authored"))
        assertEquals("AL (m)", text("llm:m@d", "something-else"))
        assertEquals("ALN", text("llm:@d", "authored"))
        assertEquals("AH", text("human", "authored"))
        assertEquals("U", text("", "authored"))
        assertEquals("U", text("magic", "jmdict-translated"))
    }

    @Test fun `모델 이름이 비면 nomodel 문장을 써서 빈 괄호가 나오지 않는다`() {
        val real = AboutFixtures.bundled["meaningSourceTexts"]!!.let { Json.decodeFromJsonElement<Map<String, String>>(it) }
        for (method in listOf("llm:@2026", "llm:", "llm: @2026")) for (source in listOf("authored", "jmdict-translated")) {
            val t = ProvenanceLabel.sheetText(m(method, source), real)
            assertFalse(t, "AI()" in t)
            assertFalse(t, "{model}" in t)
            assertFalse(t, t.isBlank())
        }
    }

    @Test fun `키가 없으면 빈 문자열이다`() = assertEquals("", ProvenanceLabel.sheetText(m("human", "authored"), emptyMap()))
}
