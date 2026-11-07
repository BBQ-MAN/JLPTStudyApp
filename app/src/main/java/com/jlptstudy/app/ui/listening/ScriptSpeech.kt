package com.jlptstudy.app.ui.listening

import com.jlptstudy.app.data.content.ListeningLine
import com.jlptstudy.app.speech.SpeechLine
import com.jlptstudy.app.speech.Voice
import com.jlptstudy.app.ui.common.Furigana

/** 대본 → 읽는 문자열(architecture.md 9.3.1). SpeechLine을 만드는 앱 코드의 유일한 곳. question은 읽지 않는다. */
object ScriptSpeech {
    fun lines(lines: List<ListeningLine>): List<SpeechLine> = lines.map { l ->
        // 루비 읽기는 쓰지 않는다: 한자가 TTS의 형태소 단서다. 잘못 읽는 곳은 작성자가 ttsText로 고친다.
        SpeechLine(l.ttsText?.takeIf { it.isNotBlank() } ?: Furigana.parse(l.text).joinToString("") { it.base }, voice(l.speaker))
    }

    /** M/F/N. 모르는 값은 내레이션. */
    fun voice(speaker: String): Voice = when (speaker) {
        "M" -> Voice.MALE
        "F" -> Voice.FEMALE
        else -> Voice.NARRATOR
    }
}
