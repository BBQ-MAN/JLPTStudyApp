package com.jlptstudy.app.data

import com.jlptstudy.app.data.study.StudyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StudyDbCorruptionTest {
    @Test fun corruptFileFailsToOpenAndIsNotDeleted() {
        val ctx = RuntimeEnvironment.getApplication()
        val file = ctx.getDatabasePath(StudyDatabase.FILE_NAME)
        file.parentFile!!.mkdirs()
        val junk = ByteArray(4096) { 0x5A }
        file.writeBytes(junk)
        try {
            runBlocking { StudyDatabase.open(ctx) }
            fail("열기가 실패해야 한다")
        } catch (e: Throwable) {
            if (e is AssertionError) throw e
        }
        assertTrue(file.exists())
        assertEquals(junk.size.toLong(), file.length())
    }
}
