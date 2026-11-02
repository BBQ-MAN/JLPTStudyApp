package com.jlptstudy.app.ui.common

import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JpSpansTest {
    private fun jaSpans(text: String): List<String> {
        val a = JpSpans.annotate(text)
        assertEquals(text, a.text)
        return a.spanStyles.filter { it.item.localeList == JpSpan.localeList }.map { a.text.substring(it.start, it.end) }
    }

    @Test fun `혼용 문장에서 일본어 구간을 찾는다`() {
        assertEquals(listOf("〜さん"), jaSpans("〜さん보다 정중한 호칭"))
        assertEquals(listOf("〜て"), jaSpans("〜て형 뒤에 붙는다"))
        assertEquals(listOf("「食べる」"), jaSpans("「食べる」의 정중형"))
        assertEquals(listOf("食べる"), jaSpans("食べる의 활용"))
        assertEquals(listOf("コーヒー・紅茶"), jaSpans("コーヒー・紅茶 중에서"))
        assertEquals(listOf("『こころ』"), jaSpans("소설 『こころ』는 유명하다"))
    }

    @Test fun `한국어 구간은 ko-KR 로캘을 갖는다`() {
        val a = JpSpans.annotate("〜さん보다 정중한 호칭")
        val ko = a.spanStyles.filter { it.item.localeList == KoSpan.localeList }.map { a.text.substring(it.start, it.end) }
        assertEquals(listOf("보다 정중한 호칭"), ko)
    }

    @Test fun `한국어만 있는 문장은 구간이 없다`() {
        assertEquals(emptyList<String>(), jaSpans("정중한 호칭입니다. (Polite) 123"))
    }
}
