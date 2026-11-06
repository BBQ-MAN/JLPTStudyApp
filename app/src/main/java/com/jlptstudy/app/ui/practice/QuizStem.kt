package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.ui.common.RubySegment

sealed interface StemPart {
    data class Text(val segment: RubySegment) : StemPart
    data object Blank : StemPart
    data object StarBlank : StemPart
}

/** 빈칸 토큰(architecture.md 9.2.2): 빈칸 `＿＿`, ★ 칸 `＿★＿`. 루비·`[[…]]` 안에는 없다. */
object QuizStem {
    private val token = Regex("＿★＿|＿＿")

    fun split(segments: List<RubySegment>): List<StemPart> = segments.flatMap { seg ->
        if (seg.ruby != null || seg.emphasis) return@flatMap listOf(StemPart.Text(seg))
        buildList<StemPart> {
            var at = 0
            for (m in token.findAll(seg.base)) {
                if (m.range.first > at) add(StemPart.Text(seg.copy(base = seg.base.substring(at, m.range.first))))
                add(if (m.value == "＿★＿") StemPart.StarBlank else StemPart.Blank)
                at = m.range.last + 1
            }
            if (at < seg.base.length) add(StemPart.Text(seg.copy(base = seg.base.substring(at))))
        }
    }
}
