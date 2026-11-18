package com.jlptstudy.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentTest {
    @Test fun `ui-design 6_10 기대값`() {
        assertEquals(0, percent(0, 25))
        assertEquals(72, percent(18, 25))
        assertEquals(33, percent(1, 3))
        assertEquals(67, percent(2, 3))
        assertEquals(13, percent(1, 8)) // 12.5 올림
        assertEquals(99, percent(199, 200))
        assertEquals(1, percent(1, 300))
        assertEquals(100, percent(25, 25))
    }
}
