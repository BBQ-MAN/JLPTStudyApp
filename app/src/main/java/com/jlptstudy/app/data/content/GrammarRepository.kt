package com.jlptstudy.app.data.content

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.ui.review.MeaningUi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// architecture.md 9.1 `:app` 계약.
data class GrammarRow(val id: String, val level: JlptLevel, val title: String, val meaningKo: String)
data class ConnectionUi(val posKo: String, val form: List<RubySegment>, val example: List<RubySegment>)
data class ExampleUi(
    val ja: List<RubySegment>, val ko: String, val sourceId: String,
    val sourceRef: String? = null, val author: String? = null, val license: String? = null, // 출처 줄(ui-design 5.11)
)
data class ContrastUi(val grammarId: String, val title: String, val mine: ExampleUi, val other: ExampleUi) // pair[0], pair[1]
data class GrammarDetailUi(
    val id: String, val level: JlptLevel, val title: String, val meaningKo: String, val explanationKo: String,
    val connections: List<ConnectionUi>, val examples: List<ExampleUi>, val contrasts: List<ContrastUi>,
    val provenance: MeaningUi, val needsReview: Boolean,
)

interface GrammarRepository {
    /** level desc, sort_key. 빈 집합 = 번들 급수 전부. JSON 열이 깨진 행은 뺀다. */
    suspend fun list(levels: Set<JlptLevel>): List<GrammarRow>
    /** 문법이 있는 급수(칩으로 보일 급수). */
    suspend fun levels(): Set<JlptLevel>
    /** 없음·retired·JSON 깨짐 → null. contrasts의 grammarId가 번들에 없으면 그 항목만 뺀다. */
    suspend fun detail(id: String): GrammarDetailUi?
}

@Serializable private data class ConnectionDto(val posKo: String, val form: String, val example: String)
@Serializable private data class PairDto(val ja: String, val ko: String)
@Serializable private data class ContrastDto(val grammarId: String, val pair: List<PairDto>)

/** [RoomContentRepository]가 위임해 같은 DB 연결을 쓴다. */
class RoomGrammarRepository(private val dao: ContentDao) : GrammarRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private fun connections(e: GrammarEntity): List<ConnectionDto>? = decode(e.connectionsJson)
    private fun contrasts(e: GrammarEntity): List<ContrastDto>? =
        decode<ContrastDto>(e.contrastsJson)?.takeIf { l -> l.all { it.pair.size == 2 } }

    private inline fun <reified T> decode(raw: String): List<T>? = try {
        json.decodeFromString<List<T>>(raw)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { null } // SerializationException·IllegalArgumentException

    override suspend fun list(levels: Set<JlptLevel>): List<GrammarRow> =
        dao.grammarRows(levels.ifEmpty { JlptLevel.entries.toSet() }.map { it.n }).mapNotNull { e ->
            val level = JlptLevel.fromN(e.level) ?: return@mapNotNull null
            if (connections(e) == null || contrasts(e) == null) null else GrammarRow(e.id, level, e.title, e.meaningKo)
        }

    override suspend fun levels(): Set<JlptLevel> = dao.grammarLevels().mapNotNull(JlptLevel::fromN).toSet()

    override suspend fun detail(id: String): GrammarDetailUi? {
        val e = dao.grammarById(id) ?: return null
        val level = JlptLevel.fromN(e.level) ?: return null
        val conns = connections(e) ?: return null
        val contrastDtos = contrasts(e) ?: return null
        val titles = chunkedQuery(contrastDtos.map { it.grammarId }) { dao.grammarTitles(it) }.associate { it.id to it.title }
        fun ex(d: PairDto) = ExampleUi(Furigana.parse(d.ja), d.ko, e.sourceId)
        return GrammarDetailUi(
            id = e.id, level = level, title = e.title, meaningKo = e.meaningKo, explanationKo = e.explanationKo,
            connections = conns.map { ConnectionUi(it.posKo, Furigana.parse(it.form), Furigana.parse(it.example)) },
            examples = dao.grammarExamples(id).map { ExampleUi(Furigana.parse(it.ja), it.ko, it.sourceId, it.sourceRef, it.author, it.license) },
            contrasts = contrastDtos.mapNotNull { c ->
                titles[c.grammarId]?.let { ContrastUi(c.grammarId, it, ex(c.pair[0]), ex(c.pair[1])) }
            },
            provenance = MeaningUi(e.explanationKo.substringBefore("\n\n"), e.koSource, e.koMethod, e.koReviewStatus, e.koReviewedAt, e.koReviewerKind),
            needsReview = e.needsReview,
        )
    }
}

/** 문법 조회를 구현하지 않은 콘텐츠 저장소(시험용 가짜)를 위한 빈 문법 = 0행. */
object NoGrammarRepository : GrammarRepository {
    override suspend fun list(levels: Set<JlptLevel>) = emptyList<GrammarRow>()
    override suspend fun levels() = emptySet<JlptLevel>()
    override suspend fun detail(id: String): GrammarDetailUi? = null
}
