package com.jlptstudy.app.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeSpeaker : Speaker {
    val mutable = MutableStateFlow<SpeakerState>(SpeakerState.Ready)
    override val state: StateFlow<SpeakerState> = mutable
    var shutdownCount = 0
    var stopCount = 0
    class Spoken(val utteranceId: String, val lines: List<SpeechLine>, val rate: Float)
    val spoken = mutableListOf<Spoken>()
    override fun speak(utteranceId: String, lines: List<SpeechLine>, rate: Float) {
        spoken += Spoken(utteranceId, lines, rate)
        if (mutable.value == SpeakerState.Ready || mutable.value is SpeakerState.Playing) mutable.value = SpeakerState.Playing(utteranceId)
    }
    override fun stop() { stopCount++; if (mutable.value is SpeakerState.Playing) mutable.value = SpeakerState.Ready }
    override fun shutdown() { shutdownCount++ }
}

class SpeakerStateTest {
    private val lines = listOf(SpeechLine("a", Voice.NARRATOR))

    @Test fun `Ready에서 speak하면 Playing, 끝 콜백이면 Ready`() {
        val s = FakeSpeaker()
        s.speak("id", lines)
        assertEquals(SpeakerState.Playing("id"), s.state.value)
        s.mutable.value = SpeakerState.Ready // 끝 콜백
        assertEquals(SpeakerState.Ready, s.state.value)
    }

    @Test fun `Playing 중 stop은 Ready`() {
        val s = FakeSpeaker()
        s.speak("id", lines)
        s.stop()
        assertEquals(SpeakerState.Ready, s.state.value)
    }
}
