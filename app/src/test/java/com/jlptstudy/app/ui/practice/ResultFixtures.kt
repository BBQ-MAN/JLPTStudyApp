package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.GrammarDetailUi
import com.jlptstudy.app.data.content.GrammarRepository
import com.jlptstudy.app.data.content.GrammarRow
import com.jlptstudy.app.data.content.KanjiDetail
import com.jlptstudy.app.data.content.VocabDetail
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.ui.common.Furigana
import kotlinx.coroutines.runBlocking

/** 답 한 줄을 저장한다(chosen = 섞기 전 원래 색인, 저장소가 쓰는 값). */
fun FakeQuiz.answer(attemptId: Long, questionId: String, chosen: Int, correct: Boolean, scriptShown: Boolean = false) =
    runBlocking { saveAnswer(QuizAnswerRecord(attemptId, questionId, chosen, correct, 1L, scriptShown)) }

/** 번들에 있는 항목만 아는 콘텐츠(링크 해소 시험). */
class LinkContent(
    private val vocab: Set<String> = emptySet(), private val kanji: Set<String> = emptySet(),
) : ContentRepository by FakeContentRepository {
    override suspend fun vocabDetail(id: String): VocabDetail? =
        if (id in vocab) VocabDetail("駅", "えき", Furigana.fromHeadword("駅", "えき"), JlptLevel.N5, emptyList(), emptyList(), listOf("駅")) else null
    override suspend fun kanjiDetail(literal: String): KanjiDetail? =
        if (literal in kanji) KanjiDetail(literal, JlptLevel.N5, 8, emptyList(), emptyList(), emptyList(), emptyList(), null) else null
}

class LinkGrammar(private val ids: Set<String> = emptySet()) : GrammarRepository {
    override suspend fun list(levels: Set<JlptLevel>): List<GrammarRow> = emptyList()
    override suspend fun levels(): Set<JlptLevel> = emptySet()
    override suspend fun detail(id: String): GrammarDetailUi? =
        if (id in ids) GrammarDetailUi(id, JlptLevel.N5, "〜てから", "뜻", "설명", emptyList(), emptyList(), emptyList(), provenance, false) else null
}
