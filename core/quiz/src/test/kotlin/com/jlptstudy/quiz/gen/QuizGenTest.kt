package com.jlptstudy.quiz.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEEDS = 300L

private fun ok(r: GenResult): GeneratedQuestion = (r as? GenResult.Ok)?.q ?: throw AssertionError("expected Ok but was $r")
private fun skip(r: GenResult): SkipReason = (r as? GenResult.Skip)?.reason ?: throw AssertionError("expected Skip but was $r")

/** 모든 시드에서 만든 문항의 선택지. Skip은 건너뛴다(어느 시드에서도 금지 글자가 없으면 통과). */
private fun choicesOver(b: QuizBank, t: GenType, vocabId: String): List<List<String>> =
    (1L..SEEDS).mapNotNull { (QuizGen.generate(b, t, vocabId, it, (it % 4).toInt()) as? GenResult.Ok)?.q?.choices }

private fun distractorsOver(b: QuizBank, t: GenType, vocabId: String): List<Distractor> =
    (1L..SEEDS).flatMap { (QuizGen.generate(b, t, vocabId, it, (it % 4).toInt()) as? GenResult.Ok)?.q?.why.orEmpty().filterNotNull() }

// ① 한자 읽기 ------------------------------------------------------------------------------------------
class KanjiReadingTest {
    private val t = GenType.KANJI_READING

    @Test fun otherReadingOfSameKanjiIsNeverADistractor_kyouKonnichi() {
        val b = TestData.bank
        val kyou = TestData.id("今日", "きょう")
        val all = choicesOver(b, t, kyou)
        assertTrue("생성이 한 번도 안 됐다", all.isNotEmpty())
        all.forEach { assertFalse("こんにち가 오답에: $it", "こんにち" in it) }
        // 반대 방향도.
        choicesOver(b, t, TestData.id("今日", "こんにち")).forEach { assertFalse("きょう가 오답에: $it", "きょう" in it) }
    }

    @Test fun nihonNipponExcluded() {
        val b = TestData.bank
        choicesOver(b, t, TestData.id("日本", "にほん")).also { assertTrue(it.isNotEmpty()) }.forEach { assertFalse(it.toString(), "にっぽん" in it) }
        choicesOver(b, t, TestData.id("日本", "にっぽん")).also { assertTrue(it.isNotEmpty()) }.forEach { assertFalse(it.toString(), "にほん" in it) }
    }

    @Test fun readingOnlyInFormsTableAlsoExcluded() {
        // 은행에는 없고 jm_form에만 있는 읽기(목록 밖 항목). 이 행이 없으면 오답에 나올 수 있어야 의미가 있다.
        val vs = TestData.vocab.filter { it.written != null && it.pos == "n" }
        val gakusei = TestData.id("学生", "がくせい")
        val without = bank(vs, conflicts = TestData.conflicts)
        assertTrue("대조군: 행이 없으면 がっせい가 나온다", choicesOver(without, t, gakusei).any { "がっせい" in it })
        val withRow = bank(vs, extraForms = setOf("学生" to "がっせい"), conflicts = TestData.conflicts)
        choicesOver(withRow, t, gakusei).forEach { assertFalse("변형도 X1 검사: $it", "がっせい" in it) }
    }

    @Test fun katakanaVersionOfFormIsAlsoExcluded() {
        val vs = TestData.vocab.filter { it.written != null && it.pos == "n" }
        val b = bank(vs, extraForms = setOf("学生" to "ガッセイ"), conflicts = TestData.conflicts)  // 가타카나 행도 hira 뒤 비교
        choicesOver(b, t, TestData.id("学生", "がくせい")).forEach { assertFalse(it.toString(), "がっせい" in it) }
    }

    @Test fun sameWrittenOtherEntryInBankExcludedEvenWithoutFormsRows() {
        // forms에는 자기 쌍만 있다. 같은 표기의 다른 은행 어휘(こんにち)는 은행으로도 막는다.
        val b = bank(TestData.vocab, conflicts = TestData.conflicts)  // ownForms: 은행 전체의 쌍 → 대조를 위해 아래서 자기 쌍만 남긴다
        val kyou = TestData.id("今日", "きょう")
        val ownOnly = QuizBank(5, TestData.vocab, TestData.kanji, setOf("今日" to "きょう") + b.forms.filter { it.first != "今日" }, b.conflicts)
        choicesOver(ownOnly, t, kyou).also { assertTrue(it.isNotEmpty()) }.forEach { assertFalse(it.toString(), "こんにち" in it) }
    }

