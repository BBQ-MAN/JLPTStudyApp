package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDao
import com.jlptstudy.app.data.content.GeneratedTexts
import com.jlptstudy.app.data.content.JmFormEntity
import com.jlptstudy.app.data.content.KanjiEntity
import com.jlptstudy.app.data.content.QuestionEntity
import com.jlptstudy.app.data.content.RoomQuizContentRepository
import com.jlptstudy.app.data.content.RoomQuizGenRepository
import com.jlptstudy.app.data.content.VocabConflictEntity
import com.jlptstudy.app.data.content.VocabEntity
import com.jlptstudy.app.data.content.VocabMeaningEntity
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.content.toQuizContent
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizAttemptRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.gen.Distractor
import com.jlptstudy.quiz.gen.DistractorKind
import com.jlptstudy.quiz.gen.GenType
import com.jlptstudy.quiz.gen.QuizGen
import com.jlptstudy.quiz.gen.QuizVocab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

// 합성 은행(실제 콘텐츠가 아니다). 뜻 문자열은 N5는 reviewed, N4는 sampled로 둔다.
private val n5Words = listOf("学生" to "がくせい", "先生" to "せんせい", "学校" to "がっこう", "時間" to "じかん", "約束" to "やくそく", "電話" to "でんわ", "会社" to "かいしゃ", "友達" to "ともだち")
private val n4Words = listOf("研究" to "けんきゅう", "経験" to "けいけん", "準備" to "じゅんび", "紹介" to "しょうかい", "予約" to "よやく", "場合" to "ばあい")

private fun vocab(id: String, w: String?, r: String, level: Int) = VocabEntity(id, w, r, level, 1, "n", "S-01", "S-05")
private fun meaning(id: String, text: String, status: String) =
    VocabMeaningEntity(id, 0, text, "S-01", "jmdict-translated", "llm:test@2026-11-08", status, null, null)

