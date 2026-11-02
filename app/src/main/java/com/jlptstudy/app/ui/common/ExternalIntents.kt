package com.jlptstudy.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** 열 수 있는 앱을 미리 묻지 않고 시작한 뒤 예외를 잡는다(architecture.md 5.1). 열지 못하면 false. */
fun Context.startExternal(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