    @Test fun questionShape() {
        val b = TestData.bank
        val id = TestData.id("学生", "がくせい")
        val q = ok(QuizGen.generate(b, t, id, 11L, 2))
        assertEquals("[[学生]]", q.stem); assertEquals("学生", q.target)
        assertEquals(2, q.answerIndex); assertEquals("がくせい", q.choices[2])
        assertEquals(listOf(id), listOf(q.vocabId)); assertEquals(4, q.choices.size)
        assertEquals(4, q.choices.map { Kana.hira(it) }.toSet().size)
        assertNull(q.why[2]); q.why.filterIndexed { i, _ -> i != 2 }.forEach { assertNotNull(it) }
        assertEquals(q.choices.indices.filter { it != 2 }.map { q.choices[it] }, q.why.filterNotNull().map { it.text })
        q.why.filterNotNull().forEach { assertTrue(it.kind in setOf(DistractorKind.LONG_VOWEL, DistractorKind.VOICING, DistractorKind.SOKUON, DistractorKind.YOON, DistractorKind.OTHER_READING)) }
        q.why.filterNotNull().filter { it.kind == DistractorKind.OTHER_READING }.forEach { assertNotNull(it.sourceVocabId) }
    }

    @Test fun slotOnlyMovesTheAnswer() {
        val b = TestData.bank; val id = TestData.id("学生", "がくせい")
        val base = ok(QuizGen.generate(b, t, id, 5L, 0))
        for (slot in 1..3) {
            val q = ok(QuizGen.generate(b, t, id, 5L, slot))
            assertEquals(base.why.filterNotNull().map { it.text }, q.why.filterNotNull().map { it.text })
            assertEquals(slot, q.answerIndex)
        }
    }

    @Test fun notTargets() {
        val b = TestData.bank
        assertEquals(SkipReason.NOT_TARGET, skip(QuizGen.generate(b, t, TestData.id(null, "パパ"), 1L, 0)))      // 표기 없음
        val kanaWritten = vocab("jm:900", "ありがとう", "ありがとう", "고맙다")
        assertEquals(SkipReason.NOT_TARGET, skip(QuizGen.generate(bank(TestData.vocab + kanaWritten), t, "jm:900", 1L, 0)))   // 한자 없음
        val kataReading = vocab("jm:901", "本", "ホン", "책")
        assertEquals(SkipReason.NOT_TARGET, skip(QuizGen.generate(bank(TestData.vocab + kataReading), t, "jm:901", 1L, 0)))  // 읽기가 히라가나 아님
        val kataWritten = vocab("jm:902", "テレビ", "てれび", "텔레비전")
        assertEquals(SkipReason.NOT_TARGET, skip(QuizGen.generate(bank(listOf(kataWritten) + TestData.vocab), t, "jm:902", 1L, 0)))  // 한자 없음, 표기≠읽기
        assertEquals(SkipReason.UNKNOWN_VOCAB, skip(QuizGen.generate(b, t, "jm:404", 1L, 0)))
        val av = vocab("av-1", "山", "やま", "산")
        assertEquals(SkipReason.UNKNOWN_VOCAB, skip(QuizGen.generate(bank(TestData.vocab + av), t, "av-1", 1L, 0)))        // jm: 만
        assertFalse(QuizGen.eligible(b, t).contains(TestData.id(null, "パパ")))
    }

    @Test fun missingOwnFormsRowMeansFormsDataIsNotTrusted() {
        val noForms = bank(TestData.vocab, conflicts = TestData.conflicts, ownForms = false)
        assertEquals(emptyList<String>(), QuizGen.eligible(noForms, t))
        assertEquals(SkipReason.NOT_TARGET, skip(QuizGen.generate(noForms, t, TestData.id("学生", "がくせい"), 1L, 0)))
        assertEquals(emptyList<String>(), QuizGen.eligible(noForms, GenType.ORTHOGRAPHY))
    }

    @Test fun otherLevelAndOtherBankVocabNeverUsed() {
        val other = vocab("jm:950", "犬", "いぬ", "개", level = 4)
        val b = bank(TestData.vocab + other, kanji = TestData.kanji + kanji("犬", listOf("ケン"), listOf("いぬ")))
        assertEquals(SkipReason.UNKNOWN_VOCAB, skip(QuizGen.generate(b, t, "jm:950", 1L, 0)))
        choicesOver(b, t, TestData.id("学生", "がくせい")).forEach { assertFalse("いぬ" in it) }
    }
}

