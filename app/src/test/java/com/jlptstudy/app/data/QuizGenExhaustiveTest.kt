package com.jlptstudy.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.data.content.RoomQuizGenRepository
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.gen.DistractorKind
import com.jlptstudy.quiz.gen.GenId
import com.jlptstudy.quiz.gen.GenResult
import com.jlptstudy.quiz.gen.GenType
import com.jlptstudy.quiz.gen.GeneratedQuestion
import com.jlptstudy.quiz.gen.QuizBank
import com.jlptstudy.quiz.gen.QuizGen
import com.jlptstudy.quiz.gen.SkipReason
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.text.Normalizer

private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
private fun hira(s: String) = buildString { for (ch in nfc(s)) append(if (ch in 'ァ'..'ヶ') ch - 0x60 else ch) }

/**
 * quizgen.md 7절 전수 검사. 오라클은 생성기·ContentDao를 부르지 않고 번들 content.db를 원시 SQL로 직접 읽은 표(jm_form·vocab_conflict·
 * vocab·vocab_meaning)다. 생성기가 만든 문항을 이 표로만 판정한다. 하한을 못 넘으면 규칙을 느슨하게 하지 않고 실패 그대로 둔다.
 */
@RunWith(RobolectricTestRunner::class)
class QuizGenExhaustiveTest {
    private val context get() = RuntimeEnvironment.getApplication()

    // ---- 오라클 표(원시 SQL) ----------------------------------------------------------------------------

    private class RawVocab(val id: String, val written: String?, val reading: String, val level: Int, val pos: String)
    private class Oracle(
        val vocab: Map<String, RawVocab>,                       // 모든 vocab 행(av-·retired 포함)
        val retired: Set<String>,
        val meanings: Map<String, List<Triple<Int, String, String>>>,   // vocab_id → (ord, text_ko, status)
        val forms: Set<Pair<String, String>>,                   // (written, reading)
        val conflicts: Map<String, Set<String>>,
        val reviewedTexts: Set<String>,
        val nonReviewedOnlyTexts: Set<String>,
    ) {
        fun ord0(id: String) = meanings[id]?.firstOrNull { it.first == 0 }
        val byWritten: Map<String, List<RawVocab>> = vocab.values.filter { it.written != null }.groupBy { it.written!! }
        val byReading: Map<String, List<RawVocab>> = vocab.values.groupBy { hira(it.reading) }
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> = query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
    }

    private fun loadOracle(db: SupportSQLiteDatabase): Oracle {
        val vocab = db.rows("SELECT id, written, reading, level, pos FROM vocab").associate { it[0]!! to RawVocab(it[0]!!, it[1], it[2]!!, it[3]!!.toInt(), it[4]!!) }
        val meanings = db.rows("SELECT vocab_id, ord, text_ko, ko_review_status FROM vocab_meaning ORDER BY vocab_id, ord")
            .groupBy({ it[0]!! }, { Triple(it[1]!!.toInt(), it[2]!!, it[3]!!) })
        val reviewed = meanings.values.flatten().filter { it.third == "reviewed" }.map { it.second }.toSet()
        val other = meanings.values.flatten().filter { it.third != "reviewed" }.map { it.second }.toSet() - reviewed
        return Oracle(
            vocab, db.rows("SELECT id FROM retired").map { it[0]!! }.toSet(), meanings,
            db.rows("SELECT written, reading FROM jm_form").map { it[0]!! to it[1]!! }.toSet(),
            db.rows("SELECT vocab_id, conflict_ids FROM vocab_conflict").associate { r -> r[0]!! to JSONArray(r[1]!!).let { a -> (0 until a.length()).map(a::getString).toSet() } },
            reviewed, other,
        )
    }

    // ---- 테스트 쪽 독립 구현(생성기 코드를 부르지 않는다) --------------------------------------------------------

    private fun visible(s: String) = Furigana.parse(s).joinToString("") { it.base }
    private fun hasKanji(s: String) = s.any { it in '一'..'鿿' || it in '㐀'..'䶿' }
    private fun isHiraOnly(s: String) = s.isNotEmpty() && s.all { it in 'ぁ'..'ゖ' || it == 'ー' }

    /** 5.4 품사군 */
    private fun posGroup(pos: String): String {
        val f = pos.substringBefore(',').trim()
        return when {
            listOf("v1", "v2", "v4", "v5", "vk", "vs-", "vz", "vn", "vr").any { f.startsWith(it) } -> "verb"
            f == "adj-i" || f == "adj-ix" -> "adj-i"
            f == "adj-na" -> "adj-na"
            f == "adv" || f == "adv-to" -> "adv"
            f.startsWith("n") || f == "pn" -> "noun"
            else -> f
        }
    }

