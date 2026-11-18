package com.jlptstudy.quiz.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTest {
    @Test fun hira() {
        assertEquals("かん", Kana.hira("カン"))
        assertEquals("こーひー", Kana.hira("コーヒー"))
        assertEquals("ゔ", Kana.hira("ヴ"))
        assertEquals("ぱぱ", Kana.hira("パパ"))
        assertEquals("日本", Kana.hira("日本"))
    }

    @Test fun posGroupExamples() {
        assertEquals("verb", posGroup("v5r,vi"))
        assertEquals("adj-i", posGroup("adj-i"))
        assertEquals("noun", posGroup("n,vs"))
        assertEquals("adj-na", posGroup("adj-na,n"))
        assertEquals("exp", posGroup("exp"))
        assertEquals("verb", posGroup("vs-i"))
        assertEquals("verb", posGroup("vk"))
        assertEquals("adj-i", posGroup("adj-ix"))
        assertEquals("adv", posGroup("adv-to"))
        assertEquals("noun", posGroup("pn"))
        assertEquals("adj-no", posGroup("adj-no"))
        assertEquals("noun", posGroup(" n , adv"))
    }

    private fun variants(r: String) = KanaVariants.of(r).toMap()

    @Test fun gakusei() {
        val v = variants("がくせい")
        assertEquals(DistractorKind.VOICING, v["かくせい"])
        assertEquals(DistractorKind.SOKUON, v["がっせい"])
        assertEquals(DistractorKind.LONG_VOWEL, v["がくせ"])
        assertFalse(v.containsKey("っがくせい"))
        assertFalse(v.containsKey("がくせい"))
    }

    @Test fun kyou() {
        val v = variants("きょう")
        assertEquals(DistractorKind.YOON, v["きよう"])
        assertEquals(DistractorKind.YOON, v["きゅう"])
        assertEquals(DistractorKind.LONG_VOWEL, v["きょ"])
        assertEquals(DistractorKind.VOICING, v["ぎょう"])
    }

    @Test fun nihonVoicingButNotTwoChanges() {
        val v = variants("にほん")
        assertEquals(DistractorKind.VOICING, v["にぼん"])
        assertEquals(DistractorKind.VOICING, v["にぽん"])
        assertFalse("한 번에 한 곳만", v.containsKey("にっぽん"))
    }

    @Test fun sokuonAndLongVowelRemovals() {
        val g = variants("がっこう")
        assertEquals(DistractorKind.SOKUON, g["がこう"])
        assertEquals(DistractorKind.SOKUON, g["がつこう"])
        assertEquals(DistractorKind.SOKUON, g["がくこう"])
        assertEquals(DistractorKind.LONG_VOWEL, g["がっこ"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("おかあさん")["おかさん"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("こーひー")["こひー"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("せんせい")["せんせ"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("こーひー")["こひー"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("こーひー")["こーひ"])
    }

    @Test fun longVowelInsertion() {
        assertEquals(DistractorKind.LONG_VOWEL, variants("とけい")["とうけい"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("おとな")["おうとな"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("おとな")["おとうな"])
        assertEquals(DistractorKind.LONG_VOWEL, variants("ねこ")["ねいこ"])
        assertFalse("같은 글자가 이미 뒤에 있으면 넣지 않는다", variants("おう").containsKey("おうう"))
        assertFalse("끝에는 넣지 않는다", variants("おとこ").containsKey("おとこう"))
        assertFalse("ん 뒤에는 넣지 않는다", variants("ほんと").containsKey("ほんうと"))
    }

    @Test fun yoonChanges() {
        val v = variants("びょういん")
        assertEquals(DistractorKind.YOON, v["びよういん"])
        assertEquals(DistractorKind.YOON, v["びゃういん"])
        assertEquals(DistractorKind.YOON, v["びういん"])
        assertEquals(DistractorKind.YOON, variants("りよこう")["りょこう"])
    }

    private val small = "ぁぃぅぇぉっゃゅょゎ"
    private val iRow = "きぎしじちぢにひびぴみり"

    /** 4.6 형식 규칙을 구현과 별개로 다시 적은 검사기. */
    private fun violation(s: String): String? {
        if (s.isEmpty()) return "empty"
        if (s.first() in "っー$small") return "first"
        if (s.last() == 'っ') return "last っ"
        if ("っっ" in s) return "っっ"
        s.forEachIndexed { i, c ->
            if (c == 'っ' && s[i + 1] in "あいうえおんっー$small") return "っ+${s[i + 1]}"
            if (c in "ゃゅょ" && (i == 0 || s[i - 1] !in iRow)) return "small ${c} after ${s.getOrNull(i - 1)}"
        }
        return null
    }

    @Test fun noFormViolationsAndNoOriginal() {
        val readings = TestData.vocab.map { Kana.hira(it.reading) } + listOf("きょう", "しゃしん", "ちょっと", "ゆうびんきょく", "おっと", "いっしょ", "りょうり", "じゅぎょう")
        for (r in readings) {
            val vs = KanaVariants.of(r)
            assertEquals("$r 정렬·중복 없음", vs.map { it.first }.sorted(), vs.map { it.first })
            assertEquals(vs.map { it.first }.distinct(), vs.map { it.first })
            for ((t, k) in vs) {
                assertTrue("$r → $t 는 원래 읽기", t != r)
                assertEquals("$r → $t($k)", null, violation(t))
            }
        }
    }

    @Test fun everyKindShowsUp() {
        val kinds = listOf("がくせい", "きょう", "がっこう", "びょういん").flatMap { KanaVariants.of(it) }.map { it.second }.toSet()
        assertTrue(kinds.containsAll(listOf(DistractorKind.VOICING, DistractorKind.SOKUON, DistractorKind.LONG_VOWEL, DistractorKind.YOON)))
    }
}
