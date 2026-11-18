package com.jlptstudy.app.ui.about

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutFilesTest {
    private val main = File(AboutFixtures.appDir, "src/main")
    private val libs get() = Json.parseToJsonElement(File(AboutFixtures.assets, "licenses/libraries.json").readText()).jsonArray.map { it.jsonObject }

    /** ic_ms_<이름>[_fill].xml의 기본 이름이 목록 밖이거나 규칙 밖인 파일 이름들. */
    private fun badIcons(fileNames: List<String>, allowed: Set<String>) = fileNames.filter {
        it.startsWith("ic_ms_") && it.endsWith(".xml") &&
            it.removePrefix("ic_ms_").removeSuffix(".xml").removeSuffix("_fill") !in allowed
    }

    @Test fun `ic_ms 아이콘의 기본 이름은 모두 S-11 usedFields에 있다`() {
        val allowed = AboutFixtures.manifestSource("S-11")["usedFields"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        val files = File(main, "res/drawable").list()!!.toList()
        assertTrue(files.any { it.startsWith("ic_ms_") })
        assertEquals(emptyList<String>(), badIcons(files, allowed))
    }

    @Test fun `번들 attribution_json의 자료 항목은 매니페스트와 같고 S-01에 자동 생성 문제 가공 내용이 있다`() {
        val bundled = AboutFixtures.bundled["sources"]!!.jsonArray.map { it.jsonObject }
        val inManifest = AboutFixtures.manifest.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        assertTrue(bundled.isNotEmpty())
        for (b in bundled.filter { it["id"]!!.jsonPrimitive.content in inManifest }) { // 'authored'는 매니페스트 밖(앱 작성 콘텐츠)
            val id = b["id"]!!.jsonPrimitive.content
            for (f in listOf("attribution", "license", "licenseUrl", "modifications")) {
                assertEquals("$id.$f", AboutFixtures.manifestSource(id)[f]!!.jsonPrimitive.content, b[f]!!.jsonPrimitive.content)
            }
        }
        val mod = bundled.first { it["id"]!!.jsonPrimitive.content == "S-01" }["modifications"]!!.jsonPrimitive.content
        assertTrue(listOf("jm_form", "vocab_conflict", "xref", "자동 생성 문제").all { it in mod })
    }

    @Test fun `목록 밖 이름의 아이콘 픽스처는 실패한다`() {
        val allowed = setOf("home", "settings")
        assertEquals(listOf("ic_ms_foo.xml", "ic_ms_foo_fill.xml"), badIcons(listOf("ic_ms_home.xml", "ic_ms_settings_fill.xml", "ic_ms_foo.xml", "ic_ms_foo_fill.xml"), allowed))
    }

    @Test fun `libs_versions_toml의 libraries 키는 모두 libraries_json에 있고 Material Symbols 항목이 있다`() {
        val toml = File(AboutFixtures.root, "gradle/libs.versions.toml").readText()
        val keys = toml.substringAfter("[libraries]").substringBefore("[plugins]").lines()
            .mapNotNull { Regex("^([\\w-]+)\\s*=").find(it.trim())?.groupValues?.get(1) }
        assertTrue(keys.size > 10)
        val have = libs.map { it["key"]!!.jsonPrimitive.content }.toSet()
        assertEquals(emptyList<String>(), keys.filter { it !in have })
        assertTrue(libs.any { it["name"]!!.jsonPrimitive.content == "Material Symbols" })
        assertTrue(libs.all { it.keys.containsAll(listOf("name", "license", "url", "distributed")) })
    }

    @Test fun `라이선스 전문 파일에 MIT와 Apache 전문이 있다`() {
        val mit = File(AboutFixtures.assets, "licenses/py-fsrs.txt").readText()
        assertTrue("Permission is hereby granted, free of charge" in mit && "Open Spaced Repetition" in mit)
        val apache = File(AboutFixtures.assets, "licenses/apache-2.0.txt").readText()
        assertTrue("Apache License" in apache && "Version 2.0, January 2004" in apache && "END OF TERMS AND CONDITIONS" in apache)
    }

    @Test fun `화면 코드와 리소스에 출처 문구와 사전 확인 호출이 없다`() {
        val words = listOf("EDRDG", "JMdict", "KANJIDIC", "Tatoeba", "KanjiVG", "Waller")
        val files = File(main, "java").walkTopDown().filter { it.isFile } +
            File(main, "res").walkTopDown().filter { it.isFile && it.name != "attribution_fallback.xml" }
        val hits = files.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (words.any { it in l }) "${f.name}:${i + 1}" else null } }.toList()
        assertEquals(emptyList<String>(), hits)
        val resolve = File(main, "java").walkTopDown().filter { it.isFile }
            .filter { f -> f.readText().let { "resolveActivity" in it || "queryIntentActivities" in it } }.map { it.name }.toList()
        assertEquals(emptyList<String>(), resolve)
    }
}
