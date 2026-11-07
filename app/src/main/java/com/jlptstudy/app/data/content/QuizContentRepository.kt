package com.jlptstudy.app.data.content

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.quiz.ExamPart
import com.jlptstudy.quiz.ExamSection
import com.jlptstudy.quiz.ExamTemplate
import com.jlptstudy.quiz.PoolItem
import com.jlptstudy.quiz.QuizQuestion
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// architecture.md 9.2.6: question + listening 행을 한 모양으로.
data class ListeningLine(val speaker: String, val text: String, val ttsText: String?)

data class QuizContent(
    val id: String, val level: JlptLevel, val section: Section, val type: String,
    val stem: String, val passage: String?, val target: String?,
    val lines: List<ListeningLine>?,
    val choices: List<String>, val answerIndex: Int, val explanationKo: String, val whyWrongKo: List<String?>,
    val targets: List<String>, val provenance: MeaningUi, val needsReview: Boolean,
)

interface QuizContentRepository {
    suspend fun pool(level: JlptLevel): List<PoolItem>
    suspend fun contents(ids: List<String>): Map<String, QuizContent>
    /** 행 없으면 null, template_json이 깨졌으면 예외. */
    suspend fun examTemplate(level: JlptLevel): ExamTemplate?
    suspend fun examLevels(): Set<JlptLevel>
    /** 번들에 청해 행이 있는가(설정의 "청해 대본 먼저 보기"를 보일지). */
    suspend fun hasListening(): Boolean = false
}

fun QuizContent.toQuestion() = QuizQuestion(id, section, type, choices, answerIndex)
fun QuizContent.toPoolItem() = PoolItem(id, section, type)

@Serializable private data class LineDto(val speaker: String, val text: String, val ttsText: String? = null)
@Serializable private data class PartDto(val section: String, val type: String, val count: Int)
@Serializable private data class SectionDto(val id: String, val titleKo: String, val minutes: Int, val parts: List<PartDto>)
@Serializable private data class TemplateDto(val sections: List<SectionDto>)

private val json = Json { ignoreUnknownKeys = true }

private inline fun <T> orNull(f: () -> T): T? = try { f() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

/** QuizQuestion 불변식: 선택지 4개, 정답 색인 0..3. 어기거나 JSON이 깨졌으면 null. */
private fun decodeChoices(choicesJson: String, answerIndex: Int): List<String>? =
    orNull { json.decodeFromString<List<String>>(choicesJson) }?.takeIf { it.size == 4 && answerIndex in 0..3 }

private fun build(
    id: String, level: Int, section: String?, type: String, stem: String, passage: String?, target: String?, linesJson: String?,
    choicesJson: String, answerIndex: Int, explanationKo: String, whyWrongJson: String, targetsJson: String,
    koSource: String, koMethod: String, status: String, reviewedAt: String?, reviewer: String?, needsReview: Boolean,
): QuizContent? = orNull {
    val lvl = JlptLevel.fromN(level) ?: return@orNull null
    val sec = if (section == null) Section.LISTENING else Section.of(section)
    val choices = decodeChoices(choicesJson, answerIndex) ?: return@orNull null
    val why = json.decodeFromString<List<String?>>(whyWrongJson)
    val targets = json.decodeFromString<List<String>>(targetsJson)
    val lines = linesJson?.let { l -> json.decodeFromString<List<LineDto>>(l).map { ListeningLine(it.speaker, it.text, it.ttsText) } }
    QuizContent(
        id, lvl, sec, type, stem, passage, target, lines, choices, answerIndex, explanationKo, why, targets,
        MeaningUi(explanationKo.substringBefore("\n\n"), koSource, koMethod, status, reviewedAt, reviewer), needsReview,
    )
}

/** 모르는 section 코드·JSON 깨짐·색인 위반 → null. */
fun QuestionEntity.toQuizContent(): QuizContent? = build(
    id, level, section, type, stem, passage, target, null, choicesJson, answerIndex, explanationKo, whyWrongKoJson,
    targetsJson, koSource, koMethod, koReviewStatus, koReviewedAt, koReviewerKind, needsReview,
)

fun ListeningEntity.toQuizContent(): QuizContent? = build(
    id, level, null, type, question.orEmpty(), null, null, linesJson, choicesJson, answerIndex, explanationKo,
    whyWrongKoJson, targetsJson, koSource, koMethod, koReviewStatus, koReviewedAt, koReviewerKind, needsReview,
)

class RoomQuizContentRepository(private val dao: ContentDao) : QuizContentRepository {
    override suspend fun pool(level: JlptLevel): List<PoolItem> =
        dao.questionPool(level.n).filter { decodeChoices(it.choicesJson, it.answerIndex) != null }
            .mapNotNull { r -> orNull { PoolItem(r.id, Section.of(r.section), r.type) } } +
            dao.listeningPool(level.n).filter { decodeChoices(it.choicesJson, it.answerIndex) != null }
                .map { PoolItem(it.id, Section.LISTENING, it.type) }

    override suspend fun contents(ids: List<String>): Map<String, QuizContent> {
        val retired = chunkedQuery(ids) { dao.retiredAmong(it) }.toSet()
        val live = ids.filter { it !in retired }
        // ID 접두사에 기대지 않고 두 테이블을 모두 읽는다(없는 ID는 어느 쪽에도 안 나온다).
        val rows = chunkedQuery(live) { dao.questionsByIds(it) }.mapNotNull { it.toQuizContent() } +
            chunkedQuery(live) { dao.listeningByIds(it) }.mapNotNull { it.toQuizContent() }
        return rows.associateBy { it.id }
    }

    override suspend fun examTemplate(level: JlptLevel): ExamTemplate? {
        val row = dao.examTemplateRow(level.n) ?: return null
        return json.decodeFromString<TemplateDto>(row.templateJson).let { t ->
            ExamTemplate(level.n, t.sections.map { s ->
                ExamSection(s.id, s.titleKo, s.minutes, s.parts.map { ExamPart(Section.of(it.section), it.type, it.count) })
            })
        }
    }

    override suspend fun examLevels(): Set<JlptLevel> = dao.examTemplateLevels().mapNotNull(JlptLevel::fromN).toSet()
    override suspend fun hasListening(): Boolean = dao.countListening() > 0
}
