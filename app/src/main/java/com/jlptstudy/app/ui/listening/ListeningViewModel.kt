package com.jlptstudy.app.ui.listening

import androidx.lifecycle.ViewModel
import com.jlptstudy.app.speech.Speaker

/** M22 뼈대: Speaker 수명만 맡는다. 화면 상태·재생 흐름은 M23이 채운다(architecture.md 9.3.2). */
class ListeningViewModel(speakerFactory: () -> Speaker) : ViewModel() {
    val speaker: Speaker = speakerFactory()
    override fun onCleared() { speaker.shutdown() }
}