// ② 표기 ----------------------------------------------------------------------------------------------
class OrthographyTest {
    private val t = GenType.ORTHOGRAPHY

    @Test fun homophoneHashiExcluded() {
        val b = TestData.bank
        val hashi = TestData.id("橋", "はし")
        val all = choicesOver(b, t, hashi)
        assertTrue(all.isNotEmpty())
        all.forEach { c -> assertFalse(c.toString(), "箸" in c || "端" in c || "嘴" in c) }
        assertTrue("대조군: 橋는 정답으로 나온다", all.all { "橋" in it })
    }

    @Test fun homophoneExcludedWhenOnlyFormsTableKnowsThem() {
        // 箸·端가 은행 어휘에 없고 jm_form에만 있어도 한자 치환(C1: 喬 등)·같은 읽기 표기로 나오지 않아야 한다.
        val vs = TestData.vocab.filter { it.written != null && it.id !in setOf(TestData.id("箸", "はし"), TestData.id("端", "はし")) }
        val without = bank(vs, conflicts = TestData.conflicts)
        // 箸는 on チョ(≠キョウ)라 C1 후보가 아니다. 대조는 会う 쪽에서 한다(아래). 여기서는 배제만 확인.
        val withRows = bank(vs, extraForms = setOf("箸" to "はし", "端" to "はし", "嘴" to "はし"), conflicts = TestData.conflicts)
        choicesOver(withRows, t, TestData.id("橋", "はし")).forEach { assertFalse(it.toString(), "箸" in it || "端" in it || "嘴" in it) }
        assertTrue(choicesOver(without, t, TestData.id("橋", "はし")).isNotEmpty())
    }

    @Test fun bankHomophonesExcludedEvenIfFormsHaveOnlyOwnPair() {
        val b = bank(TestData.vocab, conflicts = TestData.conflicts)
        val ownOnly = QuizBank(5, TestData.vocab, TestData.kanji, setOf("橋" to "はし") + b.forms.filter { it.second != "はし" }, b.conflicts)
        choicesOver(ownOnly, t, TestData.id("橋", "はし")).also { assertTrue(it.isNotEmpty()) }.forEach { assertFalse(it.toString(), "箸" in it || "端" in it) }
    }

    @Test fun variantsAndHomophonesOfAuExcluded() {
        val b = TestData.bank
        val all = choicesOver(b, t, TestData.id("会う", "あう"))
        assertTrue(all.isNotEmpty())
        all.forEach { assertFalse(it.toString(), "合う" in it || "逢う" in it) }
    }

