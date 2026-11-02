package com.jlptstudy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 병합된 매니페스트(processDebugMainManifest 산출물)와 백업 규칙 파일 두 개를 직접 읽는다. */
class ManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val appDir = File(System.getProperty("app.dir")!!)
    private val buildDir = File(System.getProperty("app.build.dir")!!)

    private fun parse(f: File): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f)

    private fun Document.attr(tag: String, name: String): String =
        (getElementsByTagName(tag).item(0) as Element).getAttributeNS(android, name)

    @Test fun `병합된 매니페스트에 INTERNET이 없고 백업 속성이 있다`() {
        val merged = File(buildDir, "intermediates/merged_manifests/debug").walkTopDown()
            .firstOrNull { it.name == "AndroidManifest.xml" }
        assertTrue("병합된 매니페스트를 찾지 못했다: $buildDir", merged != null)
        val doc = parse(merged!!)
        val perms = (0 until doc.getElementsByTagName("uses-permission").length)
            .map { (doc.getElementsByTagName("uses-permission").item(it) as Element).getAttributeNS(android, "name") }
        assertFalse(perms.toString(), "android.permission.INTERNET" in perms)
        assertEquals("true", doc.attr("application", "allowBackup"))
        assertEquals("@xml/data_extraction_rules", doc.attr("application", "dataExtractionRules"))
        assertEquals("@xml/backup_rules", doc.attr("application", "fullBackupContent"))
    }

    @Test fun `두 규칙 파일은 학습 기록 DB 파일만 include한다`() {
        val expected = setOf("study.db", "study.db-wal", "study.db-shm")
        for (name in listOf("data_extraction_rules.xml", "backup_rules.xml")) {
            val doc = parse(File(appDir, "src/main/res/xml/$name"))
            val includes = (0 until doc.getElementsByTagName("include").length)
                .map { doc.getElementsByTagName("include").item(it) as Element }
            assertTrue(name, includes.isNotEmpty())
            assertTrue(name, includes.all { it.getAttribute("domain") == "database" })
            assertEquals(name, expected, includes.map { it.getAttribute("path") }.toSet())
            assertFalse(name, includes.any { it.getAttribute("path").startsWith("content_") })
        }
    }
}
