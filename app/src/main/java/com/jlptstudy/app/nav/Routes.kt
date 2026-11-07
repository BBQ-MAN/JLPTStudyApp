package com.jlptstudy.app.nav

import kotlinx.serialization.Serializable

// 타입 안전 라우트(architecture.md 7절). 나머지 라우트는 해당 화면 마일스톤이 더한다.
@Serializable data object LevelSelect
@Serializable data object Home
@Serializable data object Dictionary
@Serializable data class Settings(val openLevelPicker: Boolean = false)
@Serializable data class About(val anchor: String? = null)
@Serializable data object AppError
@Serializable data object Review
@Serializable data class VocabDetail(val id: String)
@Serializable data class KanjiDetail(val literal: String)
@Serializable data object Grammar
@Serializable data class GrammarDetail(val id: String)
@Serializable data object Practice
@Serializable data class Quiz(val attemptId: Long)
@Serializable data class QuizResult(val attemptId: Long)
@Serializable data class ExamStart(val level: com.jlptstudy.app.JlptLevel)

/** S15 해설의 관련 항목 링크 → 상세 라우트(S06·S07·S11). */
fun com.jlptstudy.app.ui.practice.TargetLink.toRoute(): Any = when (kind) {
    com.jlptstudy.app.ui.practice.TargetKind.VOCAB -> VocabDetail(id)
    com.jlptstudy.app.ui.practice.TargetKind.KANJI -> KanjiDetail(id)
    com.jlptstudy.app.ui.practice.TargetKind.GRAMMAR -> GrammarDetail(id)
}