    @Test fun kanjiSwapCandidatesExistAndFormsRowsBlockThem() {
        // 은행에 会う 하나뿐, 한자 치환(合う·逢う·遇う)만 후보. 행이 없으면 만들어지고, 있으면 막힌다.
        val au = vocab("jm:1", "会う", "あう", "만나다", "v5u")
        val bare = bank(listOf(au))
        val q = ok(QuizGen.generate(bare, t, "jm:1", 3L, 1))
        assertEquals(setOf("会う", "合う", "逢う", "遇う"), q.choices.toSet())
        assertEquals(listOf(DistractorKind.KANJI_SWAP), q.why.filterNotNull().map { it.kind }.distinct())
        q.why.filterNotNull().forEach { assertEquals("会", it.swap!!.first); assertEquals(it.text[0].toString(), it.swap!!.second) }
        val blocked1 = bank(listOf(au), extraForms = setOf("合う" to "あう", "逢う" to "あう"))
        assertEquals(SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(blocked1, t, "jm:1", 3L, 1)))
        val blocked3 = bank(listOf(au), extraForms = setOf("合う" to "あう", "逢う" to "あう", "遇う" to "あう"))
        assertEquals(SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(blocked3, t, "jm:1", 3L, 1)))
    }

    @Test fun swapOnlyUsesBankKanji() {
        val au = vocab("jm:1", "会う", "あう", "만나다", "v5u")
        val noBankKanji = bank(listOf(au), kanji = TestData.kanji.filter { it.literal in setOf("会", "合") })
        assertEquals(SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(noBankKanji, t, "jm:1", 1L, 0)))
        val lowLevel = bank(listOf(au), kanji = TestData.kanji.map { if (it.literal in setOf("合", "逢", "遇")) it.copy(level = 3) else it })  // 더 어려운 급수(3 < 5)
        assertEquals(SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(lowLevel, t, "jm:1", 1L, 0)))
    }

    @Test fun onReadingSharingKanjiAreUsedBeforeKunFallback() {
        // 橋(キョウ)와 on이 같은 喬만 치환 후보다. 箸·端는 kun(はし)만 같으므로 on 후보가 있으면 쓰지 않는다.
        val hashi = vocab("jm:1", "橋", "はし", "다리")
        val fillers = listOf("山" to "やま", "川" to "かわ", "木" to "き", "月" to "つき").mapIndexed { i, (w, r) -> vocab("jm:${i + 2}", w, r, "뜻$i") }
        val b = bank(listOf(hashi) + fillers)
        val swaps = distractorsOver(b, t, "jm:1").filter { it.kind == DistractorKind.KANJI_SWAP }
        assertTrue(swaps.isNotEmpty()); assertEquals(setOf("喬"), swaps.map { it.text }.toSet())
    }

    @Test fun nonJmAndKanaWrittenVocabNeverUsed() {
        val extra = listOf(vocab("av-77", "学園", "がくえん", "학원"), vocab("av-78", "大学生", "だいがくせい", "대학생"))
        val b = bank(TestData.vocab + extra, conflicts = TestData.conflicts)
        for (type in GenType.entries) {
            assertTrue(QuizGen.eligible(b, type).none { it.startsWith("av-") })
            for (id in QuizGen.eligible(b, type).take(12)) assertTrue(distractorsOver(b, type, id).none { it.sourceVocabId?.startsWith("av-") == true })
            assertTrue(choicesOver(b, type, TestData.id("学校", "がっこう")).flatten().none { it == "がくえん" || it == "学園" || it == "학원" || it == "大学生" })
        }
    }

    @Test fun questionShape() {
        val b = TestData.bank
        val q = ok(QuizGen.generate(b, t, TestData.id("学校", "がっこう"), 9L, 0))
        assertEquals("[[がっこう]]", q.stem); assertEquals("がっこう", q.target)
        assertEquals("学校", q.choices[0]); assertEquals(0, q.answerIndex)
        q.choices.forEach { assertTrue("$it 에 한자가 없다", it.any { c -> c in '一'..'鿿' }) }
        assertEquals(4, q.choices.toSet().size)
        q.why.filterNotNull().forEach { assertTrue(it.kind == DistractorKind.KANJI_SWAP || it.kind == DistractorKind.OTHER_WRITTEN) }
    }

    @Test fun differentPositionsFirst() {
        // 두 글자 단어의 한자 치환은 위치가 서로 달라야 먼저 뽑힌다(그 뒤에 같은 위치).
        val ws = vocab("jm:1", "今月", "こんげつ", "이달")
        val bk = bank(listOf(ws), kanji = TestData.kanji)  // 今(コン,キン)·金(キン,コン) / 月(ゲツ,ガツ)에 공유 on 없음 → 치환 후보는 위치 0뿐
        assertEquals(SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(bk, t, "jm:1", 1L, 0)))
    }
}

// ③ 뜻 ------------------------------------------------------------------------------------------------
class MeaningTest {
    private fun ja(id: String) = TestData.vocab.first { it.id == id }.let { it.written ?: it.reading }

    @Test fun conflictsNeverTogether() {
        val b = TestData.bank
        for (t in listOf(GenType.MEANING_JA_KO, GenType.MEANING_KO_JA)) {
            val chichi = TestData.id("父", "ちち"); val papa = TestData.id(null, "パパ")
            for (c in choicesOver(b, t, chichi)) assertFalse("$t $c", "아빠" in c || "パパ" in c)
            for (c in choicesOver(b, t, papa)) assertFalse("$t $c", "아버지" in c || "父" in c)
            val haha = TestData.id("母", "はは")
            for (c in choicesOver(b, t, haha)) assertFalse("$t $c", "어머님" in c || "お母さん" in c)
        }
        // 대조군: 충돌 목록이 없으면 같은 은행에서 오답으로 나온다 → 막은 것은 충돌 목록이다.
        val loose = bank(TestData.vocab)
        assertTrue(choicesOver(loose, GenType.MEANING_JA_KO, TestData.id("母", "はは")).any { "어머님" in it })
        assertTrue(choicesOver(loose, GenType.MEANING_KO_JA, TestData.id("母", "はは")).any { "お母さん" in it })
    }

