package com.jlptstudy.app.ui.listening

import androidx.lifecycle.ViewModelStore
import com.jlptstudy.app.speech.FakeSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ListeningViewModelTest {
    @Test fun `ViewModel마다 새 Speaker를 만들고 onCleared는 자기 인스턴스만 shutdown`() {
        val made = mutableListOf<FakeSpeaker>()
        val factory = { FakeSpeaker().also(made::add) }
        val s1 = ViewModelStore().also { it.put("a", ListeningViewModel(factory)) }
        val s2 = ViewModelStore().also { it.put("a", ListeningViewModel(factory)) }
        assertEquals(2, made.size)
        assertNotSame(made[0], made[1])
        s1.clear()
        assertEquals(listOf(1, 0), made.map { it.shutdownCount })
        assertEquals(com.jlptstudy.app.speech.SpeakerState.Ready, made[1].state.value)
        s2.clear()
        assertEquals(listOf(1, 1), made.map { it.shutdownCount })
    }
}