    /** 4.6 형식 규칙 */
    private fun variantViolation(s: String): String? {
        val small = "ぁぃぅぇぉゃゅょゎ"
        if (s.isEmpty()) return "empty"
        if (s.first() in "っー$small") return "첫 글자"
        if (s.last() == 'っ') return "끝 っ"
        if ("っっ" in s) return "っっ"
        for (i in s.indices) {
            if (s[i] == 'っ' && (s[i + 1] in "あいうえおんっー" || s[i + 1] in small)) return "っ 뒤"
            if (s[i] in "ゃゅょ" && (i == 0 || s[i - 1] !in "きぎしじちぢにひびぴみり")) return "작은 ゃゅょ 앞"
        }
        return null
    }

    // ---- 집계 ------------------------------------------------------------------------------------------

    private class Cover(val vocab: Int, val eligible: Int, val generatedAll: Int, val generatedAny: Int, val notTarget: Int, val unreviewed: Int, val tooFew: Int)

    private val seeds = listOf(0L, 1L, 2L, 3L, 42L, 20261108L, Long.MAX_VALUE, -1L)

    @Test fun `N5 N4 전수 검사 불변식 I1-I10과 하한`() = runBlocking {
        val db = ContentDatabase.open(context)
        try {
            val sql = db.openHelper.readableDatabase
            val o = loadOracle(sql)
            val repo = RoomQuizGenRepository(db.contentDao())
            val violations = mutableListOf<String>()
            fun check(ok: Boolean, msg: () -> String) { if (!ok && violations.size < 200) violations += msg() else if (!ok) violations += "…" }

            val coverage = mutableListOf<Pair<Pair<Int, GenType>, Cover>>()
            val skipped = mutableListOf<String>()
            var checked = 0

            for (level in listOf(5, 4)) {
                val bank = repo.bank(JlptLevel.fromN(level)!!)
                val ids = o.vocab.values.filter { it.level == level && it.id.startsWith("jm:") && it.id !in o.retired }.map { it.id }.sorted()
                for (type in GenType.entries) {
                    // 대상 판정을 오라클이 따로 계산해 어댑터가 행을 빠뜨리지 않았는지 본다.
                    val oracleEligible = ids.filter { id ->
                        val v = o.vocab.getValue(id)
                        when (type) {
                            GenType.KANJI_READING, GenType.ORTHOGRAPHY -> v.written != null && hasKanji(v.written) && v.written != v.reading &&
                                isHiraOnly(v.reading) && (nfc(v.written) to hira(v.reading)) in o.forms.mapTo(HashSet()) { nfc(it.first) to hira(it.second) }
                            else -> o.ord0(id)?.let { it.third == "reviewed" && it.second.isNotBlank() } == true && id in o.conflicts
                        }
                    }
                    assertEquals("L$level ${type.code} eligible", oracleEligible, QuizGen.eligible(bank, type))

                    val okAll = HashSet<String>(); val okAny = HashSet<String>()
                    val reasons = HashMap<String, SkipReason>()
                    for (id in ids) {
                        for ((si, seed) in seeds.withIndex()) {
                            val slot = si % 4
                            when (val r = QuizGen.generate(bank, type, id, seed, slot)) {
                                is GenResult.Skip -> { if (r.reason == SkipReason.TOO_FEW_DISTRACTORS || id !in reasons) reasons[id] = r.reason }
                                is GenResult.Ok -> okAny += id
                            }
                        }
                    }
                    for (id in ids) {
                        val oks = seeds.indices.count { si -> QuizGen.generate(bank, type, id, seeds[si], si % 4) is GenResult.Ok }
                        if (oks == seeds.size) okAll += id
                        else skipped += "$level\t${type.code}\t$id\t" + (reasons[id]?.name?.lowercase() ?: "too_few") + (if (oks in 1 until seeds.size) "\tpartial($oks/${seeds.size})" else "")
                    }

                    // 문항 검사: Ok였던 (어휘, 시드)마다 I1~I5·I7~I10
                    for ((si, seed) in seeds.withIndex()) {
                        val slot = si % 4
                        val qs = ids.mapNotNull { id -> (QuizGen.generate(bank, type, id, seed, slot) as? GenResult.Ok)?.q }
                        val contents = repo.contents(qs.map { it.id })
                        val contents2 = repo.contents(qs.map { it.id })
                        for (q in qs) {
                            checked++
                            val c = contents[q.id]
                            check(c != null) { "I5 ${q.id}: contents 없음" }
                            c ?: continue
                            checkQuestion(o, bank, level, type, seed, slot, q, c, ::check)
                            check(contents2[q.id] == c) { "I5 ${q.id}: contents 두 번이 다름" }
                            check(QuizGen.generate(bank, type, q.vocabId, seed, slot) == GenResult.Ok(q)) { "I5 ${q.id}: generate 두 번이 다름" }
                            check(QuizGen.regenerate(bank, q.id) == GenResult.Ok(q)) { "I5 ${q.id}: regenerate가 다름" }
                        }
                    }

                    // I6 정답 위치
                    val all = IntArray(4); var total = 0
                    for (sessionSeed in 1L..200L) {
                        val s = QuizGen.buildSession(bank, type, sessionSeed, 10)
                        val m = s.size
                        val perSlot = IntArray(4).also { a -> s.forEach { a[it.answerIndex]++ } }
                        val cap = (m + 3) / 4
                        check(perSlot.all { it <= cap }) { "I6 L$level ${type.code} 세션 $sessionSeed: $perSlot (m=$m)" }
                        check(s.map { it.vocabId }.distinct().size == m) { "I6 L$level ${type.code} 세션 $sessionSeed: 같은 단어 중복" }
                        perSlot.forEachIndexed { i, n -> all[i] += n }; total += m
                    }
                    if (total > 0) check(all.all { it.toDouble() / total < 0.40 }) { "I6 L$level ${type.code} 전체 집계 ${all.toList()}" }

                    coverage += (level to type) to Cover(
                        ids.size, oracleEligible.size, okAll.size, okAny.size,
                        reasons.values.count { it == SkipReason.NOT_TARGET }, reasons.values.count { it == SkipReason.UNREVIEWED },
                        reasons.values.count { it == SkipReason.TOO_FEW_DISTRACTORS },
                    )
                }
            }

            writeReports(o, coverage, skipped)
            println("QuizGen exhaustive: 검사한 문항 $checked, 위반 ${violations.size}")
            assertTrue("불변식 위반 ${violations.size}건:\n" + violations.take(60).joinToString("\n"), violations.isEmpty())

            fun ratio(level: Int, type: GenType) = coverage.first { it.first == level to type }.second.let { it.generatedAll.toDouble() / it.eligible }
            val floors = listOf(GenType.KANJI_READING to 0.90, GenType.ORTHOGRAPHY to 0.80, GenType.MEANING_JA_KO to 0.60, GenType.MEANING_KO_JA to 0.60)
            val misses = floors.filter { (t, f) -> ratio(5, t) < f }.map { (t, f) -> "N5 ${t.code} %.1f%% < %.0f%%".format(ratio(5, t) * 100, f * 100) }
            assertTrue("하한 미달(규칙을 느슨하게 하지 않음, needs-decision): $misses", misses.isEmpty())
        } finally {
            db.close()
        }
    }

