package com.jlptstudy.app.ui.common

/** base 위에 ruby를 얹는 한 구간. emphasis = `[[…]]` 대상 구간(밑줄). */
data class RubySegment(val base: String, val ruby: String? = null, val emphasis: Boolean = false)

object Furigana {
    /**
     * `{漢字|かな}` 후리가나와 `[[…]]` 대상 구간을 해석한다(architecture.md 4.4).
     * 짝이 안 맞거나 중첩이면 예외 없이 원문 전체를 한 구간으로 돌려준다.
     */
    fun parse(text: String): List<RubySegment> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<RubySegment>()
        val plain = StringBuilder()
        var emphasis = false
        fun flush() {
            if (plain.isNotEmpty()) out += RubySegment(plain.toString(), null, emphasis)
            plain.clear()
        }
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("[[", i) -> {
                    if (emphasis) return listOf(RubySegment(text))
                    flush(); emphasis = true; i += 2
                }
                text.startsWith("]]", i) -> {
                    if (!emphasis) return listOf(RubySegment(text))
                    flush(); emphasis = false; i += 2
                }
                text[i] == '{' -> {
                    val bar = text.indexOf('|', i)
                    val close = text.indexOf('}', i)
                    if (bar < 0 || close < 0 || bar > close) return listOf(RubySegment(text))
                    val base = text.substring(i + 1, bar)
                    val ruby = text.substring(bar + 1, close)
                    if (base.isEmpty() || ruby.isEmpty() || "{[]|".any { it in base || it in ruby }) return listOf(RubySegment(text))
                    flush(); out += RubySegment(base, ruby, emphasis); i = close + 1
                }
                text[i] == '}' || text[i] == '|' -> return listOf(RubySegment(text))
                else -> { plain.append(text[i]); i++ }
            }
        }
        if (emphasis) return listOf(RubySegment(text))
        flush()
        return out
    }

    /**
     * 표제어의 앞뒤 공통 가나를 잘라 가운데 구간에만 루비를 얹는다. 나눌 수 없으면 단어 전체가 한 구간(묶음 루비).
     * written이 null(가나 전용)이면 루비 없음.
     */
    fun fromHeadword(written: String?, reading: String): List<RubySegment> {
        if (written.isNullOrEmpty()) return listOf(RubySegment(reading))
        if (written == reading) return listOf(RubySegment(written))
        var p = 0
        while (p < written.length && p < reading.length && written[p] == reading[p] && isKana(written[p])) p++
        var s = 0
        while (s < written.length - p && s < reading.length - p &&
            written[written.length - 1 - s] == reading[reading.length - 1 - s] && isKana(written[written.length - 1 - s])
        ) s++
        val midW = written.substring(p, written.length - s)
        val midR = reading.substring(p, reading.length - s)
        if (midW.isEmpty() || midR.isEmpty()) return listOf(RubySegment(written, reading))
        return buildList {
            if (p > 0) add(RubySegment(written.substring(0, p)))
            add(RubySegment(midW, midR))
            if (s > 0) add(RubySegment(written.substring(written.length - s)))
        }
    }

    private fun isKana(c: Char) = c in '぀'..'ゟ' || c in '゠'..'ヿ'
}
