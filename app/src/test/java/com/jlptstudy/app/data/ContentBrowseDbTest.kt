package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.ui.dictionary.BrowseItem
import com.jlptstudy.app.ui.dictionary.BrowseTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 번들 샘플 DB로 browse·상세 SQL을 확인한다(내용은 DB에서 읽은 값만 쓴다). */
@RunWith(RobolectricTestRunner::class)
class ContentBrowseDbTest {
    private fun <T> withRepo(body: suspend (RoomContentRepository) -> T): T = runBlocking {
        val db = ContentDatabase.open(RuntimeEnvironment.getApplication())
        try { body(RoomContentRepository(db.contentDao())) } finally { db.close() }
    }

    @Test fun `빈 급수는 전부 보이고 검색어는 표기 읽기 뜻 중 하나로 좁힌다`() = withRepo { repo ->
        val all = repo.browse(BrowseTab.VOCAB, emptySet(), "").filterIsInstance<BrowseItem.Word>()
        assertTrue(all.isNotEmpty())
        val first = all.first()
        val byReading = repo.browse(BrowseTab.VOCAB, emptySet(), first.reading).map { it.id }
        assertTrue(first.id in byReading)
        first.meaningKo?.let { m ->
            assertTrue(first.id in repo.browse(BrowseTab.VOCAB, emptySet(), m.take(2)).map { it.id })
        }
        assertEquals(emptyList<BrowseItem>(), repo.browse(BrowseTab.VOCAB, emptySet(), "%_\\없는말"))
        assertEquals(emptyList<BrowseItem>(), repo.browse(BrowseTab.VOCAB, setOf(JlptLevel.N5), "%")) // % 는 와일드카드가 아니다
    }

    @Test fun `단어와 한자 상세가 읽히고 없는 ID는 null이다`() = withRepo { repo ->
        val w = repo.browse(BrowseTab.VOCAB, emptySet(), "").first()
        val d = repo.vocabDetail(w.id)
        assertNotNull(d)
        assertTrue(d!!.meanings.isNotEmpty())
        val k = repo.browse(BrowseTab.KANJI, emptySet(), "").filterIsInstance<BrowseItem.Kanji>().firstOrNull()
        if (k != null) {
            val kd = repo.kanjiDetail(k.literal)!!
            assertTrue(kd.strokes!!.isNotEmpty()) // 번들 DB에 필순이 있다(M13)
            assertEquals(k.level, kd.level)
        }
        assertNull(repo.vocabDetail("없는-id"))
        assertNull(repo.kanjiDetail("없"))
    }
}
