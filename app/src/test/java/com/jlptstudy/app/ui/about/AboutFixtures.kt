package com.jlptstudy.app.ui.about

import com.jlptstudy.app.data.attribution.AssetAttributionRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** 시험 픽스처: 매니페스트(docs/content/manifest.json)의 자료 항목으로 attribution.json을 만든다. 앱 콘텐츠가 아니다. */
object AboutFixtures {
    val appDir = File(System.getProperty("app.dir")!!)
    val root: File = appDir.parentFile
    val assets = File(appDir, "src/main/assets")
    val manifest: JsonArray = Json.parseToJsonElement(File(root, "docs/content/manifest.json").readText()).jsonArray
    val bundled: JsonObject = Json.parseToJsonElement(File(assets, "content/attribution.json").readText()).jsonObject

    fun manifestSource(id: String): JsonObject = manifest.first { it.jsonObject["id"]!!.jsonPrimitive.content == id }.jsonObject

    private fun sourceJson(id: String): JsonObject {
        val m = manifestSource(id)
        return JsonObject(listOf("id", "name", "version", "attribution", "license", "licenseUrl", "homepage", "modifications", "links", "extraCredits").associateWith { m.getValue(it) })
    }

    fun attributionJson(sourceIds: List<String> = listOf("S-01", "S-02", "S-06")): String = JsonObject(
        bundled + mapOf(
            "sources" to JsonArray(sourceIds.map(::sourceJson)),
        ),
    ).toString()

    fun repo(json: String = attributionJson()) =
        AssetAttributionRepository(openLicense = { File(assets, "licenses/$it").inputStream() }) { json.byteInputStream() }
}
