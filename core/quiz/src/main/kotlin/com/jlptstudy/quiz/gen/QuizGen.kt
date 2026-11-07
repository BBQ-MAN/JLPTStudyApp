package com.jlptstudy.quiz.gen

import java.util.Collections
import java.util.Random
import kotlin.math.abs

/**
 * 규칙 기반 4지선다 생성기(quizgen.md 4·5절). 입력은 [QuizBank]뿐이고 DB·Android를 모른다.
 * 오답은 유형별 배제 규칙을 통과한 것만 쓰고, 후보가 3개 미만이면 규칙을 느슨하게 하지 않고 [SkipReason.TOO_FEW_DISTRACTORS]로 건너뛴다.
 */
object QuizGen {
    private const val DISTRACTORS = 3

    // --- 대상 판정 -------------------------------------------------------------------------------------

    /** 대상이 아닌 이유. null이면 대상. 오답이 충분한지는 보지 않는다. */
    private fun targetReason(bank: QuizBank, type: GenType, v: QuizVocab): SkipReason? = when (type) {
        GenType.KANJI_READING, GenType.ORTHOGRAPHY -> {
            val w = v.written
            // 자기 (표기, 읽기) 쌍이 jm_form에 없으면 배제 데이터가 로드되지 않은 것이다 → 만들지 않는다.
            if (w == null || w.none(::isKanji) || w == v.reading || !isHiraReading(v.reading) ||
                (norm(w) to rk(v.reading)) !in bank.formKeys
            ) SkipReason.NOT_TARGET else null
        }
        GenType.MEANING_JA_KO, GenType.MEANING_KO_JA ->
            if (v.meaningKo.isNullOrBlank()) SkipReason.UNREVIEWED else if (v.id !in bank.conflicts) SkipReason.NOT_TARGET else null
    }

    fun eligible(bank: QuizBank, type: GenType): List<String> = bank.jm.filter { targetReason(bank, type, it) == null }.map { it.id }

    // --- 생성 -------------------------------------------------------------------------------------------

    fun generate(bank: QuizBank, type: GenType, vocabId: String, seed: Long, slot: Int): GenResult {
        require(slot in 0..3) { "slot $slot" }
        val a = bank.byId[vocabId] ?: return GenResult.Skip(SkipReason.UNKNOWN_VOCAB)
        targetReason(bank, type, a)?.let { return GenResult.Skip(it) }
        val rnd = Random(seed)
        val (stem, target, answer, ds) = when (type) {
            GenType.KANJI_READING -> kanjiReading(bank, a, rnd)
            GenType.ORTHOGRAPHY -> orthography(bank, a, rnd)
            GenType.MEANING_JA_KO, GenType.MEANING_KO_JA -> meaning(bank, type, a, rnd)
        }
        if (ds.size < DISTRACTORS) return GenResult.Skip(SkipReason.TOO_FEW_DISTRACTORS)
        // slot은 정답 자리만 정한다: 고른 오답 3개를 고른 순서대로 남은 자리 0..3에 채운다.
        val choices = ArrayList<String>(4); val why = ArrayList<Distractor?>(4)
        var next = 0
        for (i in 0..3) if (i == slot) { choices += answer; why += null } else { choices += ds[next].text; why += ds[next]; next++ }
        return GenResult.Ok(GeneratedQuestion(idOf(GenId(type, slot, seed, vocabId)), type, vocabId, stem, target, choices, slot, why))
    }

    private data class Built(val stem: String, val target: String?, val answer: String, val distractors: List<Distractor>)

    /** 오답을 최대 3개 모은다. [key]가 같은 글자(정답·이미 고른 것 포함)는 받지 않고, [ok]를 통과해야 한다. */
    private class Picker<T>(answerKeys: Collection<String>, val key: (T) -> String, val ok: (T) -> Boolean, val toD: (T) -> Distractor) {
        val keys = HashSet(answerKeys)
        val picked = ArrayList<Distractor>()
        fun full() = picked.size >= DISTRACTORS
        fun add(c: T): Boolean {
            if (full()) return false
            val k = key(c)
            if (k in keys || !ok(c)) return false
            keys += k; picked += toD(c); return true
        }
        /** 첫 바퀴: [group]마다 하나씩, 둘째 바퀴: 아무거나. */
        fun addDiverse(cands: List<T>, group: (T) -> Any) {
            val seen = HashSet<Any>()
            for (c in cands) if (!full() && group(c) !in seen && add(c)) seen += group(c)
            for (c in cands) add(c)
        }
    }

