package com.jlptstudy.app.ui.listening

import com.jlptstudy.app.data.content.ListeningLine
import com.jlptstudy.app.speech.SpeechLine
import com.jlptstudy.app.speech.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScriptSpeechTest {
    @Test fun `ttsText가 있으면 그것, 없으면 루비 base만 이은 문자열이고 화자는 M F N을 MALE FEMALE NARRATOR로 바꾼다`() {
        val got = ScriptSpeech.lines(
            listOf(
                ListeningLine("M", "{今日|きょう}は", null),
                ListeningLine("F", "{今日|きょう}は{天気|てんき}がいい", "きょうはてんきがいい"),
                ListeningLine("N", "[[{駅|えき}]]まで", null),
                ListeningLine("X", "こんにちは", null),
            ),
        )
        assertEquals(
            listOf(
                SpeechLine("今日は", Voice.MALE), SpeechLine("きょうはてんきがいい", Voice.FEMALE),
                SpeechLine("駅まで", Voice.NARRATOR), SpeechLine("こんにちは", Voice.NARRATOR),
            ),
            got,
        )
    }

    @Test fun `빈 ttsText는 없는 것으로 본다`() {
        assertEquals("今日", ScriptSpeech.lines(listOf(ListeningLine("M", "{今日|きょう}", "  ")))[0].text)
    }

    @Test fun `앱 코드에서 SpeechLine을 만드는 곳은 ScriptSpeech 한 곳이다`() {
        val root = File("src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")
        val hits = root.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { "SpeechLine(" in it && !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") } }
            .map { it.name }.toSet()
        // speech 패키지의 정의(Speaker.kt)와 DeviceTtsSpeaker의 사용(읽기만)을 빼면 ScriptSpeech.kt 하나
        assertEquals(setOf("ScriptSpeech.kt", "Speaker.kt"), hits - "DeviceTtsSpeaker.kt")
        assertTrue(File(root, "com/jlptstudy/app/ui/listening/ScriptSpeech.kt").isFile)
    }
}