    @Test fun asymmetricConflictDataIsTreatedAsConflictBothWays() {
        val vs = TestData.vocab
        val papaToChichi = mapOf(TestData.id(null, "パパ") to setOf(TestData.id("父", "ちち")))   // パパ 쪽에만 있다
        val b = bank(vs, conflicts = papaToChichi)
        choicesOver(b, GenType.MEANING_JA_KO, TestData.id("父", "ちち")).also { assertTrue(it.isNotEmpty()) }.forEach { assertFalse(it.toString(), "아빠" in it) }
    }

    @Test fun unreviewedIsSkippedAsAnswerAndAbsentAsDistractor() {
        val b = TestData.bank
        val un = TestData.vocab.first { it.meaningKo == null }.id
        for (t in listOf(GenType.MEANING_JA_KO, GenType.MEANING_KO_JA)) {
            assertEquals(SkipReason.UNREVIEWED, skip(QuizGen.generate(b, t, un, 1L, 0)))
            assertFalse(QuizGen.eligible(b, t).contains(un))
            val gakusei = TestData.id("学生", "がくせい")
            assertTrue(distractorsOver(b, t, gakusei).none { it.sourceVocabId == un })
        }
        // 공백뿐인 뜻도 미검수와 같다.
        val blank = vocab("jm:800", "犬", "いぬ", "  ")
        assertEquals(SkipReason.UNREVIEWED, skip(QuizGen.generate(bank(TestData.vocab + blank), GenType.MEANING_JA_KO, "jm:800", 1L, 0)))
        // ①②는 뜻을 쓰지 않으므로 미검수여도 대상이다.
        val unKanji = vocab("jm:801", "犬", "いぬ", null)
        assertTrue(QuizGen.eligible(bank(TestData.vocab + unKanji), GenType.KANJI_READING).contains("jm:801"))
    }

    @Test fun onlySamePosGroup() {
        val b = TestData.bank
        for (t in listOf(GenType.MEANING_JA_KO, GenType.MEANING_KO_JA)) {
            for (id in listOf(TestData.id("言う", "いう"), TestData.id("学生", "がくせい"), TestData.id("美しい", "うつくしい"))) {
                val want = posGroup(TestData.vocab.first { it.id == id }.pos)
                val ds = distractorsOver(b, t, id)
                assertTrue("$t $id 생성 안 됨", ds.isNotEmpty() || id == TestData.id("美しい", "うつくしい"))
                ds.forEach { d -> assertEquals(want, posGroup(TestData.vocab.first { it.id == d.sourceVocabId }.pos)) }
            }
        }
    }

    @Test fun sameVisibleHomographsNeverTogether() {
        val b = TestData.bank
        val kata = TestData.id("方", "かた"); val hou = TestData.id("方", "ほう")
        for (t in listOf(GenType.MEANING_JA_KO, GenType.MEANING_KO_JA)) {
            assertTrue(distractorsOver(b, t, kata).none { it.sourceVocabId == hou })
            assertTrue(distractorsOver(b, t, hou).none { it.sourceVocabId == kata })
        }
        // 한자·가나가 같은 단어가 선택지에 둘 나오지 않는다(ko-ja).
        for (c in choicesOver(b, GenType.MEANING_KO_JA, kata)) assertEquals(c.toString(), c.toSet().size, 4)
    }

    @Test fun sameKoreanMeaningRejectedEvenWithoutConflictRow() {
        val a = vocab("jm:1", "犬", "いぬ", "개"); val dup = vocab("jm:2", "イヌ", "イヌ", "개")
        val rest = (3..8).map { vocab("jm:$it", "語$it", "ご$it", "뜻$it") }
        val b = bank(listOf(a, dup) + rest)
        assertTrue(distractorsOver(b, GenType.MEANING_JA_KO, "jm:1").none { it.sourceVocabId == "jm:2" })
        assertTrue(distractorsOver(b, GenType.MEANING_KO_JA, "jm:1").none { it.sourceVocabId == "jm:2" })
    }

