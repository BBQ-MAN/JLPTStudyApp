package com.jlptstudy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jlptstudy.app.nav.AppRoot
import com.jlptstudy.app.ui.theme.JlptTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JlptApp).container
        container.start()
        setContent {
            val theme by container.theme.collectAsState()
            JlptTheme(theme) { AppRoot(container) }
        }
    }
}