    private fun <T> shuffled(xs: List<T>, rnd: Random): List<T> = xs.toMutableList().also { Collections.shuffle(it, rnd) }

    // ① 한자 읽기
    private fun kanjiReading(bank: QuizBank, a: QuizVocab, rnd: Random): Built {
        val w = norm(a.written!!); val r = rk(a.reading)
        val x1 = bank.readingsOfWritten[w].orEmpty()                       // 그 표기의 모든 읽기(+ 같은 표기의 다른 은행 어휘)
        val p = Picker<Distractor>(listOf(r), { rk(it.text) }, { rk(it.text) !in x1 && it.text.isNotBlank() }, { it })
        val c1 = shuffled(KanaVariants.of(r).map { (t, k) -> Distractor(t, k) }, rnd)
        p.addDiverse(c1) { it.kind }
        val wKanji = w.filter(::isKanji).toSet()
        val c2 = shuffled(
            bank.jm.filter { it.id != a.id && isHiraReading(it.reading) && abs(it.reading.length - r.length) <= 1 }
                .sortedWith(compareBy({ it.reading }, { it.id })), rnd,
        ).sortedBy { v -> if (v.written.orEmpty().any { it in wKanji }) 0 else 1 }   // 한자를 공유하는 단어 먼저(안정 정렬)
        for (v in c2) p.add(Distractor(v.reading, DistractorKind.OTHER_READING, v.id))
        return Built("[[$w]]", w, a.reading, p.picked)
    }

    // ② 표기
    private fun orthography(bank: QuizBank, a: QuizVocab, rnd: Random): Built {
        val w = norm(a.written!!); val r = rk(a.reading)
        val x2 = bank.writtensOfReading[r].orEmpty()                       // 그 읽기의 모든 표기(동음·이표기 + 같은 읽기의 은행 어휘)
        val p = Picker<Distractor>(listOf(w), { norm(it.text) }, { norm(it.text) !in x2 && it.text.any(::isKanji) }, { it })
        // C1: 한자 치환
        val swaps = ArrayList<Pair<Int, Distractor>>()
        val cps = w.codePoints().toArray()
        cps.forEachIndexed { i, cp ->
            val from = String(Character.toChars(cp))
            val src = bank.bankKanji[from] ?: return@forEachIndexed
            val others = bank.bankKanji.values.filter { it.literal != from }
            val onSrc = src.on.mapTo(HashSet()) { Kana.hira(it) }
            val byOn = others.filter { o -> o.on.any { Kana.hira(it) in onSrc } }
            val kunSrc = kunStems(src)
            val chosen = byOn.ifEmpty { others.filter { o -> kunStems(o).any { it in kunSrc } } }
            for (o in chosen) {
                val text = buildString { cps.forEachIndexed { j, c -> if (j == i) append(o.literal) else appendCodePoint(c) } }
                swaps += i to Distractor(text, DistractorKind.KANJI_SWAP, swap = from to o.literal)
            }
        }
        val c1 = shuffled(swaps.distinctBy { it.second.text }.sortedBy { it.second.text }, rnd)
        val pos = HashMap<String, Int>().also { m -> c1.forEach { m[it.second.text] = it.first } }
        p.addDiverse(c1.map { it.second }) { pos.getValue(it.text) }
        // C2: 같은 급수 다른 어휘의 표기(글자 수 같음, 끝 가나 같은 것 먼저)
        val tail = tailAfterKanji(w)
        val c2 = shuffled(
            bank.jm.filter { it.id != a.id && it.written != null && it.written.codePointCount(0, it.written.length) == cps.size }
                .sortedWith(compareBy({ it.written }, { it.id })), rnd,
        ).sortedBy { if (tailAfterKanji(norm(it.written!!)) == tail) 0 else 1 }
        for (v in c2) p.add(Distractor(v.written!!, DistractorKind.OTHER_WRITTEN, v.id))
        return Built("[[${a.reading}]]", a.reading, a.written, p.picked)
    }

    /** `.` 앞부분, `-` 제거(あ.う → あ, -か → か). */
    private fun kunStems(k: QuizKanji): Set<String> = k.kun.map { it.replace("-", "").substringBefore('.') }.filter { it.isNotEmpty() }.toSet()

    private fun tailAfterKanji(w: String): String = w.substring(w.indexOfLast(::isKanji) + 1)

