package com.jlptstudy.app.speech

import android.speech.tts.TextToSpeech
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FakeEngine(var speakResult: Int = TextToSpeech.SUCCESS, var langResult: Int = TextToSpeech.LANG_AVAILABLE, override var hasEngines: Boolean = true) : TtsEngine {
    val calls = mutableListOf<String>()
    var shutdownCount = 0
    override fun setLanguage(locale: Locale): Int { calls += "lang:$locale"; return langResult }
    override fun setPitch(pitch: Float) { calls += "pitch:$pitch" }
    override fun setSpeechRate(rate: Float) { calls += "rate:$rate" }
    override fun speak(text: String, queueMode: Int, utteranceId: String): Int { calls += "speak:$queueMode:$utteranceId"; return speakResult }
    override fun playSilence(ms: Long, queueMode: Int, utteranceId: String): Int { calls += "silence:$ms:$queueMode:$utteranceId"; return TextToSpeech.SUCCESS }
    override fun stop() { calls += "stop" }
    override fun shutdown() { shutdownCount++ }
}

class Harness(scope: kotlinx.coroutines.CoroutineScope, val engine: FakeEngine = FakeEngine(), timeout: Long = 10_000) {
    lateinit var init: (Int) -> Unit
    lateinit var events: TtsEvents
    val speaker = DeviceTtsSpeaker(scope, timeout) { i, e -> init = i; events = e; engine }
}

private val M3 = listOf(SpeechLine("a", Voice.MALE), SpeechLine("b", Voice.FEMALE), SpeechLine("c", Voice.NARRATOR))

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTtsSpeakerTest {
    private fun TestScope.ready(engine: FakeEngine = FakeEngine()) =
        Harness(this, engine).also { it.init(TextToSpeech.SUCCESS) }

    @Test fun `줄 3개를 정해진 순서로 엔진에 보내고 마지막 줄 onDone 뒤에만 Ready`() = runTest {
        val h = ready()
        assertEquals(SpeakerState.Ready, h.speaker.state.value)
        h.engine.calls.clear()
        h.speaker.speak("q1", M3)
        assertEquals(listOf(
            "rate:1.0", "pitch:0.85", "speak:0:q1#0", "silence:400:1:q1#gap0",
            "pitch:1.15", "speak:1:q1#1", "silence:400:1:q1#gap1",
            "pitch:1.0", "speak:1:q1#2"), h.engine.calls)
        assertEquals(SpeakerState.Playing("q1"), h.speaker.state.value)
        h.events.onDone("q1#0")
        assertEquals(SpeakerState.Playing("q1"), h.speaker.state.value)
        h.events.onDone("q1#2")
        assertEquals(SpeakerState.Ready, h.speaker.state.value)
    }

    @Test fun `줄 1개면 쉼이 없다`() = runTest {
        val h = ready()
        h.speaker.speak("q", listOf(SpeechLine("a", Voice.MALE)))
        assertEquals(0, h.engine.calls.count { it.startsWith("silence") })
    }

    @Test fun `rate는 첫 speak보다 먼저 전달된다`() = runTest {
        val h = ready()
        h.engine.calls.clear()
        h.speaker.speak("q", M3, rate = 0.8f)
        assertEquals("rate:0.8", h.engine.calls.first())
    }

    @Test fun `Playing 중 stop은 엔진을 멈추고 Ready`() = runTest {
        val h = ready()
        h.speaker.speak("q", M3)
        h.speaker.stop()
        assertEquals(SpeakerState.Ready, h.speaker.state.value)
        assertEquals(1, h.engine.calls.count { it == "stop" })
    }

    @Test fun `재생 중 onError는 Error`() = runTest {
        val h = ready()
        h.speaker.speak("q", M3)
        h.events.onError("q#1")
        assertEquals(SpeakerState.Error, h.speaker.state.value)
    }

    @Test fun `일본어 데이터 없음 또는 미지원이면 MissingJapaneseData`() = runTest {
        for (r in listOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)) {
            assertEquals(SpeakerState.MissingJapaneseData, ready(FakeEngine(langResult = r)).speaker.state.value)
        }
    }

    @Test fun `onInit ERROR는 엔진 유무로 NoEngine 또는 Error`() = runTest {
        val none = Harness(this, FakeEngine(hasEngines = false)).also { it.init(TextToSpeech.ERROR) }
        assertEquals(SpeakerState.NoEngine, none.speaker.state.value)
        val some = Harness(this, FakeEngine(hasEngines = true)).also { it.init(TextToSpeech.ERROR) }
        assertEquals(SpeakerState.Error, some.speaker.state.value)
    }

    @Test fun `Ready와 Playing 밖에서는 speak가 엔진을 부르지 않는다`() = runTest {
        val initializing = Harness(this)
        val missing = ready(FakeEngine(langResult = TextToSpeech.LANG_MISSING_DATA))
        val noEngine = Harness(this, FakeEngine(hasEngines = false)).also { it.init(TextToSpeech.ERROR) }
        val error = Harness(this).also { it.init(TextToSpeech.ERROR) }
        for (h in listOf(initializing, missing, noEngine, error)) {
            h.engine.calls.removeAll { true }
            h.speaker.speak("q", M3)
            assertEquals(emptyList<String>(), h.engine.calls)
        }
    }

    @Test fun `초기화가 10초 안에 끝나지 않으면 Error`() = runTest {
        val h = Harness(this)
        advanceTimeBy(9_999)
        assertEquals(SpeakerState.Initializing, h.speaker.state.value)
        advanceTimeBy(2)
        assertEquals(SpeakerState.Error, h.speaker.state.value)
        h.init(TextToSpeech.SUCCESS) // 늦은 onInit은 무시
        assertEquals(SpeakerState.Error, h.speaker.state.value)
    }

    @Test fun `시간 안에 초기화되면 시간 초과가 상태를 건드리지 않는다`() = runTest {
        val h = ready()
        advanceTimeBy(20_000)
        assertEquals(SpeakerState.Ready, h.speaker.state.value)
    }

    @Test fun `shutdown은 엔진을 해제한다`() = runTest {
        val h = ready()
        h.speaker.shutdown()
        assertEquals(1, h.engine.shutdownCount)
    }

    @Test fun `엔진이 speak를 ERROR로 거부하면 Error이고 남은 줄을 보내지 않는다`() = runTest {
        val h = ready(FakeEngine(speakResult = TextToSpeech.ERROR))
        h.engine.calls.clear()
        h.speaker.speak("q", M3)
        assertEquals(SpeakerState.Error, h.speaker.state.value)
        assertEquals(1, h.engine.calls.count { it.startsWith("speak") })
    }
}