private class GenDao(
    val vocab: List<VocabEntity>, val meanings: List<VocabMeaningEntity>, val forms: List<JmFormEntity>, val conflicts: List<VocabConflictEntity>,
    val kanji: List<KanjiEntity> = emptyList(), val questions: List<QuestionEntity> = emptyList(), val retired: Set<String> = emptySet(),
) : ContentDao by todo() {
    override suspend fun quizVocab(level: Int) = vocab.filter { it.level == level && it.id.startsWith("jm:") && it.id !in retired }.sortedBy { it.id }
    override suspend fun quizMeanings(level: Int) = meanings.filter { m -> vocab.any { it.id == m.vocabId && it.level == level } && m.ord == 0 }
    override suspend fun quizKanji(level: Int) = kanji.filter { it.level >= level }
    override suspend fun quizForms(level: Int) = forms
    override suspend fun quizConflicts(level: Int) = conflicts.filter { c -> vocab.any { it.id == c.vocabId && it.level == level } }
    override suspend fun vocabLevel(id: String) = vocab.firstOrNull { it.id == id }?.level
    override suspend fun retiredAmong(ids: List<String>) = ids.filter { it in retired }
    override suspend fun questionsByIds(ids: List<String>) = questions.filter { it.id in ids }
    override suspend fun listeningByIds(ids: List<String>) = emptyList<com.jlptstudy.app.data.content.ListeningEntity>()

    companion object {
        fun todo(): ContentDao = Proxy.newProxyInstance(ContentDao::class.java.classLoader, arrayOf(ContentDao::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as ContentDao

        fun of(withForms: Boolean = true, withConflicts: Boolean = true, retired: Set<String> = emptySet(), questions: List<QuestionEntity> = emptyList()): GenDao {
            val v5 = n5Words.mapIndexed { i, (w, r) -> vocab("jm:${100 + i}", w, r, 5) }
            val v4 = n4Words.mapIndexed { i, (w, r) -> vocab("jm:${200 + i}", w, r, 4) }
            val m5 = v5.mapIndexed { i, v -> meaning(v.id, "학습뜻${i}", "reviewed") }
            val m4 = v4.mapIndexed { i, v -> meaning(v.id, "샘플뜻${i}", "sampled") }
            val all = v5 + v4
            return GenDao(
                all, m5 + m4,
                if (withForms) all.map { JmFormEntity(it.written!!, it.reading) } else emptyList(),
                if (withConflicts) all.map { VocabConflictEntity(it.id, "[]") } else emptyList(),
                listOf(KanjiEntity("学", 5, 8, null, """["ガク"]""", """["まな.ぶ"]""", """["학"]""", 1, "S-02"), KanjiEntity("校", 5, 10, null, """["コウ"]""", "[]", """["교"]""", 2, "S-02")),
                questions, retired,
            )
        }
    }
}

private fun fixedQuestion(id: String) = QuestionEntity(
    id, 5, "vocab", "kanji-reading", 1, "s", null, null, """["a","b","c","d"]""", 3, "e", """[null,"x","y","z"]""", "[]",
    "authored", "authored", "human", "unreviewed", null, null, false,
)

private class AttemptStore(val record: QuizAttemptRecord) : QuizRepository {
    override suspend fun startAttempt(examId: String?, seed: Long, questionOrder: List<String>, startedAtEpochMs: Long) = TODO()
    override suspend fun saveAnswer(answer: QuizAnswerRecord) = TODO()
    override suspend fun saveProgress(attemptId: Long, currentIndex: Int, elapsedMs: Long) = TODO()
    override suspend fun finish(attemptId: Long, finishedAtEpochMs: Long) = TODO()
    override suspend fun attempt(attemptId: Long) = record.takeIf { it.id == attemptId }
    override suspend fun answers(attemptId: Long) = emptyList<QuizAnswerRecord>()
    override suspend fun unfinishedExam() = TODO()
}

class QuizGenRepositoryTest {
    private fun repos(dao: GenDao = GenDao.of()): Pair<RoomQuizGenRepository, RoomQuizContentRepository> {
        val gen = RoomQuizGenRepository(dao)
        return gen to RoomQuizContentRepository(dao, gen)
    }

    private suspend fun ids(gen: RoomQuizGenRepository, level: JlptLevel, type: GenType, seed: Long = 7, size: Int = 5) = gen.newSession(level, type, seed, size)

    @Test fun `contents는 q 와 gq ID를 섞은 목록에서 둘 다 돌려주고 깨진 gq ID는 뺀다`() = runBlocking {
        val (gen, content) = repos(GenDao.of(questions = listOf(fixedQuestion("q-n5-vocab-0001"))))
        val good = ids(gen, JlptLevel.N5, GenType.KANJI_READING, size = 2)
        assertEquals(2, good.size)
        val bad = listOf("gq:kanji-reading:9:zz:jm:100", "gq:nope:0:1:jm:100", "gq:kanji-reading:0:1", "gq:kanji-reading:0:1:jm:999999", "gq:kanji-reading:0:1:av-x")
        val got = content.contents(listOf("q-n5-vocab-0001") + good + bad)
        assertEquals(setOf("q-n5-vocab-0001") + good.toSet(), got.keys)
        assertTrue(got.getValue(good[0]).generated)
        assertFalse(got.getValue("q-n5-vocab-0001").generated)
    }

    @Test fun `retired 어휘의 gq 문항은 빠진다`() = runBlocking {
        val live = repos().first
        val id = live.newSession(JlptLevel.N5, GenType.KANJI_READING, 3, 10).first { it.endsWith(":jm:103") }
        val (_, content) = repos(GenDao.of(retired = setOf("jm:103")))
        assertTrue(content.contents(listOf(id)).isEmpty())
    }

    @Test fun `고정 문항의 provenance는 여전히 null이 아니고 generated는 false다`() {
        val c = fixedQuestion("q-1").toQuizContent()!!
        assertNotNull(c.provenance); assertFalse(c.generated); assertFalse(c.toQuestion().fixedOrder)
    }

    @Test fun `생성 문항을 QuizSessionLoader로 읽으면 AttemptMeta와 fixedOrder와 정답 자리가 맞는다`() = runBlocking {
        for (type in GenType.entries) {
            val (gen, content) = repos()
            val order = ids(gen, JlptLevel.N5, type, seed = 11, size = 8)
            assertTrue(type.code, order.size >= 4)
            val loaded = QuizSessionLoader(AttemptStore(QuizAttemptRecord(1, null, 11L, order, 0, 0, 0, null)), content).load(1)!!
            assertEquals(AttemptMeta(QuizMode.PRACTICE, JlptLevel.N5, Section.VOCAB, type.code), loaded.meta)
            assertEquals(0, loaded.retiredCount)
            for (c in loaded.items) {
                val q = c.toQuestion()
                assertTrue(q.fixedOrder)
                val slot = QuizGen.parseId(c.id)!!.slot
                for (seed in listOf(0L, 1L, 99L, -5L)) assertEquals("${c.id} seed $seed", slot, Quiz.shuffle(q, seed).answerIndex)
                assertEquals(slot, c.answerIndex)
                assertEquals(listOf(c.targets.single()), listOf(QuizGen.parseId(c.id)!!.vocabId))
            }
        }
    }

    @Test fun `N4 한자읽기와 표기 문항에는 sampled 뜻이 없고 provenance가 null이다`() = runBlocking {
        for (type in listOf(GenType.KANJI_READING, GenType.ORTHOGRAPHY)) {
            val (gen, content) = repos()
            val order = ids(gen, JlptLevel.N4, type, size = 6)
            assertEquals(6, order.size)
            for (c in content.contents(order).values) {
                assertNull(c.provenance)
                val text = (listOf(c.stem, c.explanationKo) + c.choices + c.whyWrongKo.filterNotNull()).joinToString("\n")
                assertFalse(text, "샘플뜻" in text || "뜻:" in c.explanationKo)
                assertTrue(c.generated); assertFalse(c.needsReview)
            }
        }
    }

    @Test fun `N5 한자읽기 문항은 reviewed 뜻을 해설에 붙이고 provenance가 있다`() = runBlocking {
        val (gen, content) = repos()
        val c = content.contents(ids(gen, JlptLevel.N5, GenType.KANJI_READING)).values.first()
        val p = c.provenance!!
        assertEquals("reviewed", p.reviewStatus)
        assertTrue(c.explanationKo, c.explanationKo.endsWith("\n\n뜻: ${p.textKo}"))
    }

    @Test fun `뜻 유형은 N5에서 나오고 N4(미검수)는 나오지 않는다 일본어 쪽은 루비 표기다`() = runBlocking {
        val (gen, content) = repos()
        assertTrue(ids(gen, JlptLevel.N4, GenType.MEANING_JA_KO).isEmpty())
        val jaKo = content.contents(ids(gen, JlptLevel.N5, GenType.MEANING_JA_KO)).values
        assertTrue(jaKo.isNotEmpty())
        for (c in jaKo) {
            assertTrue(c.stem, c.stem.startsWith("[[{") && c.stem.endsWith("}]]"))
            assertTrue(c.choices.all { it.startsWith("학습뜻") })
            assertNotNull(c.provenance)
        }
        val koJa = content.contents(ids(gen, JlptLevel.N5, GenType.MEANING_KO_JA)).values
        for (c in koJa) { assertTrue(c.stem.startsWith("학습뜻")); assertTrue(c.choices.all { it.startsWith("{") && '|' in it }) }
    }

    @Test fun `해설과 오답 이유에는 표시 기호가 없고 정답 자리만 null이다`() = runBlocking {
        val (gen, content) = repos()
        for (type in GenType.entries) for (c in content.contents(ids(gen, JlptLevel.N5, type, size = 8)).values) {
            assertTrue(c.id, listOf("{", "|", "}", "[[", "]]").none { it in c.explanationKo || c.whyWrongKo.filterNotNull().any { w -> it in w } })
            assertNull(c.whyWrongKo[c.answerIndex])
            assertTrue(c.whyWrongKo.withIndex().all { (i, w) -> i == c.answerIndex || !w.isNullOrBlank() })
        }
    }

    @Test fun `같은 ID는 같은 QuizContent로 두 번 만들어진다`() = runBlocking {
        val (gen, content) = repos()
        val order = ids(gen, JlptLevel.N5, GenType.ORTHOGRAPHY, size = 6)
        assertEquals(content.contents(order), content.contents(order))
    }

    @Test fun `availability는 유형 선언 순서대로 대상 수를 세고 표가 비면 모두 0이다`() = runBlocking {
        val a = repos().first.availability(JlptLevel.N5)
        assertEquals(GenType.entries, a.map { it.type })
        assertEquals(listOf(8, 8, 8, 8), a.map { it.targets })
        assertEquals(listOf(6, 6, 0, 0), repos().first.availability(JlptLevel.N4).map { it.targets })
        val empty = RoomQuizGenRepository(GenDao.of(withForms = false, withConflicts = false))
        assertEquals(listOf(0, 0, 0, 0), empty.availability(JlptLevel.N5).map { it.targets })
        assertTrue(empty.newSession(JlptLevel.N5, GenType.KANJI_READING, 1, 10).isEmpty())
    }

    @Test fun `자기 표기와 읽기 쌍이 forms에 없으면 대상이 아니다`() = runBlocking {
        val dao = GenDao.of()
        val trimmed = GenDao(dao.vocab, dao.meanings, dao.forms.filter { it.written != "学生" }, dao.conflicts, dao.kanji)
        val av = RoomQuizGenRepository(trimmed).availability(JlptLevel.N5)
        assertEquals(7, av.first { it.type == GenType.KANJI_READING }.targets)
    }
}

class GeneratedTextsTest {
    @Test fun `해설 문구는 유형마다 정해진 모양이다`() {
        assertEquals("「学生」는 「がくせい」로 읽습니다.", GeneratedTexts.explanation(GenType.KANJI_READING, "学生", "がくせい", null))
        assertEquals("「がくせい」는 「学生」로 씁니다.\n\n뜻: 학생", GeneratedTexts.explanation(GenType.ORTHOGRAPHY, "学生", "がくせい", "학생"))
        assertEquals("「学生」(がくせい): 학생", GeneratedTexts.explanation(GenType.MEANING_JA_KO, "学生", "がくせい", "학생"))
        assertEquals("「ありがとう」: 고맙다", GeneratedTexts.explanation(GenType.MEANING_KO_JA, null, "ありがとう", "고맙다"))
    }

    @Test fun `오답 이유 문구`() {
        val src = QuizVocab("jm:9", "橋", "はし", 5, "n", "다리")
        fun d(k: DistractorKind, s: QuizVocab? = null, swap: Pair<String, String>? = null) = GeneratedTexts.whyWrong(Distractor("x", k, s?.id, swap), s)
        assertEquals("장음(길게 읽는 소리)이 다릅니다.", d(DistractorKind.LONG_VOWEL))
        assertEquals("탁음·반탁음 표시가 다릅니다.", d(DistractorKind.VOICING))
        assertEquals("촉음(っ)이 다릅니다.", d(DistractorKind.SOKUON))
        assertEquals("요음(ゃ·ゅ·ょ)이 다릅니다.", d(DistractorKind.YOON))
        assertEquals("「橋」의 읽기입니다.", d(DistractorKind.OTHER_READING, src))
        assertEquals("「端」 대신 「橋」를 썼습니다.", d(DistractorKind.KANJI_SWAP, swap = "端" to "橋"))
        assertEquals("「はし」로 읽는 다른 단어입니다.", d(DistractorKind.OTHER_WRITTEN, src))
        assertEquals("「橋」의 뜻입니다.", d(DistractorKind.OTHER_MEANING, src))
        assertEquals("뜻: 다리", d(DistractorKind.OTHER_WORD, src))
    }
}