    @Test fun missingConflictRowMeansConflictDataIsNotTrusted() {
        val a = vocab("jm:1", "犬", "いぬ", "개")
        val rest = (2..9).map { vocab("jm:$it", "語$it", "ご$it", "뜻$it") }
        val full = bank(listOf(a) + rest)
        assertEquals(9, QuizGen.eligible(full, GenType.MEANING_JA_KO).size)
        val noRows = QuizBank(5, listOf(a) + rest, emptyList(), emptySet(), emptyMap())
        assertEquals(emptyList<String>(), QuizGen.eligible(noRows, GenType.MEANING_JA_KO))
        assertEquals(SkipReason.NOT_TARGET, skip(QuizGen.generate(noRows, GenType.MEANING_JA_KO, "jm:1", 1L, 0)))
        // 후보 쪽 행이 빠져도 그 후보는 쓰지 않는다.
        val partial = QuizBank(5, listOf(a) + rest, emptyList(), emptySet(), mapOf("jm:1" to emptySet(), "jm:2" to emptySet(), "jm:3" to emptySet(), "jm:4" to emptySet()))
        val q = ok(QuizGen.generate(partial, GenType.MEANING_JA_KO, "jm:1", 1L, 0))
        assertEquals(setOf("jm:2", "jm:3", "jm:4"), q.why.filterNotNull().map { it.sourceVocabId }.toSet())
    }

    @Test fun shapes() {
        val b = TestData.bank
        val gakusei = TestData.id("学生", "がくせい")
        val jaKo = ok(QuizGen.generate(b, GenType.MEANING_JA_KO, gakusei, 4L, 1))
        assertEquals("[[学生]]", jaKo.stem); assertEquals("学生", jaKo.target)
        assertEquals(TestData.vocab.first { it.id == gakusei }.meaningKo, jaKo.choices[1])
        jaKo.why.filterNotNull().forEach { assertEquals(DistractorKind.OTHER_MEANING, it.kind); assertNotNull(it.sourceVocabId) }
        val koJa = ok(QuizGen.generate(b, GenType.MEANING_KO_JA, gakusei, 4L, 3))
        assertEquals(TestData.vocab.first { it.id == gakusei }.meaningKo, koJa.stem); assertNull(koJa.target)
        assertEquals("学生", koJa.choices[3])
        koJa.why.filterNotNull().forEach { assertEquals(DistractorKind.OTHER_WORD, it.kind); assertEquals(ja(it.sourceVocabId!!), it.text) }
        // 표기가 없으면 읽기가 보이는 글자다.
        val papa = ok(QuizGen.generate(b, GenType.MEANING_JA_KO, TestData.id(null, "パパ"), 4L, 0))
        assertEquals("[[パパ]]", papa.stem); assertEquals("パパ", papa.target)
    }
}

