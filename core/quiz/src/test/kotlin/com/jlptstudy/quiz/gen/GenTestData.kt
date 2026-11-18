package com.jlptstudy.quiz.gen

// 합성 테스트 데이터(앱 콘텐츠 아님). 실제 DB에서 있을 법한 동음·동형·이표기 사례를 흉내 낸다.

internal fun vocab(id: String, written: String?, reading: String, ko: String?, pos: String = "n", level: Int = 5) =
    QuizVocab(id, written, reading, level, pos, ko)

internal fun kanji(literal: String, on: List<String>, kun: List<String>, level: Int = 5) = QuizKanji(literal, level, on, kun)

/** 양방향 충돌 목록. */
internal fun sym(vararg pairs: Pair<String, String>): Map<String, Set<String>> {
    val m = HashMap<String, MutableSet<String>>()
    for ((a, b) in pairs) { m.getOrPut(a) { HashSet() } += b; m.getOrPut(b) { HashSet() } += a }
    return m
}

/** forms에는 (written, hira(reading)) 자기 쌍을 자동으로 넣는다(파이프라인이 그렇게 만든다). conflicts에는 모든 어휘의 키를 둔다. */
internal fun bank(
    vocab: List<QuizVocab>, kanji: List<QuizKanji> = TestData.kanji, extraForms: Set<Pair<String, String>> = emptySet(),
    conflicts: Map<String, Set<String>> = emptyMap(), ownForms: Boolean = true, level: Int = 5,
): QuizBank {
    val own = if (ownForms) vocab.mapNotNull { v -> v.written?.let { it to Kana.hira(v.reading) } } else emptyList()
    return QuizBank(level, vocab, kanji, own.toSet() + extraForms, vocab.associate { it.id to emptySet<String>() } + conflicts)
}

internal object TestData {
    val kanji = listOf(
        kanji("橋", listOf("キョウ"), listOf("はし")), kanji("箸", listOf("チョ"), listOf("はし")),
        kanji("端", listOf("タン"), listOf("はし", "は", "はた")), kanji("喬", listOf("キョウ"), listOf("たか.い")),
        kanji("会", listOf("カイ", "エ"), listOf("あ.う")), kanji("合", listOf("ゴウ", "ガッ", "カッ"), listOf("あ.う", "あ.わせる")),
        kanji("逢", listOf("ホウ"), listOf("あ.う")), kanji("遇", listOf("グウ"), listOf("あ.う")),
        kanji("今", listOf("コン", "キン"), listOf("いま")), kanji("日", listOf("ニチ", "ジツ"), listOf("ひ", "-か")),
        kanji("金", listOf("キン", "コン"), listOf("かね")), kanji("本", listOf("ホン"), listOf("もと")),
        kanji("学", listOf("ガク"), listOf("まな.ぶ")), kanji("生", listOf("セイ", "ショウ"), listOf("い.きる", "う.まれる")),
        kanji("先", listOf("セン"), listOf("さき")), kanji("校", listOf("コウ"), emptyList()),
        kanji("山", listOf("サン"), listOf("やま")), kanji("川", listOf("セン"), listOf("かわ")),
        kanji("木", listOf("ボク", "モク"), listOf("き")), kanji("月", listOf("ゲツ", "ガツ"), listOf("つき")),
        kanji("言", listOf("ゲン", "ゴン"), listOf("い.う")), kanji("買", listOf("バイ"), listOf("か.う")),
        kanji("使", listOf("シ"), listOf("つか.う")), kanji("書", listOf("ショ"), listOf("か.く")),
        kanji("読", listOf("ドク"), listOf("よ.む")), kanji("行", listOf("コウ", "ギョウ"), listOf("い.く", "ゆ.く")),
        kanji("大", listOf("ダイ", "タイ"), listOf("おお.きい")), kanji("電", listOf("デン"), emptyList()),
        kanji("話", listOf("ワ"), listOf("はな.す")), kanji("時", listOf("ジ"), listOf("とき")),
        kanji("間", listOf("カン", "ケン"), listOf("あいだ")), kanji("友", listOf("ユウ"), listOf("とも")),
        kanji("達", listOf("タツ"), emptyList()), kanji("父", listOf("フ"), listOf("ちち")), kanji("母", listOf("ボ"), listOf("はは")),
        kanji("方", listOf("ホウ"), listOf("かた")),
    )

    private val nouns = listOf(
        "学生" to "がくせい", "先生" to "せんせい", "学校" to "がっこう", "大学" to "だいがく", "時間" to "じかん",
        "電話" to "でんわ", "友達" to "ともだち", "山" to "やま", "川" to "かわ", "木" to "き", "月" to "つき",
        "本" to "ほん", "今月" to "こんげつ", "先月" to "せんげつ", "大木" to "たいぼく", "話" to "はなし",
    )
    private val verbs = listOf("言う" to "いう", "買う" to "かう", "使う" to "つかう", "書く" to "かく", "読む" to "よむ", "行く" to "いく")

    /** 전체 은행. id는 jm:N 순번. */
    val vocab: List<QuizVocab> = buildList {
        var n = 0
        fun add(w: String?, r: String, ko: String?, pos: String = "n") { add(vocab("jm:${++n}", w, r, ko, pos)) }
        add("今日", "きょう", "오늘"); add("今日", "こんにち", "오늘날"); add("日本", "にほん", "일본"); add("日本", "にっぽん", "일본국")
        add("橋", "はし", "다리"); add("箸", "はし", "젓가락"); add("端", "はし", "가장자리")
        add("会う", "あう", "만나다", "v5u"); add("合う", "あう", "맞다", "v5u"); add("逢う", "あう", "상봉하다", "v5u")
        add("父", "ちち", "아버지"); add(null, "パパ", "아빠"); add("母", "はは", "어머니"); add("お母さん", "おかあさん", "어머님")
        add("方", "かた", "방향"); add("方", "ほう", "쪽")
        nouns.forEachIndexed { i, (w, r) -> add(w, r, "명사뜻$i") }
        verbs.forEachIndexed { i, (w, r) -> add(w, r, "동사뜻$i", "v5u") }
        add("美しい", "うつくしい", "아름답다", "adj-i"); add("静か", "しずか", "조용하다", "adj-na")
        add("新しい", "あたらしい", "새롭다", "adj-i"); add("古い", "ふるい", "오래되다", "adj-i"); add("大きい", "おおきい", "크다", "adj-i")
        add("小さい", "ちいさい", "작다", "adj-i"); add("暑い", "あつい", "덥다", "adj-i")
        add("未검수", "みけんしゅう", null)
        add("あれ", "あれ", "저것", "pn"); add("それ", "それ", "그것", "pn"); add("これ", "これ", "이것", "pn"); add("ぬ", "ぬ", "ぬ-뜻")
        add("テレビ", "てれび", "텔레비전")
    }
    fun id(written: String?, reading: String): String = vocab.first { it.written == written && it.reading == reading }.id

    val conflicts = sym(
        id("父", "ちち") to id(null, "パパ"), id("母", "はは") to id("お母さん", "おかあさん"),
        id("今日", "きょう") to id("今日", "こんにち"),
    )
    val bank: QuizBank get() = bank(vocab, conflicts = conflicts)
}
