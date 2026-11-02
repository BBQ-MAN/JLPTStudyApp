package com.jlptstudy.app

enum class JlptLevel(val n: Int) {
    N5(5), N4(4), N3(3), N2(2), N1(1);

    companion object {
        fun fromN(n: Int): JlptLevel? = entries.firstOrNull { it.n == n }
    }
}

enum class CardKind(val code: String) {
    VR("vr"), VP("vp"), KR("kr"), GR("gr");

    companion object {
        fun fromCode(code: String): CardKind? = entries.firstOrNull { it.code == code }
    }
}

enum class ThemeMode(val code: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromCode(code: String): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** 앱 전체 상태(architecture.md 7절, S90). */
sealed interface AppState {
    data object Opening : AppState
    data class Ready(val needsOnboarding: Boolean) : AppState
    data object ContentDbFailed : AppState
    data object RecordDbFailed : AppState
}
