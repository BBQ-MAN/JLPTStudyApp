package com.jlptstudy.quiz.gen

/** quizgen.md 3.2: 가타카나 U+30A1–U+30F6 → 히라가나(−0x60). 그 밖(ー 포함)은 그대로. 파이프라인 pl_common.hira와 같은 정의. */
object Kana {
    fun hira(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in 'ァ'..'ヶ') (c.code - 0x60).toChar() else c)
        return sb.toString()
    }
}

/** quizgen.md 5.4 */
fun posGroup(pos: String): String {
    val f = pos.substringBefore(',').trim()
    return when {
        f.startsWith("v1") || f.startsWith("v2") || f.startsWith("v4") || f.startsWith("v5") || f.startsWith("vk") ||
            f.startsWith("vs-") || f.startsWith("vz") || f.startsWith("vn") || f.startsWith("vr") -> "verb"
        f == "adj-i" || f == "adj-ix" -> "adj-i"
        f == "adj-na" -> "adj-na"
        f == "adv" || f == "adv-to" -> "adv"
        f.startsWith("n") || f.startsWith("pn") -> "noun"   // "n", "n-adv", "n-suf" …
        else -> f
    }
}

/** quizgen.md 4.6: 읽기(히라가나)를 한 곳만 바꾼 소리 변형. 결과는 중복 제거·원래 읽기 제외·문자열 정렬. */
object KanaVariants {
    private const val SMALL = "ぁぃぅぇぉっゃゅょゎ"
    private const val SMALL_Y = "ゃゅょ"
    private const val I_ROW = "きぎしじちぢにひびぴみり"
    private const val UNVOICED = "かきくけこさしすせそたちつてとぱぴぷぺぽ"
    private val VOICE_GROUPS = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ", "ただ", "ちぢ", "つづ", "てで", "とど",
        "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
    )
    private val VOICE: Map<Char, List<Char>> = VOICE_GROUPS.flatMap { g -> g.map { c -> c to g.filter { it != c }.toList() } }.toMap()
    private val VOWEL: Map<Char, Char> = mapOf(
        'a' to "あかがさざただなはばぱまやゃらわゎぁ", 'i' to "いきぎしじちぢにひびぴみりぃ", 'u' to "うくぐすずつづぬふぶぷむゆゅるぅゔ",
        'e' to "えけげせぜてでねへべぺめれぇ", 'o' to "おこごそぞとどのほぼぽもよょろをぉ",
    ).flatMap { (v, cs) -> cs.map { it to v } }.toMap()
    private val BIG = mapOf('ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ')
    private val SMALLER = BIG.entries.associate { (s, b) -> b to s }

    private fun vowel(c: Char?): Char? = c?.let { VOWEL[it] }

    /** 형식 규칙(어기면 버림). */
    private fun valid(s: String): Boolean {
        if (s.isEmpty() || s.first() in "っー$SMALL" || s.last() == 'っ') return false
        for (i in s.indices) {
            val c = s[i]
            if (c == 'っ' && s[i + 1] in "あいうえおんっー$SMALL") return false   // i+1: 마지막이 っ인 경우는 위에서 걸러졌다
            if (c in SMALL_Y && (i == 0 || s[i - 1] !in I_ROW)) return false
        }
        return true
    }

    fun of(reading: String): List<Pair<String, DistractorKind>> {
        val r = reading
        val out = LinkedHashMap<String, DistractorKind>()
        fun add(s: String, k: DistractorKind) { if (s != r && valid(s)) out.putIfAbsent(s, k) }
        fun replace(i: Int, c: Char) = r.substring(0, i) + c + r.substring(i + 1)
        fun insert(i: Int, c: Char) = r.substring(0, i) + c + r.substring(i)
        fun delete(i: Int) = r.substring(0, i) + r.substring(i + 1)

        for (i in r.indices) VOICE[r[i]]?.forEach { add(replace(i, it), DistractorKind.VOICING) }

        for (i in r.indices) {
            if (r[i] == 'っ') { add(delete(i), DistractorKind.SOKUON); add(replace(i, 'つ'), DistractorKind.SOKUON); add(replace(i, 'く'), DistractorKind.SOKUON) }
            if (i >= 1 && r[i] in UNVOICED && r[i - 1] !in "っんー$SMALL") add(insert(i, 'っ'), DistractorKind.SOKUON)
            if (i >= 1 && i + 1 < r.length && r[i] in "くきつち" && r[i + 1] in UNVOICED) add(replace(i, 'っ'), DistractorKind.SOKUON)
        }

        for (i in r.indices) {
            val c = r[i]; val prev = r.getOrNull(i - 1)
            val drop = when (c) {
                'ー' -> true
                'う' -> vowel(prev) in setOf('o', 'u')
                'い' -> vowel(prev) in setOf('e', 'i')
                'あ', 'え', 'お' -> vowel(prev) == vowel(c)
                else -> false
            }
            if (drop) add(delete(i), DistractorKind.LONG_VOWEL)
            if (i + 1 < r.length) {   // 끝 뒤에는 넣지 않는다. ん·っ·ー는 모음이 없어 vowel == null.
                val ins = when (vowel(c)) { 'o', 'u' -> 'う'; 'e' -> 'い'; else -> null }
                if (ins != null && r[i + 1] != ins && r[i + 1] !in SMALL) add(insert(i + 1, ins), DistractorKind.LONG_VOWEL)
            }
        }

        for (i in r.indices) {
            val c = r[i]
            if (c in SMALL_Y) {
                add(replace(i, BIG.getValue(c)), DistractorKind.YOON)
                SMALL_Y.filter { it != c }.forEach { add(replace(i, it), DistractorKind.YOON) }
                add(delete(i), DistractorKind.YOON)
            }
            if (c in SMALLER && i >= 1 && r[i - 1] in I_ROW) add(replace(i, SMALLER.getValue(c)), DistractorKind.YOON)
        }
        return out.entries.map { it.key to it.value }.sortedBy { it.first }
    }
}
