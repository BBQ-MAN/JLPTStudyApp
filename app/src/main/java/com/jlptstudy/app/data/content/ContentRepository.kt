package com.jlptstudy.app.data.content

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.ui.dictionary.BrowseItem
import com.jlptstudy.app.ui.dictionary.BrowseTab
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.review.ReviewCardUi
import com.jlptstudy.app.ui.review.VocabDirection
import com.jlptstudy.app.ui.review.WordRef
import androidx.core.graphics.PathParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** minSdk 26 SQLite의 변수 한도(999)보다 작게 나눈다(architecture.md 4.5). */
const val MAX_SQL_VARIABLES = 900

internal suspend fun <T, R> chunkedQuery(ids: Collection<T>, query: suspend (List<T>) -> List<R>): List<R> =
    ids.distinct().chunked(MAX_SQL_VARIABLES).flatMap { query(it) }

data class ContentMeta(val kind: String, val contentVersion: String, val levels: Set<JlptLevel>)
data class LevelCounts(val words: Int, val kanji: Int)
data class NewCardCandidate(val cardId: String, val level: JlptLevel)

data class VocabDetail(
    val written: String?, val reading: String, val ruby: List<RubySegment>, val level: JlptLevel,
    val pos: List<String>, val meanings: List<MeaningUi>, val kanji: List<String>,
)

data class KanjiDetail(
    val literal: String, val level: JlptLevel, val strokeCount: Int, val on: List<String>, val kun: List<String>,
    val koreanReadings: List<String>, val words: List<WordRef>, val strokes: List<String>?,
)

/** S09 집계 줄(ui-design 8절). 번들된 한국어 뜻 전체 기준. */
data class ReviewCounts(val total: Int, val human: Int, val agent: Int, val sampled: Int, val unreviewed: Int, val reviewerUnrecorded: Int)

interface ContentRepository {
    suspend fun meta(): ContentMeta
    suspend fun levelCounts(): Map<JlptLevel, LevelCounts>
    /** 도입 순서(level desc, sort_key, kind vr→vp→kr→gr, id). retired는 뺀다. */
    suspend fun newCardOrder(levels: Set<JlptLevel>, kinds: Set<CardKind>): List<NewCardCandidate>
    /** 900개씩 조회. 없는·retired ID는 결과에서 빠진다. */
    suspend fun cardContents(cardIds: List<String>): Map<String, ReviewCardUi>
    suspend fun cardLevels(cardIds: List<String>): Map<String, JlptLevel>
    /** 빈 levels = 번들된 급수 전부. query가 비어 있지 않으면 표기·읽기·한국어 뜻(한자는 글자·읽기) 부분 일치. retired는 뺀다. */
    suspend fun browse(tab: BrowseTab, levels: Set<JlptLevel>, query: String): List<BrowseItem>
    /** 없거나 retired면 null. */
    suspend fun vocabDetail(id: String): VocabDetail?
    suspend fun kanjiDetail(literal: String): KanjiDetail?
    /** 한국어 뜻 행(retired 제외)을 검수 상태별로 센다. */
    suspend fun meaningReviewCounts(): ReviewCounts
}