    // ③ 뜻
    private fun meaning(bank: QuizBank, type: GenType, a: QuizVocab, rnd: Random): Built {
        val jaToKo = type == GenType.MEANING_JA_KO
        val aJa = a.written ?: a.reading
        val aKo = a.meaningKo!!
        val aGroup = posGroup(a.pos)
        val ca = bank.conflicts[a.id].orEmpty()
        val cands = shuffled(bank.jm.filter { it.id != a.id }, rnd).filter { b ->
            !b.meaningKo.isNullOrBlank() && posGroup(b.pos) == aGroup &&
                b.id in bank.conflicts &&                                      // 충돌 행이 없는 후보는 믿지 않는다
                b.id !in ca && a.id !in bank.conflicts.getValue(b.id)          // 한쪽에만 적혀 있어도 충돌
        }
        val koKey = { b: QuizVocab -> "ko:" + norm(b.meaningKo!!.trim()) }
        val jaKey = { b: QuizVocab -> "ja:" + norm(b.written ?: b.reading) }
        val keysOfAnswer = listOf("ko:" + norm(aKo.trim()), "ja:" + norm(aJa))
        val picked = ArrayList<Distractor>(); val seen = HashSet(keysOfAnswer)
        for (b in cands) {
            if (picked.size >= DISTRACTORS) break
            // 한국어 뜻·일본어 글자 둘 다 정답·이미 고른 것과 달라야 한다(같은 글자 方 かた/ほう, 같은 뜻 母/お母さん 방지).
            if (koKey(b) in seen || jaKey(b) in seen) continue
            seen += koKey(b); seen += jaKey(b)
            picked += if (jaToKo) Distractor(b.meaningKo!!, DistractorKind.OTHER_MEANING, b.id) else Distractor(b.written ?: b.reading, DistractorKind.OTHER_WORD, b.id)
        }
        return if (jaToKo) Built("[[$aJa]]", aJa, aKo, picked) else Built(aKo, null, aJa, picked)
    }

    // --- 세션 ------------------------------------------------------------------------------------------

    private fun mix(sessionSeed: Long, k: Int): Long {
        var z = sessionSeed + (k + 1L) * -0x61c8864680b583ebL            // 0x9E3779B97F4A7C15
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L                   // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L                   // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** 같은 sessionSeed → 같은 목록. 후보가 모자라면 size보다 짧다. 한 자리의 문항 수는 ⌈m/4⌉ 이하. */
    fun buildSession(bank: QuizBank, type: GenType, sessionSeed: Long, size: Int): List<GeneratedQuestion> {
        if (size <= 0) return emptyList()
        val ids = shuffled(eligible(bank, type), Random(sessionSeed))
        val chosen = ArrayList<Pair<String, Long>>()
        for ((k, id) in ids.withIndex()) {
            if (chosen.size >= size) break
            val qSeed = mix(sessionSeed, k)
            if (generate(bank, type, id, qSeed, 0) is GenResult.Ok) chosen += id to qSeed   // 오답 선택은 slot과 무관하다
        }
        val slots = shuffled((0 until chosen.size).map { it % 4 }, Random(sessionSeed xor 0x5DEECE66DL))
        return chosen.mapIndexed { j, (id, qSeed) -> (generate(bank, type, id, qSeed, slots[j]) as GenResult.Ok).q }
    }

    // --- ID --------------------------------------------------------------------------------------------

    fun idOf(g: GenId): String = "gq:${g.type.code}:${g.slot}:${java.lang.Long.toUnsignedString(g.seed, 36)}:${g.vocabId}"

    /** 형식이 아니거나 idOf로 되돌렸을 때 글자가 달라지는 것(대문자·부호·앞 0)은 null. */
    fun parseId(id: String): GenId? {
        val p = id.split(":", limit = 5)
        if (p.size != 5 || p[0] != "gq" || p[4].isEmpty()) return null
        val type = GenType.of(p[1]) ?: return null
        val slot = p[2].toIntOrNull()?.takeIf { it in 0..3 } ?: return null
        val seed = try { java.lang.Long.parseUnsignedLong(p[3], 36) } catch (_: NumberFormatException) { return null }
        return GenId(type, slot, seed, p[4]).takeIf { idOf(it) == id }
    }

    fun regenerate(bank: QuizBank, id: String): GenResult {
        val g = parseId(id) ?: return GenResult.Skip(SkipReason.UNKNOWN_VOCAB)
        return generate(bank, g.type, g.vocabId, g.seed, g.slot)
    }

    // --- 문자 ------------------------------------------------------------------------------------------

    private fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'
    private fun isHiraReading(s: String): Boolean = s.isNotEmpty() && s.all { it in 'ぁ'..'ゖ' || it == 'ー' }
}

