package com.jlptstudy.app.data.content

import com.jlptstudy.quiz.gen.Distractor
import com.jlptstudy.quiz.gen.DistractorKind
import com.jlptstudy.quiz.gen.GenType
import com.jlptstudy.quiz.gen.QuizVocab

/**
 * 자동 생성 문항의 해설·오답 이유 문구 한 곳(quizgen.md 6.2). 문구의 정본은 이 파일이다(ui-design.md에는 해설·오답 이유 문구가 없다).
 * 한국어 필드 규칙(architecture.md 4.4): `{`·`|`·`}`·`[[`·`]]`를 쓰지 않는다. 일본어는 표시 기호 없이 그대로 쓴다.
 * 학습 콘텐츠(뜻)는 인자로만 받는다 — 여기 문자열은 형식 문구뿐이다.
 */
object GeneratedTexts {
    /** ①②에서 meaningKo != null이면 "\n\n뜻: …"을 붙인다(미검수라 null이면 붙지 않는다). ③은 meaningKo가 늘 있다. */
    fun explanation(type: GenType, written: String?, reading: String, meaningKo: String?): String {
        val visible = written ?: reading
        val head = when (type) {
            GenType.KANJI_READING -> "「$visible」는 「$reading」로 읽습니다."
            GenType.ORTHOGRAPHY -> "「$reading」는 「$visible」로 씁니다."
            GenType.MEANING_JA_KO, GenType.MEANING_KO_JA ->
                (if (written == null || written == reading) "「$visible」" else "「$visible」($reading)") + ": " + meaningKo.orEmpty()
        }
        return if ((type == GenType.KANJI_READING || type == GenType.ORTHOGRAPHY) && meaningKo != null) "$head\n\n뜻: $meaningKo" else head
    }

    fun whyWrong(d: Distractor, source: QuizVocab?): String = when (d.kind) {
        DistractorKind.LONG_VOWEL -> "장음(길게 읽는 소리)이 다릅니다."
        DistractorKind.VOICING -> "탁음·반탁음 표시가 다릅니다."
        DistractorKind.SOKUON -> "촉음(っ)이 다릅니다."
        DistractorKind.YOON -> "요음(ゃ·ゅ·ょ)이 다릅니다."
        DistractorKind.OTHER_READING -> source?.let { "「${it.written ?: it.reading}」의 읽기입니다." } ?: "다른 단어의 읽기입니다."
        DistractorKind.KANJI_SWAP -> d.swap?.let { (from, to) -> "「$from」 대신 「$to」를 썼습니다." } ?: "한자가 다릅니다."
        DistractorKind.OTHER_WRITTEN -> source?.let { "「${it.reading}」로 읽는 다른 단어입니다." } ?: "다른 단어의 표기입니다."
        DistractorKind.OTHER_MEANING -> source?.let { "「${it.written ?: it.reading}」의 뜻입니다." } ?: "다른 단어의 뜻입니다."
        DistractorKind.OTHER_WORD -> source?.meaningKo?.let { "뜻: $it" } ?: "다른 단어입니다."
    }
}
