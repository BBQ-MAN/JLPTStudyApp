package com.jlptstudy.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FuriganaTest {
    private fun seg(base: String, ruby: String? = null, emphasis: Boolean = false) = RubySegment(base, ruby, emphasis)

    @Test fun `parse는 후리가나 구간을 나눈다`() {
        assertEquals(listOf(seg("日本", "にほん"), seg("語")), Furigana.parse("{日本|にほん}語"))
    }

    @Test fun `짝이 안 맞는 괄호는 원문 그대로 하나의 구간이다`() {
        for (bad in listOf("{日本|にほん語", "日本|にほん}語", "{日本にほん}語", "{|にほん}", "{日本|}", "{{日|ひ}|にち}", "語}")) {
            assertEquals(bad, listOf(seg(bad)), Furigana.parse(bad))
        }
    }

    @Test fun `대상 구간은 안쪽 세그먼트에 emphasis를 준다`() {
        assertEquals(
            listOf(seg("あとで", null, false), seg("食", "た", true), seg("べて", null, true), seg("ください", null, false)),
            Furigana.parse("あとで[[{食|た}べて]]ください"),
        )
    }

    @Test fun `짝이 안 맞거나 중첩된 대괄호는 원문 그대로 emphasis false`() {
        for (bad in listOf("あとで[[食べてください", "あとで食べて]]ください", "[[a[[b]]c]]", "[[{食|た[[]]}]]")) {
            assertEquals(bad, listOf(seg(bad)), Furigana.parse(bad))
        }
    }

    @Test fun `빈 문자열은 구간 없음`() {
        assertEquals(emptyList<RubySegment>(), Furigana.parse(""))
    }

    @Test fun `fromHeadword는 앞뒤 공통 가나를 잘라 가운데만 루비를 얹는다`() {
        assertEquals(listOf(seg("食", "た"), seg("べる")), Furigana.fromHeadword("食べる", "たべる"))
        assertEquals(listOf(seg("お"), seg("茶", "ちゃ")), Furigana.fromHeadword("お茶", "おちゃ"))
        assertEquals(listOf(seg("今日", "きょう")), Furigana.fromHeadword("今日", "きょう"))
        assertEquals(listOf(seg("引き出", "ひきだ"), seg("し")), Furigana.fromHeadword("引き出し", "ひきだし"))
        assertEquals(listOf(seg("小", "ちい"), seg("さい")), Furigana.fromHeadword("小さい", "ちいさい"))
        assertEquals(listOf(seg("お"), seg("母", "かあ"), seg("さん")), Furigana.fromHeadword("お母さん", "おかあさん"))
    }

    @Test fun `가나 전용 어휘는 루비가 없다`() {
        assertEquals(listOf(seg("ある")), Furigana.fromHeadword(null, "ある"))
        assertEquals(listOf(seg("ある")), Furigana.fromHeadword("ある", "ある"))
    }
}