class RoomContentRepository private constructor(private val dao: ContentDao, val quizGen: QuizGenRepository) :
    ContentRepository, GrammarRepository by RoomGrammarRepository(dao), QuizContentRepository by RoomQuizContentRepository(dao, quizGen) {
    constructor(dao: ContentDao) : this(dao, RoomQuizGenRepository(dao))

    private val json = Json

    override suspend fun meta(): ContentMeta {
        val m = dao.meta().associate { it.key to it.value }
        val levels = m["levels"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.mapNotNull(JlptLevel::fromN)
        return ContentMeta(m["kind"].orEmpty(), m["content_version"].orEmpty(), levels.toSet())
    }

    override suspend fun levelCounts(): Map<JlptLevel, LevelCounts> {
        val words = dao.vocabCounts().associate { it.level to it.n }
        val kanji = dao.kanjiCounts().associate { it.level to it.n }
        return JlptLevel.entries.associateWith { LevelCounts(words[it.n] ?: 0, kanji[it.n] ?: 0) }
    }

    override suspend fun newCardOrder(levels: Set<JlptLevel>, kinds: Set<CardKind>): List<NewCardCandidate> {
        if (levels.isEmpty()) return emptyList()
        val ns = levels.map { it.n }
        data class Row(val kind: CardKind, val id: String, val level: Int, val sortKey: Int)
        val rows = buildList {
            val vocab = if (CardKind.VR in kinds || CardKind.VP in kinds) dao.vocabOrder(ns) else emptyList()
            for (k in listOf(CardKind.VR, CardKind.VP)) if (k in kinds) vocab.forEach { add(Row(k, it.id, it.level, it.sortKey)) }
            if (CardKind.KR in kinds) dao.kanjiOrder(ns).forEach { add(Row(CardKind.KR, it.id, it.level, it.sortKey)) }
            if (CardKind.GR in kinds) dao.grammarOrder(ns).forEach { add(Row(CardKind.GR, it.id, it.level, it.sortKey)) }
        }
        return rows
            .sortedWith(compareByDescending<Row> { it.level }.thenBy { it.sortKey }.thenBy { it.kind.ordinal }.thenBy { it.id })
            .mapNotNull { r -> JlptLevel.fromN(r.level)?.let { NewCardCandidate("${r.kind.code}:${r.id}", it) } }
    }

    override suspend fun cardLevels(cardIds: List<String>): Map<String, JlptLevel> {
        val parsed = parse(cardIds)
        val out = HashMap<String, JlptLevel>()
        val vocabIds = parsed.filter { it.kind == CardKind.VR || it.kind == CardKind.VP }.map { it.contentId }
        val kanjiIds = parsed.filter { it.kind == CardKind.KR }.map { it.contentId }
        val grammarIds = parsed.filter { it.kind == CardKind.GR }.map { it.contentId }
        val retired = retired(vocabIds + kanjiIds + grammarIds)
        val levelOf = HashMap<String, JlptLevel>()
        chunkedQuery(grammarIds) { dao.grammarLevelsOf(it) }.forEach { r -> JlptLevel.fromN(r.level)?.let { levelOf["g:" + r.id] = it } }
        chunkedQuery(vocabIds) { dao.vocabLevels(it) }.forEach { r -> JlptLevel.fromN(r.level)?.let { levelOf["v:" + r.id] = it } }
        chunkedQuery(kanjiIds) { dao.kanjiLevels(it) }.forEach { r -> JlptLevel.fromN(r.level)?.let { levelOf["k:" + r.id] = it } }
        for (p in parsed) {
            if (p.contentId in retired) continue
            val key = (if (p.kind == CardKind.KR) "k:" else if (p.kind == CardKind.GR) "g:" else "v:") + p.contentId
            levelOf[key]?.let { out[p.cardId] = it }
        }
        return out
    }

    override suspend fun cardContents(cardIds: List<String>): Map<String, ReviewCardUi> {
        val parsed = parse(cardIds)
        val vocabIds = parsed.filter { it.kind == CardKind.VR || it.kind == CardKind.VP }.map { it.contentId }
        val kanjiIds = parsed.filter { it.kind == CardKind.KR }.map { it.contentId }
        val grammarIds = parsed.filter { it.kind == CardKind.GR }.map { it.contentId }
        val retired = retired(vocabIds + kanjiIds + grammarIds)
        val grammar = chunkedQuery(grammarIds) { dao.grammarByIds(it) }.associateBy { it.id }
        val grammarEx = chunkedQuery(grammarIds) { dao.grammarFirstExamples(it) }.associateBy { it.grammarId }
        val vocab = chunkedQuery(vocabIds) { dao.vocabByIds(it) }.associateBy { it.id }
        val meanings = chunkedQuery(vocabIds) { dao.meaningsByIds(it) }.groupBy { it.vocabId }
        val kanji = chunkedQuery(kanjiIds) { dao.kanjiByIds(it) }.associateBy { it.literal }
        val words = chunkedQuery(kanjiIds) { dao.kanjiWords(it) }.groupBy { it.kanji }

        val out = LinkedHashMap<String, ReviewCardUi>()
        for (p in parsed) {
            if (p.contentId in retired) continue
            val card: ReviewCardUi = when (p.kind) {
                CardKind.VR, CardKind.VP -> {
                    val v = vocab[p.contentId] ?: continue
                    val level = JlptLevel.fromN(v.level) ?: continue
                    ReviewCardUi.Vocab(
                        cardId = p.cardId,
                        direction = if (p.kind == CardKind.VR) VocabDirection.JA_TO_KO else VocabDirection.KO_TO_JA,
                        level = level, written = v.written, reading = v.reading,
                        ruby = Furigana.fromHeadword(v.written, v.reading),
                        pos = v.pos.split(',').filter { it.isNotBlank() },
                        meanings = meanings[v.id].orEmpty().map { it.toUi() },
                    )
                }
                CardKind.KR -> {
                    val k = kanji[p.contentId] ?: continue
                    val level = JlptLevel.fromN(k.level) ?: continue
                    ReviewCardUi.Kanji(
                        cardId = p.cardId, level = level, literal = k.literal, strokeCount = strokePathCount(k.literal) ?: k.strokeCount,
                        on = strings(k.onReadings), kun = strings(k.kunReadings), koreanReadings = strings(k.koreanReadings),
                        words = words[k.literal].orEmpty().take(3).map { WordRef(it.vocabId, it.written, it.reading, it.textKo.orEmpty()) },
                    )
                }
                CardKind.GR -> {
                    val g = grammar[p.contentId] ?: continue
                    val ex = grammarEx[g.id] ?: continue // 예문 0행이면 앞면을 그릴 수 없다
                    val level = JlptLevel.fromN(g.level) ?: continue
                    ReviewCardUi.Grammar(
                        cardId = p.cardId, level = level, title = g.title, example = Furigana.parse(ex.ja), exampleKo = ex.ko,
                        meaningKo = g.meaningKo, summaryKo = g.explanationKo.substringBefore("\n\n"),
                        provenance = MeaningUi(g.explanationKo.substringBefore("\n\n"), g.koSource, g.koMethod, g.koReviewStatus, g.koReviewedAt, g.koReviewerKind),
                        needsReview = g.needsReview,
                    )
                }
            }
            out[p.cardId] = card
        }
        return out
    }

    override suspend fun browse(tab: BrowseTab, levels: Set<JlptLevel>, query: String): List<BrowseItem> {
        val ns = levels.ifEmpty { JlptLevel.entries.toSet() }.map { it.n }
        val q = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return when (tab) {
            BrowseTab.VOCAB -> dao.browseVocab(ns, q).mapNotNull { r ->
                JlptLevel.fromN(r.level)?.let { BrowseItem.Word(r.id, r.written, r.reading, r.textKo, it) }
            }
            BrowseTab.KANJI -> dao.browseKanji(ns, q).mapNotNull { r ->
                JlptLevel.fromN(r.level)?.let { BrowseItem.Kanji(r.literal, strings(r.koreanReadings).firstOrNull(), it) }
            }
        }
    }

    override suspend fun vocabDetail(id: String): VocabDetail? {
        if (retired(listOf(id)).isNotEmpty()) return null
        val v = dao.vocabByIds(listOf(id)).firstOrNull() ?: return null
        val level = JlptLevel.fromN(v.level) ?: return null
        return VocabDetail(
            written = v.written, reading = v.reading, ruby = Furigana.fromHeadword(v.written, v.reading), level = level,
            pos = v.pos.split(',').filter { it.isNotBlank() },
            meanings = dao.meaningsByIds(listOf(id)).map { it.toUi() },
            kanji = dao.vocabKanji(id).sortedBy { v.written?.indexOf(it) ?: 0 },
        )
    }

    override suspend fun kanjiDetail(literal: String): KanjiDetail? {
        if (retired(listOf(literal)).isNotEmpty()) return null
        val k = dao.kanjiByIds(listOf(literal)).firstOrNull() ?: return null
        val level = JlptLevel.fromN(k.level) ?: return null
        val strokes = strokes(literal)
        return KanjiDetail(
            // 사전 획수와 필순 그림의 획 수가 다른 한자(飴 13/14, 噌 14/15)는 화면의 '획수'를 그림의 획 수(paths 개수)에 맞춘다(U-9)
            literal = k.literal, level = level, strokeCount = strokes?.size ?: k.strokeCount,
            on = strings(k.onReadings), kun = strings(k.kunReadings), koreanReadings = strings(k.koreanReadings),
            words = dao.kanjiWords(listOf(literal)).map { WordRef(it.vocabId, it.written, it.reading, it.textKo.orEmpty()) },
            strokes = strokes,
        )
    }

    /** 카드용: 해석 없이 paths 개수만 센다(세션 대기열 전체에 부르므로). 행 없음·JSON 깨짐·빈 목록이면 null. */
    private suspend fun strokePathCount(literal: String): Int? =
        dao.strokePaths(literal)?.let { runCatching { strings(it).size }.getOrNull() }?.takeIf { it > 0 }

    /** 행 없음·JSON 깨짐·경로 하나라도 해석 실패면 null(절 숨김, 일부 획만 그린 필순은 틀린 정보다). */
    private suspend fun strokes(literal: String): List<String>? {
        val raw = dao.strokePaths(literal) ?: return null
        return try {
            strings(raw).takeIf { it.isNotEmpty() }?.also { l -> l.forEach { PathParser.createPathFromPathData(it) } }
        } catch (e: Exception) { // SerializationException, 경로 해석의 RuntimeException
            android.util.Log.w("Content", "필순 해석 실패: $literal", e); null
        }
    }

    override suspend fun meaningReviewCounts(): ReviewCounts {
        val rows = dao.meaningReviewRows()
        fun n(f: (ReviewCountRow) -> Boolean) = rows.filter(f).sumOf { it.n }
        val reviewed = { r: ReviewCountRow -> r.status == "reviewed" }
        return ReviewCounts(
            total = rows.sumOf { it.n },
            human = n { reviewed(it) && it.kind == "human" },
            agent = n { reviewed(it) && it.kind == "agent" },
            sampled = n { it.status == "sampled" },
            unreviewed = n { it.status == "unreviewed" },
            reviewerUnrecorded = n { reviewed(it) && it.kind != "human" && it.kind != "agent" },
        )
    }

    private fun VocabMeaningEntity.toUi() = MeaningUi(textKo, koSource, koMethod, koReviewStatus, koReviewedAt, koReviewerKind)

    private suspend fun retired(ids: List<String>): Set<String> = chunkedQuery(ids) { dao.retiredAmong(it) }.toSet()

    private fun strings(raw: String): List<String> = json.decodeFromString(ListSerializer(String.serializer()), raw)

    private data class Parsed(val cardId: String, val kind: CardKind, val contentId: String)

    private fun parse(cardIds: List<String>): List<Parsed> = cardIds.distinct().mapNotNull { id ->
        val kind = CardKind.fromCode(id.substringBefore(':', "")) ?: return@mapNotNull null
        Parsed(id, kind, id.substringAfter(':'))
    }
}
