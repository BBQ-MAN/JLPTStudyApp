package com.jlptstudy.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JlptApp : Application() {
    val container: AppContainer by lazy {
        AppContainer.create(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
