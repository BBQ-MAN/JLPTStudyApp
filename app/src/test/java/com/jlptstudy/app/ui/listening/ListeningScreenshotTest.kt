package com.jlptstudy.app.ui.listening

import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.ui.dictionary.DictShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S16 스크린샷(ui-design 12절 이름 listening_*, 합성 픽스처). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class ListeningScreenshotTest : DictShots() {
    private val base = ListeningUiState(
        ListeningStatus.ANSWERING, attemptId = 1, index = 2, total = 10, question = listeningQuestionUi(), speaker = SpeakerState.Ready,
        audioNotice = noticeN12(),
    )

    @Test fun listening_ready() = shots("listening_ready") { ListeningScreen(base) }

    @Test fun listening_playing() = shots("listening_playing") {
        ListeningScreen(base.copy(speaker = SpeakerState.Playing("ls-n5-0001"), rate = 0.8f))
    }

    @Test fun listening_no_engine() = shots("listening_no_engine") {
        ListeningScreen(base.copy(speaker = SpeakerState.NoEngine, scriptOpen = true, scriptSeen = true))
    }

    @Test fun listening_script_open() = shots("listening_script_open") {
        ListeningScreen(base.copy(scriptOpen = true, scriptSeen = true))
    }

    @Test fun listening_missing_voice() = shots("listening_missing_voice") {
        ListeningScreen(base.copy(speaker = SpeakerState.MissingJapaneseData))
    }

    @Test fun listening_error() = shots("listening_error") { ListeningScreen(base.copy(speaker = SpeakerState.Error)) }

    @Test fun listening_feedback() = shots("listening_feedback") { ListeningScreen(feedback) }

    @Test fun listening_feedback_font200() = shots("listening_feedback", fontScale = 2f) { ListeningScreen(feedback) }

    @Test fun listening_missing_voice_font200() = shots("listening_missing_voice", fontScale = 2f) {
        ListeningScreen(base.copy(speaker = SpeakerState.MissingJapaneseData))
    }

    @Test fun listening_script_open_font200() = shots("listening_script_open", fontScale = 2f) {
        ListeningScreen(base.copy(scriptOpen = true, scriptSeen = true))
    }

    private val feedback get() = base.copy(status = ListeningStatus.FEEDBACK, chosenIndex = 0, scriptOpen = true, scriptSeen = true)
}
