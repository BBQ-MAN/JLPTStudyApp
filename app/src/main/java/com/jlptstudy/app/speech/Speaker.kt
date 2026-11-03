package com.jlptstudy.app.speech

import kotlinx.coroutines.flow.StateFlow

/** architecture.md 8.2·9.3.2. 청해 음성은 D-04와 무관하게 이 인터페이스 뒤에 둔다. */
enum class Voice { MALE, FEMALE, NARRATOR }

/** text = 읽을 문자열(9.3.1). */
data class SpeechLine(val text: String, val voice: Voice)

interface Speaker {
    val state: StateFlow<SpeakerState>
    /** Ready·Playing에서만 동작한다(Playing이면 앞 재생을 끊고 새로 시작). rate 1.0 = 보통. */
    fun speak(utteranceId: String, lines: List<SpeechLine>, rate: Float = 1.0f)
    /** Playing이면 Ready로. 다른 상태는 그대로. */
    fun stop()
    /** 이 인스턴스만 해제한다. 이후 재사용하지 않는다. */
    fun shutdown()
}

sealed interface SpeakerState {
    data object Initializing : SpeakerState
    data object Ready : SpeakerState
    data class Playing(val utteranceId: String) : SpeakerState
    data object MissingJapaneseData : SpeakerState
    data object NoEngine : SpeakerState
    data object Error : SpeakerState
}
