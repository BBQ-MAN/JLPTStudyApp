package com.jlptstudy.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** TextToSpeech의 얇은 껍데기. 테스트가 가짜로 바꾼다. */
interface TtsEngine {
    val hasEngines: Boolean
    fun setLanguage(locale: Locale): Int
    fun setPitch(pitch: Float)
    fun setSpeechRate(rate: Float)
    fun speak(text: String, queueMode: Int, utteranceId: String): Int
    fun playSilence(ms: Long, queueMode: Int, utteranceId: String): Int
    fun stop()
    fun shutdown()
}

interface TtsEvents {
    fun onDone(utteranceId: String)
    fun onError(utteranceId: String)
}

typealias TtsEngineFactory = (onInit: (Int) -> Unit, events: TtsEvents) -> TtsEngine

private class RealTtsEngine(context: Context, onInit: (Int) -> Unit, events: TtsEvents) : TtsEngine {
    private val tts = TextToSpeech(context.applicationContext) { onInit(it) }.also {
        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { utteranceId?.let(events::onDone) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let(events::onError) }
            override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let(events::onError) }
        })
    }
    override val hasEngines get() = tts.engines.isNotEmpty()
    override fun setLanguage(locale: Locale) = tts.setLanguage(locale)
    override fun setPitch(pitch: Float) { tts.setPitch(pitch) }
    override fun setSpeechRate(rate: Float) { tts.setSpeechRate(rate) }
    override fun speak(text: String, queueMode: Int, utteranceId: String) = tts.speak(text, queueMode, null, utteranceId)
    override fun playSilence(ms: Long, queueMode: Int, utteranceId: String) = tts.playSilentUtterance(ms, queueMode, utteranceId)
    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
}

/** architecture.md 9.3.2. 화자 구분은 줄마다 피치(AR-U12 A). */
class DeviceTtsSpeaker(
    private val scope: CoroutineScope,
    private val initTimeoutMs: Long = 10_000,
    engineFactory: TtsEngineFactory,
) : Speaker {
    constructor(context: Context, scope: CoroutineScope, initTimeoutMs: Long = 10_000) :
        this(scope, initTimeoutMs, { onInit, events -> RealTtsEngine(context, onInit, events) })

    private val _state = MutableStateFlow<SpeakerState>(SpeakerState.Initializing)
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    @Volatile private var lastLineId: String? = null
    @Volatile private var closed = false

    private val events = object : TtsEvents {
        override fun onDone(utteranceId: String) {
            if (utteranceId == lastLineId) _state.value.let { if (it is SpeakerState.Playing) _state.value = SpeakerState.Ready }
        }
        override fun onError(utteranceId: String) {
            if (_state.value is SpeakerState.Playing) _state.value = SpeakerState.Error
        }
    }

    // 생성자가 onInit을 동기로 부르는 엔진을 위해 engine은 lateinit이고 onInit은 한 번 미룬다.
    private var engine: TtsEngine? = null
    private var earlyInit: Int? = null

    init {
        engine = engineFactory({ earlyInit = it; engine?.let { _ -> handleInit(it) } }, events)
        earlyInit?.let(::handleInit)
        scope.launch {
            delay(initTimeoutMs)
            _state.compareAndSet(SpeakerState.Initializing, SpeakerState.Error)
        }
    }

    private fun handleInit(status: Int) {
        if (closed || _state.value != SpeakerState.Initializing) return
        val e = engine ?: return
        val next = if (status == TextToSpeech.SUCCESS) {
            val r = e.setLanguage(Locale.JAPAN)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) SpeakerState.MissingJapaneseData
            else SpeakerState.Ready
        } else if (!e.hasEngines) SpeakerState.NoEngine else SpeakerState.Error
        _state.compareAndSet(SpeakerState.Initializing, next)
    }

    override fun speak(utteranceId: String, lines: List<SpeechLine>, rate: Float) {
        val e = engine ?: return
        if (closed || lines.isEmpty()) return
        val s = _state.value
        if (s != SpeakerState.Ready && s !is SpeakerState.Playing) return
        lastLineId = "$utteranceId#${lines.lastIndex}"
        _state.value = SpeakerState.Playing(utteranceId)
        e.setSpeechRate(rate)
        // 서비스 연결이 끊기면 콜백 없이 ERROR만 돌아온다. 남은 줄을 보내지 않고 Error로 둔다.
        for ((i, line) in lines.withIndex()) {
            e.setPitch(pitchOf(line.voice))
            var r = e.speak(line.text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, "$utteranceId#$i")
            if (r != TextToSpeech.ERROR && i < lines.lastIndex) r = e.playSilence(LINE_GAP_MS, TextToSpeech.QUEUE_ADD, "$utteranceId#gap$i")
            if (r == TextToSpeech.ERROR) { _state.value = SpeakerState.Error; return }
        }
    }

    override fun stop() {
        if (_state.value !is SpeakerState.Playing) return
        engine?.stop()
        _state.value = SpeakerState.Ready
    }

    override fun shutdown() {
        closed = true
        engine?.shutdown()
    }

    private fun pitchOf(v: Voice) = when (v) { Voice.MALE -> 0.85f; Voice.FEMALE -> 1.15f; Voice.NARRATOR -> 1.0f }

    companion object { const val LINE_GAP_MS = 400L }
}
