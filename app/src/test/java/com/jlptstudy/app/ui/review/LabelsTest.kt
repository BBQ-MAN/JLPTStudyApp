package com.jlptstudy.app.ui.review

import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.srs.IntervalPreview
import com.jlptstudy.srs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration

class LabelsTest {
    private fun step(d: Duration) = IntervalPreview(0, 0, d)
    private fun days(n: Int) = IntervalPreview(0, n, null)

    @Test fun `IntervalFormat은 ui-design 5-3 표의 행과 경계값을 만든다`() {
        val cases = listOf(
            step(Duration.ofSeconds(30)) to "1분 미만", step(Duration.ofSeconds(59)) to "1분 미만",
            step(Duration.ofSeconds(60)) to "1분", step(Duration.ofSeconds(330)) to "6분", step(Duration.ofMinutes(10)) to "10분",
            step(Duration.ofSeconds(59 * 60 + 30)) to "1시간", step(Duration.ofHours(5)) to "5시간", step(Duration.ofHours(23)) to "23시간",
            step(Duration.ofHours(24)) to "1일", step(Duration.ofHours(36)) to "2일",
            days(3) to "3일", days(29) to "29일", days(30) to "1개월", days(63) to "2.1개월",
            days(364) to "12.1개월", days(365) to "1년", days(438) to "1.2년",
        )
        cases.forEach { (p, text) -> assertEquals(text, IntervalFormat.format(p)) }
    }

    @Test fun `onClickLabel 틀은 조사를 붙이지 않고 1분 미만이면 1분 안에`() {
        Rating.entries.forEach {
            assertEquals("평가: ${RatingLabels.name(it)}, 다음 복습 10분 후", RatingLabels.onClickLabel(it, "10분"))
            assertEquals("평가: ${RatingLabels.name(it)}, 다음 복습 1분 안에", RatingLabels.onClickLabel(it, IntervalFormat.format(step(Duration.ofSeconds(30)))))
            assertFalse(RatingLabels.onClickLabel(it, "3일").contains("으로"))
        }
        assertEquals("평가: 다시, 다음 복습 10분 후", RatingLabels.onClickLabel(Rating.AGAIN, "10분"))
    }

    private fun m(method: String, source: String, status: String, reviewer: String?) = MeaningUi("뜻", source, method, status, null, reviewer)

    @Test fun `ProvenanceLabel은 5-7 표의 앞 5 x 뒤 6 조합을 모두 맞춘다`() {
        val front = listOf(
            Triple("llm:model@2026-11-01", "jmdict-translated", "AI 번역"), Triple("llm:model@2026-11-01", "authored", "AI 작성"),
            Triple("human", "jmdict-translated", "사람 번역"), Triple("human", "authored", "직접 작성"),
            Triple("", "authored", "만든 방법 미기록"),
        )
        val back = listOf(
            Triple("unreviewed", "human", "검수 전"), Triple("sampled", null, "표본 검수한 묶음"),
            Triple("reviewed", "human", "사람 검수 완료"), Triple("reviewed", "agent", "AI 검수"),
            Triple("reviewed", null, "검수자 미기록"), Triple("", "human", "검수 상태 미기록"),
        )
        for ((method, source, a) in front) for ((status, reviewer, b) in back) {
            assertEquals("$a · $b", ProvenanceLabel.of(m(method, source, status, reviewer)))
        }
        // 알 수 없는 값
        assertEquals("만든 방법 미기록 · 검수자 미기록", ProvenanceLabel.of(m("magic", "jmdict-translated", "reviewed", "robot")))
        assertEquals("직접 작성 · 검수 상태 미기록", ProvenanceLabel.of(m("human", "other", "weird", null)))
        assertEquals("AI 작성 · 검수 전", ProvenanceLabel.of(m("llm:x", "other", "unreviewed", null)))
    }
}
