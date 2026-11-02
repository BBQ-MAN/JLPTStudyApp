package com.jlptstudy.app.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.app.ui.theme.KoSpan

object JpSpans {
    /** 문자 집합 J(architecture.md 4.4): 가나, 한자(확장 A 포함), 々, 〜, 「」『』. */
    fun isJapanese(c: Char): Boolean =
        c in '぀'..'ヿ' || c in '一'..'鿿' || c in '㐀'..'䶿' ||
            c == '々' || c == '〜' || c in '「'..'』'

    /** 한국어 문장에서 J가 이어진 구간에 ja-JP 로캘(JpSpan)을 건다. 구간 표시는 콘텐츠에 없다. */
    fun annotate(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val ja = isJapanese(text[i])
            var j = i
            while (j < text.length && isJapanese(text[j]) == ja) j++
            if (ja) withStyle(JpSpan) { append(text, i, j) } else withStyle(KoSpan) { append(text, i, j) }
            i = j
        }
    }
}
