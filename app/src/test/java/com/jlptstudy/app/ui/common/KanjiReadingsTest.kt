package com.jlptstudy.app.ui.common

import org.junit.Test
import org.junit.Assert.assertEquals

class KanjiReadingsTest {
    @Test fun okurigana_in_parentheses() = assertEquals("ひら(く)", kunDisplay("ひら.く"))
    @Test fun prefix_and_suffix_markers() {
        assertEquals("～びら(き)", kunDisplay("-びら.き"))
        assertEquals("あお～", kunDisplay("あお-"))
        assertEquals("ひと(つ)", kunDisplay("ひと.つ"))
    }
    @Test fun plain_reading_unchanged() = assertEquals("みず", kunDisplay("みず"))
}