    private fun checkQuestion(
        o: Oracle, bank: QuizBank, level: Int, type: GenType, seed: Long, slot: Int, q: GeneratedQuestion, c: QuizContent,
        check: (Boolean, () -> String) -> Unit,
    ) {
        val id = q.id
        val a = o.vocab.getValue(q.vocabId)
        // I1·I2
        check(c.choices.size == 4 && c.choices.all { visible(it).isNotBlank() }) { "I1 $id: 선택지 ${c.choices}" }
        check(c.choices.map { hira(visible(it)) }.distinct().size == 4) { "I2 $id: 보이는 글자 중복 ${c.choices}" }
        check(visible(c.stem).isNotBlank()) { "I1 $id: 지문 빔" }
        // I3
        when (type) {
            GenType.KANJI_READING -> {
                check(q.choices[q.answerIndex] == a.reading) { "I3 $id: 정답 ${q.choices[q.answerIndex]} != vocab.reading ${a.reading}" }
                for ((i, d) in q.choices.withIndex()) if (i != q.answerIndex) {
                    check((a.written!! to hira(d)) !in o.forms) { "I3 $id: 오답 $d 가 jm_form (${a.written}, ${hira(d)})" }
                    check(hira(d) != hira(a.reading)) { "I3 $id: 오답 $d == 정답 읽기" }
                    check(o.byWritten[a.written!!].orEmpty().none { hira(it.reading) == hira(d) }) { "I3 $id: 오답 $d 가 같은 표기 ${a.written} 의 다른 항목 읽기" }
                }
                check(q.stem == "[[${a.written}]]" && q.target == a.written) { "I3 $id: 지문 ${q.stem}" }
                check(c.stem == q.stem && c.choices == q.choices) { "I3 $id: ①은 루비 없음" }
            }
            GenType.ORTHOGRAPHY -> {
                check(q.choices[q.answerIndex] == a.written) { "I3 $id: 정답 ${q.choices[q.answerIndex]} != vocab.written ${a.written}" }
                for ((i, x) in q.choices.withIndex()) if (i != q.answerIndex) {
                    check((x to hira(a.reading)) !in o.forms) { "I3 $id: 오답 $x 가 jm_form ($x, ${hira(a.reading)})" }
                    check(o.byReading[hira(a.reading)].orEmpty().none { it.written == x }) { "I3 $id: 오답 $x 가 같은 읽기의 다른 항목 표기" }
                    check(x != a.written && hasKanji(x)) { "I3 $id: 오답 $x 한자 없음/같음" }
                }
                check(q.stem == "[[${a.reading}]]" && c.stem == q.stem && c.choices == q.choices) { "I3 $id: ② 지문" }
            }
            GenType.MEANING_JA_KO, GenType.MEANING_KO_JA -> {
                val ja = type == GenType.MEANING_JA_KO
                val am = o.ord0(a.id)
                check(am != null && am.third == "reviewed") { "I4 $id: 정답 뜻이 reviewed 아님" }
                am ?: return
                val aTexts = o.meanings[a.id].orEmpty().map { it.second }.toSet()
                if (ja) check(q.choices[q.answerIndex] == am.second) { "I3 $id: 정답 뜻 불일치" }
                else check(q.stem == am.second && visible(c.choices[q.answerIndex]) == (a.written ?: a.reading)) { "I3 $id: ko-ja 정답/지문 불일치" }
                for ((i, w) in q.why.withIndex()) if (i != q.answerIndex) {
                    val bid = w?.sourceVocabId ?: run { check(false) { "I3 $id: 오답 $i 출처 없음" }; continue }
                    val b = o.vocab[bid] ?: run { check(false) { "I10 $id: 출처 $bid 가 vocab에 없음" }; null }
                    b ?: continue
                    check(b.level == a.level) { "I3 $id: 오답 $bid 급수 ${b.level} != ${a.level}" }
                    check(posGroup(b.pos) == posGroup(a.pos)) { "I3 $id: 오답 $bid 품사군 ${b.pos} != ${a.pos}" }
                    check(bid != a.id && bid !in o.conflicts[a.id].orEmpty() && a.id !in o.conflicts[bid].orEmpty()) { "I3 $id: 오답 $bid 가 충돌 목록" }
                    val bm = o.ord0(bid)
                    check(bm != null && bm.third == "reviewed") { "I4 $id: 오답 $bid 뜻이 reviewed 아님" }
                    if (bm != null) {
                        check(bm.second !in aTexts && o.meanings[bid].orEmpty().none { it.second == am.second }) { "I3 $id: 오답 $bid 뜻이 정답과 같음" }
                        if (ja) check(q.choices[i] == bm.second) { "I3 $id: 오답 $i 뜻 문자열이 $bid 의 뜻이 아님" }
                    }
                    if (!ja) check(visible(c.choices[i]) == (b.written ?: b.reading) && (b.written ?: b.reading) != (a.written ?: a.reading)) { "I3 $id: ko-ja 오답 $i 글자" }
                }
                check(if (ja) visible(c.stem) == (a.written ?: a.reading) else true) { "I3 $id: ja-ko 지문" }
            }
        }
        // I4: 화면에 나오는 한국어 뜻 전부가 reviewed text_ko 그대로
        val shown = mutableListOf<String>()
        when (type) {
            GenType.KANJI_READING, GenType.ORTHOGRAPHY -> {
                val am = o.ord0(a.id)
                val has = c.explanationKo.contains("\n\n뜻: ")
                check(has == (am?.third == "reviewed")) { "I4 $id: 해설 뜻 줄 유무 $has, 뜻 상태 ${am?.third}" }
                check((c.provenance != null) == has) { "I4 $id: provenance ${c.provenance != null} 해설 뜻 $has" }
                if (has) { shown += c.explanationKo.substringAfter("\n\n뜻: "); check(shown.last() == am?.second) { "I4 $id: 해설 뜻이 ord 0과 다름" } }
            }
            GenType.MEANING_JA_KO -> { shown += c.choices; shown += c.explanationKo.substringAfter(": "); check(c.provenance != null) { "I4 $id: provenance 없음" } }
            GenType.MEANING_KO_JA -> { shown += c.stem; shown += c.explanationKo.substringAfter(": "); check(c.provenance != null) { "I4 $id: provenance 없음" } }
        }
        c.whyWrongKo.filterNotNull().filter { it.startsWith("뜻: ") }.forEach { shown += it.removePrefix("뜻: ") }
        for (t in shown) check(t in o.reviewedTexts) { "I4 $id: 화면 뜻 \"$t\" 가 reviewed가 아님" }
        // 이미 reviewed로 확인한 뜻 문자열을 걷어낸 나머지에 미검수 전용 뜻이 부분 문자열로 남아 있으면 안 된다(예: "학생(특히 대학생)" 안의 "대학생"은 reviewed의 일부).
        var screen = (listOf(c.stem, c.explanationKo) + c.choices + c.whyWrongKo.filterNotNull()).joinToString("\n")
        for (t in shown.sortedByDescending { it.length }) screen = screen.replace(t, "")
        for (t in o.nonReviewedOnlyTexts) if (t.length >= 3 && t in screen) { check(false) { "I4 $id: 미검수 뜻 \"$t\" 가 화면 텍스트에 있음" }; break }
        // I7
        if (type == GenType.KANJI_READING) for (d in q.why.filterNotNull()) {
            if (d.kind in listOf(DistractorKind.LONG_VOWEL, DistractorKind.VOICING, DistractorKind.SOKUON, DistractorKind.YOON)) {
                check(variantViolation(d.text) == null) { "I7 $id: ${d.text} ${variantViolation(d.text)}" }
            }
        }
        // I8
        check(c.targets == listOf(q.vocabId)) { "I8 $id: targets ${c.targets}" }
        check(QuizGen.parseId(id) == GenId(type, slot, seed, q.vocabId)) { "I8 $id: parseId" }
        check(c.answerIndex == slot && q.answerIndex == slot) { "I8 $id: answerIndex ${c.answerIndex} != slot $slot" }
        check(c.generated && !c.needsReview && c.section == Section.VOCAB && c.type == type.code && c.level.n == level) { "I8 $id: 메타" }
        // I9
        val marks = listOf("{", "|", "}", "[[", "]]")
        check(marks.none { it in c.explanationKo } && c.whyWrongKo.filterNotNull().all { w -> marks.none { it in w } }) { "I9 $id: 해설·오답 이유에 표시 기호" }
        check(c.whyWrongKo.size == 4 && c.whyWrongKo[c.answerIndex] == null && c.whyWrongKo.withIndex().all { (i, w) -> i == c.answerIndex || !w.isNullOrBlank() }) { "I9 $id: whyWrong 모양" }
        // I10
        val srcs = q.why.mapNotNull { it?.sourceVocabId } + q.vocabId
        check(srcs.all { it.startsWith("jm:") && it !in o.retired && o.vocab[it]?.level == level }) { "I10 $id: av-/retired/다른 급수 $srcs" }
    }

    private fun writeReports(o: Oracle, coverage: List<Pair<Pair<Int, GenType>, Cover>>, skipped: List<String>) {
        val dir = File(System.getProperty("user.dir"), "build/reports/quizgen").apply { mkdirs() }
        File(dir, "coverage.tsv").writeText(
            "level\ttype\tvocab\teligible\tgenerated\tskip_not_target\tskip_unreviewed\tskip_too_few\tgenerated_any_seed\n" +
                coverage.joinToString("") { (k, c) ->
                    "N${k.first}\t${k.second.code}\t${c.vocab}\t${c.eligible}\t${c.generatedAll}\t${c.notTarget}\t${c.unreviewed}\t${c.tooFew}\t${c.generatedAny}\n"
                },
        )
        File(dir, "skipped.tsv").writeText("level\ttype\tvocab_id\treason\n" + skipped.joinToString("\n") { "N" + it } + "\n")
        val n5 = o.vocab.values.filter { it.level == 5 && it.id.startsWith("jm:") }
        println("N5 jm: 어휘 ${n5.size}, 표기 있음 ${n5.count { it.written != null }}, 한자 포함 ${n5.count { it.written?.let(::hasKanji) == true }}")
        println(File(dir, "coverage.tsv").readText())
    }
}