// 후보 부족·결정성·ID ------------------------------------------------------------------------------------
class SkipAndDeterminismTest {
    @Test fun tooFewDistractorsIsSkippedNotLoosened() {
        val a = vocab("jm:1", "犬", "いぬ", "개"); val b1 = vocab("jm:2", "猫", "ねこ", "고양이"); val b2 = vocab("jm:3", "鳥", "とり", "새")
        val tiny = bank(listOf(a, b1, b2))           // 오답 후보 2개
        // ①은 소리 변형만으로 오답이 나오므로 제외. ②(한자 치환 후보 없음, 같은 글자 수 어휘 2개)·③은 후보 부족.
        for (t in GenType.entries.filter { it != GenType.KANJI_READING }) {
            assertEquals(t.code, SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(tiny, t, "jm:1", 1L, 0)))
            assertEquals(emptyList<GeneratedQuestion>(), QuizGen.buildSession(tiny, t, 1L, 10))
        }
        // 후보 3개가 있어도 하나가 충돌이면 건너뛴다(3개 미만).
        val b3 = vocab("jm:4", "魚", "さかな", "물고기")
        val conflicted = bank(listOf(a, b1, b2, b3), conflicts = sym("jm:1" to "jm:4"))
        assertEquals(SkipReason.TOO_FEW_DISTRACTORS, skip(QuizGen.generate(conflicted, GenType.MEANING_JA_KO, "jm:1", 1L, 0)))
        assertTrue(QuizGen.generate(bank(listOf(a, b1, b2, b3)), GenType.MEANING_JA_KO, "jm:1", 1L, 0) is GenResult.Ok)
    }

    @Test fun deterministicAndInputOrderIndependent() {
        val b = TestData.bank
        val shuffled = QuizBank(5, TestData.vocab.reversed(), TestData.kanji.reversed(), b.forms, b.conflicts)
        for (t in GenType.entries) for (id in QuizGen.eligible(b, t).take(6)) for (seed in 1L..20L) {
            val r1 = QuizGen.generate(b, t, id, seed, (seed % 4).toInt())
            assertEquals(r1, QuizGen.generate(b, t, id, seed, (seed % 4).toInt()))
            assertEquals("입력 순서 무관 $t $id $seed", r1, QuizGen.generate(shuffled, t, id, seed, (seed % 4).toInt()))
        }
    }

    @Test fun differentSeedsGiveDifferentQuestions() {
        val b = TestData.bank; val id = TestData.id("学生", "がくせい")
        assertTrue((1L..50L).map { ok(QuizGen.generate(b, GenType.KANJI_READING, id, it, 0)).choices.toSet() }.distinct().size > 3)
    }

    @Test fun eligibleSortedAndMatchesTargets() {
        val b = TestData.bank
        for (t in GenType.entries) { val e = QuizGen.eligible(b, t); assertEquals(e.sorted(), e); assertTrue(e.isNotEmpty()) }
        assertFalse(QuizGen.eligible(b, GenType.MEANING_JA_KO).any { id -> TestData.vocab.first { it.id == id }.meaningKo == null })
    }

    @Test fun idRoundTrip() {
        val b = TestData.bank; val id = TestData.id("学校", "がっこう")
        for (t in GenType.entries) {
            val q = (QuizGen.generate(b, t, id, 123456789L, 2) as GenResult.Ok).q
            assertEquals(QuizGen.idOf(GenId(t, 2, 123456789L, id)), q.id)
            assertEquals(GenId(t, 2, 123456789L, id), QuizGen.parseId(q.id))
            assertEquals(GenResult.Ok(q), QuizGen.regenerate(b, q.id))
        }
        assertEquals("gq:kanji-reading:2:${java.lang.Long.toUnsignedString(-1L, 36)}:jm:1", QuizGen.idOf(GenId(GenType.KANJI_READING, 2, -1L, "jm:1")))
        for (seed in listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 35L, 36L)) {
            val g = GenId(GenType.ORTHOGRAPHY, 3, seed, "jm:1358280")
            assertEquals(g, QuizGen.parseId(QuizGen.idOf(g)))
        }
    }

    @Test fun parseIdExample() {
        val g = QuizGen.parseId("gq:kanji-reading:2:abc:jm:1358280")!!
        assertEquals(GenType.KANJI_READING, g.type); assertEquals(2, g.slot); assertEquals("jm:1358280", g.vocabId)
        assertEquals(java.lang.Long.parseUnsignedLong("abc", 36), g.seed)
    }

    @Test fun parseIdRejectsMalformed() {
        for (bad in listOf("", "q-n5-vocab-0001", "gq:kanji-reading:2:abc", "gq:foo:1:a:jm:1", "gq:kanji-reading:4:a:jm:1", "gq:kanji-reading:-1:a:jm:1",
            "gq:kanji-reading:x:a:jm:1", "gq:kanji-reading:01:a:jm:1", "gq:kanji-reading:+1:a:jm:1", "gq:kanji-reading:1:A:jm:1", "gq:kanji-reading:1:+a:jm:1",
            "gq:kanji-reading:1::jm:1", "gq:kanji-reading:1:a:", "xx:kanji-reading:1:a:jm:1", "gq:kanji-reading:1:zzzzzzzzzzzzzzzzzzzz:jm:1"))
            assertNull(bad, QuizGen.parseId(bad))
        assertEquals(SkipReason.UNKNOWN_VOCAB, skip(QuizGen.regenerate(TestData.bank, "not-an-id")))
        assertEquals(SkipReason.UNKNOWN_VOCAB, skip(QuizGen.regenerate(TestData.bank, "gq:kanji-reading:1:a:jm:99999")))
    }

    @Test fun genTypeLookup() {
        GenType.entries.forEach { assertEquals(it, GenType.of(it.code)) }
        assertNull(GenType.of("context"))
        assertEquals(listOf("kanji-reading", "orthography", "meaning-ja-ko", "meaning-ko-ja"), GenType.entries.map { it.code })
    }

    @Test fun badSlotRejected() {
        val id = TestData.id("学生", "がくせい")
        for (s in listOf(-1, 4)) try { QuizGen.generate(TestData.bank, GenType.KANJI_READING, id, 1L, s); throw AssertionError("slot $s") } catch (_: IllegalArgumentException) {}
    }
}
