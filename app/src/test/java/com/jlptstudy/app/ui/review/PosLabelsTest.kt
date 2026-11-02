package com.jlptstudy.app.ui.review

import org.junit.Assert.assertEquals
import org.junit.Test

class PosLabelsTest {
    @Test fun `표의 코드는 한국어 이름표로 바뀐다`() {
        assertEquals("명사", PosLabels.of("n"))
        assertEquals("い형용사", PosLabels.of("adj-i"))
        assertEquals("1단 동사", PosLabels.of("v1"))
        assertEquals("5단 동사", PosLabels.of("v5r"))
    }

    @Test fun `표에 없는 코드는 원 코드를 그대로 보인다`() {
        assertEquals("vz-unknown", PosLabels.of("vz-unknown"))
        assertEquals("", PosLabels.of(""))
    }
}
