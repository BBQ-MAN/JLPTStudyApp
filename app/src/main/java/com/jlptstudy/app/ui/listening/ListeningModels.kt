package com.jlptstudy.app.ui.listening

import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.ui.practice.QuizQuestionUi

// architecture.md 9.3.3. 계약 밖 필드는 주석으로 표시한다.
enum class ListeningStatus { LOADING, EMPTY, ANSWERING, FEEDBACK, SUBMITTED }

data class ListeningUiState(
    val status: ListeningStatus = ListeningStatus.LOADING,
    val attemptId: Long = 0, val index: Int = 0, val total: Int = 0,
    val question: QuizQuestionUi? = null,
    val chosenIndex: Int? = null,
    val scriptOpen: Boolean = false,
    val scriptSeen: Boolean = false,
    val speaker: SpeakerState = SpeakerState.Initializing,
    val rate: Float = 1.0f,
    val audioNotice: NoticeUi? = null,
    val externalOpenFailed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val showRuby: Boolean = true, // 계약 밖: 설정의 본문 후리가나(상단 토글)
    val finishFailed: Boolean = false, // 계약 밖: saveFailed 중 finish 예외(스낵바 문구)
)
