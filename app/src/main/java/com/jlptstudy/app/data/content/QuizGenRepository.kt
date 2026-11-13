package com.jlptstudy.app.data.content

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.practice.PRACTICE_SIZE
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.gen.GenResult
import com.jlptstudy.quiz.gen.GenType
import com.jlptstudy.quiz.gen.GeneratedQuestion
import com.jlptstudy.quiz.gen.QuizBank
import com.jlptstudy.quiz.gen.QuizGen
import com.jlptstudy.quiz.gen.QuizKanji
import com.jlptstudy.quiz.gen.QuizVocab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class GenTypeAvailability(val type: GenType, val targets: Int)

/** 자동 생성 연습 문제(quizgen.md 6.2). 문항 내용은 저장하지 않고 ID(`gq:…`)로 다시 만든다. */
interface QuizGenRepository {
    /** GenType 선언 순서. 배제 표가 비었으면(sample DB) 모두 0. */
    suspend fun availability(level: JlptLevel): List<GenTypeAvailability>
    /** 세션 문항 ID 목록. 대상이 없으면 빈 목록. */
    suspend fun newSession(level: JlptLevel, type: GenType, seed: Long, size: Int = PRACTICE_SIZE): List<String>
    /** `gq:` ID만. 형식 오류·Skip·없는(retired 포함) 어휘는 결과에서 빠진다. */
    suspend fun contents(ids: List<String>): Map<String, QuizContent>
}

/** 급수 한 개의 은행과 화면 쪽 보조 정보. meaning은 ord 0 뜻이 reviewed인 어휘만(은행의 meaningKo와 같은 기준). */
private class LevelData(val bank: QuizBank, val vocab: Map<String, QuizVocab>, val meaning: Map<String, MeaningUi>)

private val json = Json { ignoreUnknownKeys = true }

private fun strings(s: String): List<String>? = try { json.decodeFromString<List<String>>(s) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

class RoomQuizGenRepository(private val dao: ContentDao, private val texts: GeneratedTexts = GeneratedTexts) : QuizGenRepository {
    private val mutex = Mutex()
    private val cache = HashMap<Int, LevelData>()

    private suspend fun data(level: Int): LevelData = mutex.withLock {
        cache[level] ?: build(level).also { cache[level] = it }
    }

    private suspend fun build(level: Int): LevelData {
        val vocabRows = dao.quizVocab(level)
        val meanings = dao.quizMeanings(level)
            .filter { it.ord == 0 && it.koReviewStatus == "reviewed" && it.textKo.isNotBlank() }.associateBy { it.vocabId }
        val kanjiRows = dao.quizKanji(level)
        val formRows = dao.quizForms(level)
        val conflictRows = dao.quizConflicts(level)
        return withContext(Dispatchers.Default) {
            // reading은 DB 값 그대로 넘긴다. 생성기가 jm_form 비교 키에 Kana.hira를 적용한다(けしゴム ↔ けしごむ). 가타카나가 섞인 읽기는 ①②에서 빠진다.
            val vocab = vocabRows.map { QuizVocab(it.id, it.written, it.reading, it.level, it.pos, meanings[it.id]?.textKo) }
            val kanji = kanjiRows.map { QuizKanji(it.literal, it.level, strings(it.onReadings).orEmpty(), strings(it.kunReadings).orEmpty()) }
            val conflicts = HashMap<String, Set<String>>()
            for (c in conflictRows) strings(c.conflictIds)?.let { conflicts[c.vocabId] = it.toSet() }
            val bank = QuizBank(level, vocab, kanji, formRows.mapTo(HashSet()) { it.written to it.reading }, conflicts)
            LevelData(
                bank, vocab.associateBy { it.id },
                meanings.mapValues { (_, m) -> MeaningUi(m.textKo, m.koSource, m.koMethod, m.koReviewStatus, m.koReviewedAt, m.koReviewerKind) },
            )
        }
    }

    /** 전수 검사가 쓰는 급수 은행(캐시된 것 그대로). */
    suspend fun bank(level: JlptLevel): QuizBank = data(level.n).bank

    override suspend fun availability(level: JlptLevel): List<GenTypeAvailability> {
        val d = data(level.n)
        return withContext(Dispatchers.Default) { GenType.entries.map { GenTypeAvailability(it, QuizGen.eligible(d.bank, it).size) } }
    }

    override suspend fun newSession(level: JlptLevel, type: GenType, seed: Long, size: Int): List<String> {
        val d = data(level.n)
        return withContext(Dispatchers.Default) { QuizGen.buildSession(d.bank, type, seed, size).map { it.id } }
    }

    override suspend fun contents(ids: List<String>): Map<String, QuizContent> {
        val parsed = ids.mapNotNull { id -> QuizGen.parseId(id)?.let { id to it.vocabId } }
        if (parsed.isEmpty()) return emptyMap()
        val levelOf = parsed.map { it.second }.distinct().mapNotNull { v -> dao.vocabLevel(v)?.let { v to it } }.toMap()
        val out = HashMap<String, QuizContent>()
        for ((id, vocabId) in parsed) {
            val level = levelOf[vocabId] ?: continue
            if (JlptLevel.fromN(level) == null) continue
            val d = data(level)
            withContext(Dispatchers.Default) {
                (QuizGen.regenerate(d.bank, id) as? GenResult.Ok)?.let { out[id] = render(d, it.q) }
            }
        }
        return out
    }

    private fun ruby(v: QuizVocab): String =
        Furigana.fromHeadword(v.written, v.reading).joinToString("") { if (it.ruby != null) "{${it.base}|${it.ruby}}" else it.base }

    /** 생성기 문자열 → 화면 문항. ③의 일본어 쪽만 루비 표기를 덧씌운다(①②는 루비 금지). */
    private fun render(d: LevelData, q: GeneratedQuestion): QuizContent {
        val a = d.vocab.getValue(q.vocabId)
        val stem = if (q.type == GenType.MEANING_JA_KO) "[[${ruby(a)}]]" else q.stem
        val choices = if (q.type != GenType.MEANING_KO_JA) q.choices else q.choices.indices.map { i ->
            if (i == q.answerIndex) ruby(a) else q.why[i]?.sourceVocabId?.let(d.vocab::get)?.let(::ruby) ?: q.choices[i]
        }
        return QuizContent(
            id = q.id, level = JlptLevel.fromN(d.bank.level)!!, section = Section.VOCAB, type = q.type.code,
            stem = stem, passage = null, target = q.target, lines = null, choices = choices, answerIndex = q.answerIndex,
            explanationKo = texts.explanation(q.type, a.written, a.reading, a.meaningKo),
            whyWrongKo = q.why.map { w -> w?.let { texts.whyWrong(it, it.sourceVocabId?.let(d.vocab::get)) } },
            targets = listOf(a.id),
            // 해설·지문·선택지에 한국어 뜻이 보일 때만(= 이 어휘의 ord 0 뜻이 reviewed). 안 보이면 null.
            provenance = if (a.meaningKo != null) d.meaning[a.id] else null,
            needsReview = false, generated = true,
        )
    }
}
