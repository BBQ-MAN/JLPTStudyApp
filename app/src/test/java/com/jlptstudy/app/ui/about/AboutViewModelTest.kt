package com.jlptstudy.app.ui.about

import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.data.attribution.LicenseUi
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.ReviewCounts
import com.jlptstudy.app.data.content.RoomContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AboutViewModelTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun AboutViewModel.loaded() = withTimeout(10_000) { state.first { it.status != AboutStatus.LOADING } }
    private fun settled(vm: AboutViewModel) = runBlocking { vm.loaded() }

    private fun bundledRepo() = com.jlptstudy.app.data.attribution.AssetAttributionRepository.create(context)

    @Test fun `번들 DB의 모든 source_id와 level_source_id가 화면 상태 sources에 있고 고지는 에셋 그대로다`() = runBlocking {
        val db = ContentDatabase.open(context)
        try {
            val sql = db.openHelper.readableDatabase
            val used = listOf(
                "SELECT source_id FROM vocab", "SELECT level_source_id FROM vocab", "SELECT source_id FROM vocab_meaning",
                "SELECT source_id FROM kanji", "SELECT source_id FROM vocab_kanji",
            ).flatMap { q -> sql.query(q).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() } }.toSet()
            assertTrue(used.isNotEmpty())
            val content = RoomContentRepository(db.contentDao())
            val vm = AboutViewModel(bundledRepo(), content)
            val s = vm.loaded()
            assertEquals(AboutStatus.LOADED, s.status)
            assertEquals(emptyList<String>(), used.filter { id -> s.sources.none { it.id == id } })

            val json = AboutFixtures.bundled
            val expected = json["notices"]!!.jsonArray.map { Triple(it.jsonObject["id"]!!.jsonPrimitive.content, it.jsonObject["title"]!!.jsonPrimitive.content, it.jsonObject["body"]!!.jsonPrimitive.content) }
            assertEquals(expected, s.notices.map { Triple(it.id, it.title, it.body) })
            assertFalse(s.notices.any { it.id == "N-08" })
            assertEquals(json["dbLicense"]!!.jsonObject["name"]!!.jsonPrimitive.content, s.dbLicense!!.label)
            assertEquals(json["dbLicense"]!!.jsonObject["url"]!!.jsonPrimitive.content, s.dbLicense!!.url)
            assertEquals(json["modelNames"]!!.jsonArray.map { it.jsonPrimitive.content }, s.modelNames)
            assertEquals(content.meaningReviewCounts(), s.reviewCounts)
            assertTrue(s.openSourceLicenses.any { it.name == "py-fsrs" && it.fullText!!.contains("MIT License") })
            assertTrue(s.openSourceLicenses.any { it.fullTextAsset == "apache-2.0.txt" && it.fullText!!.contains("Apache License") })
        } finally {
            db.close()
        }
    }

    @Test fun `extraCredits 문구는 매니페스트 값과 글자 그대로 같다`() = runBlocking {
        val vm = AboutViewModel(AboutFixtures.repo(), null)
        val s = vm.loaded()
        for (id in listOf("S-02", "S-06")) {
            val want = AboutFixtures.manifestSource(id)["extraCredits"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }
            assertTrue(id, want.isNotEmpty())
            assertEquals(want, s.sources.first { it.id == id }.extraCredits.map { it.text })
        }
    }

    @Test fun `attribution_json이 깨졌으면 FAILED이다`() {
        val vm = AboutViewModel(AboutFixtures.repo("{ broken"), FakeContentRepository)
        val s = settled(vm)
        assertEquals(AboutStatus.FAILED, s.status)
        assertTrue(s.sources.isEmpty())
    }

    @Test fun `콘텐츠 저장소가 없거나 집계가 실패해도 LOADED이고 reviewCounts만 null이다`() {
        val none = settled(AboutViewModel(AboutFixtures.repo(), null))
        assertEquals(AboutStatus.LOADED, none.status)
        assertNull(none.reviewCounts)
        assertTrue(none.sources.isNotEmpty() && none.notices.isNotEmpty())

        val throwing = object : ContentRepository by FakeContentRepository {
            override suspend fun meaningReviewCounts(): ReviewCounts = throw java.io.IOException("db")
        }
        val s = settled(AboutViewModel(AboutFixtures.repo(), throwing))
        assertEquals(AboutStatus.LOADED, s.status)
        assertNull(s.reviewCounts)
        assertEquals(none.sources, s.sources)
    }

    @Test fun `라이선스 에셋을 못 읽어도 LOADED이고 목록만 빈다`() {
        val repo = object : com.jlptstudy.app.data.attribution.AttributionRepository by AboutFixtures.repo() {
            override suspend fun openSourceLicenses(): List<LicenseUi> = throw java.io.IOException("x")
        }
        val s = settled(AboutViewModel(repo, null))
        assertEquals(AboutStatus.LOADED, s.status)
        assertTrue(s.openSourceLicenses.isEmpty())
    }
}
