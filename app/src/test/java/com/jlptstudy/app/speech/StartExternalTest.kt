package com.jlptstudy.app.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.ui.common.startExternal
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartExternalTest {
    @Test fun `받을 Activity가 없으면 죽지 않고 false`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(ctx as android.app.Application).checkActivities(true)
        assertFalse(ctx.startExternal(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)))
        assertFalse(ctx.startExternal(Intent("com.android.settings.TTS_SETTINGS")))
    }
}
