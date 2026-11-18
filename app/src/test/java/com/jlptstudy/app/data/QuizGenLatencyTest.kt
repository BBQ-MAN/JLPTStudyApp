package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomQuizGenRepository
import com.jlptstudy.quiz.gen.GenType
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * 첫 은행 생성 시간 측정(QG4, ui-design 6.10 S12 "시작 중 연타"). QUIZGEN_LATENCY_OUT(절대 경로)이 있을 때만 돈다 — verify에서는 건너뛴다.
 * Robolectric JVM 값이라 실기기보다 빠르다(참고용). 급수마다 새 저장소로 은행 만들기(availability) → 세션 만들기 → 문항 내용 만들기를 잰다.
 */
@RunWith(RobolectricTestRunner::class)
class QuizGenLatencyTest {
    @Test fun `첫 은행 생성 시간을 잰다`() = runBlocking {
        val out = System.getenv("QUIZGEN_LATENCY_OUT")
        assumeTrue("QUIZGEN_LATENCY_OUT 없음", !out.isNullOrBlank())
        val db = ContentDatabase.open(RuntimeEnvironment.getApplication())
        val sb = StringBuilder()
        try {
            repeat(3) { round ->
                for (level in listOf(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3, JlptLevel.N2, JlptLevel.N1)) {
                    val gen = RoomQuizGenRepository(db.contentDao())
                    val t0 = System.nanoTime()
                    val avail = gen.availability(level)
                    val t1 = System.nanoTime()
                    val ids = gen.newSession(level, GenType.MEANING_JA_KO, 1L)
                    val t2 = System.nanoTime()
                    val contents = gen.contents(ids)
                    val t3 = System.nanoTime()
                    sb.appendLine("round=$round ${level.name} 은행+availability=${(t1 - t0) / 1_000_000}ms newSession=${(t2 - t1) / 1_000_000}ms contents(${contents.size})=${(t3 - t2) / 1_000_000}ms targets=${avail.map { it.targets }}")
                }
            }
        } finally { db.close() }
        File(out!!).writeText(sb.toString())
    }
}
