package com.jlptstudy.app.data

import com.jlptstudy.app.data.content.ContentDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ContentDbTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `번들 에셋을 createFromAsset으로 열면 vocab 행이 있고 kind가 release이다`() = runBlocking {
        val db = ContentDatabase.open(context)
        try {
            val dao = db.contentDao()
            assertEquals("release", dao.meta().first { it.key == "kind" }.value)
            assertTrue(dao.vocabCounts().sumOf { it.n } > 0)
        } finally {
            db.close()
        }
    }

    @Test fun `v2 새 여섯 테이블 count 조회가 실행되고 필순은 채워져 있다`() = runBlocking {
        val db = ContentDatabase.open(context)
        try {
            val d = db.contentDao()
            // 번들 DB는 빌드 산출물이라 행 수는 마일스톤마다 늘어난다. 쿼리가 스키마와 맞는지(실행됨)와 M13의 필순 행만 본다.
            val others = listOf(d.countGrammar(), d.countGrammarExample(), d.countQuestion(), d.countExamTemplate(), d.countListening())
            assertTrue(others.all { it >= 0 })
            assertTrue(d.countKanjiStroke() > 0)
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test fun `옛 content 파일만 지우고 현재 파일과 study_db는 둔다`() {
        val names = listOf("content_old000000.db", "content_cur000000.db", "study.db")
        names.forEach { context.getDatabasePath(it).apply { parentFile?.mkdirs() }.writeText("x") }
        ContentDatabase.deleteStale(context, keepName = "content_cur000000.db")
        assertFalse(context.getDatabasePath("content_old000000.db").exists())
        assertTrue(context.getDatabasePath("content_cur000000.db").exists())
        assertTrue(context.getDatabasePath("study.db").exists())
    }
}
