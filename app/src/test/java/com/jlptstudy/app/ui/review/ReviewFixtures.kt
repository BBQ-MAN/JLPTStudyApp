package com.jlptstudy.app.ui.review

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentMeta
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.KanjiDetail
import com.jlptstudy.app.data.content.LevelCounts
import com.jlptstudy.app.data.content.VocabDetail
import com.jlptstudy.app.ui.dictionary.BrowseItem
import com.jlptstudy.app.ui.dictionary.BrowseTab
import com.jlptstudy.app.data.content.NewCardCandidate
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.data.study.UndoResult
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.srs.CardState
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Phase
import com.jlptstudy.srs.ScheduleResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 시험용 시계. 직접 옮긴다. */
class MutableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

// 시험 픽스처(앱 콘텐츠가 아니다): 화면·ViewModel 동작을 확인하기 위한 가짜 카드.
fun meaning(text: String, source: String = "authored", method: String = "human", status: String = "reviewed", reviewer: String? = "human") =
    MeaningUi(text, source, method, status, "2026-11-01", reviewer)

fun vocab(
    id: String, written: String?, reading: String, meanings: List<MeaningUi> = listOf(meaning("뜻 $id")),
    level: JlptLevel = JlptLevel.N5, direction: VocabDirection = VocabDirection.JA_TO_KO, pos: List<String> = listOf("n"),
) = ReviewCardUi.Vocab(
    cardId = (if (direction == VocabDirection.JA_TO_KO) "vr:" else "vp:") + id, direction = direction, level = level,
    written = written, reading = reading, ruby = Furigana.fromHeadword(written, reading), pos = pos, meanings = meanings,
)

fun kanji(literal: String, level: JlptLevel = JlptLevel.N5) = ReviewCardUi.Kanji(
    cardId = "kr:$literal", level = level, literal = literal, strokeCount = 9, on = listOf("チョク"), kun = listOf("ただ(ちに)"),
    koreanReadings = listOf("직"), words = listOf(WordRef("v1", "直接", "ちょくせつ", "직접")),
)

fun grammarCard(
    id: String, level: JlptLevel = JlptLevel.N5, title: String = "〜てください", example: String = "これを[[{見|み}てください]]。",
    exampleKo: String = "시험 예문 뜻", meaningKo: String = "시험 대응 표현", summaryKo: String = "시험 요약 첫 문단", needsReview: Boolean = false,
) = ReviewCardUi.Grammar(
    cardId = "gr:$id", level = level, title = title, example = Furigana.parse(example), exampleKo = exampleKo, meaningKo = meaningKo,
    summaryKo = summaryKo, provenance = meaning(summaryKo, "authored", "llm:test-model@2026-11-03", "unreviewed", null), needsReview = needsReview,
)

class FakeContent(
    cards: List<ReviewCardUi>, val retired: MutableSet<String> = mutableSetOf(),
    private val metaLevels: Set<JlptLevel> = JlptLevel.entries.toSet(), private val kind: String = "sample",
) : ContentRepository {
    val all = cards
    private fun alive() = all.filter { it.cardId !in retired }
    private fun levelOf(c: ReviewCardUi) = when (c) { is ReviewCardUi.Vocab -> c.level; is ReviewCardUi.Kanji -> c.level; is ReviewCardUi.Grammar -> c.level }
    override suspend fun meta() = ContentMeta(kind, "test", metaLevels)
    override suspend fun levelCounts(): Map<JlptLevel, LevelCounts> = JlptLevel.entries.associateWith { l ->
        LevelCounts(all.count { it is ReviewCardUi.Vocab && it.level == l }, all.count { it is ReviewCardUi.Kanji && it.level == l })
    }
    override suspend fun newCardOrder(levels: Set<JlptLevel>, kinds: Set<CardKind>) =
        alive().filter { levelOf(it) in levels && CardKind.fromCode(it.cardId.substringBefore(':')) in kinds }
            .map { NewCardCandidate(it.cardId, levelOf(it)) }
    override suspend fun cardContents(cardIds: List<String>) = alive().filter { it.cardId in cardIds }.associateBy { it.cardId }
    override suspend fun cardLevels(cardIds: List<String>) = alive().filter { it.cardId in cardIds }.associate { it.cardId to levelOf(it) }

    var failBrowse: Throwable? = null
    override suspend fun browse(tab: BrowseTab, levels: Set<JlptLevel>, query: String): List<BrowseItem> {
        failBrowse?.let { throw it }
        val ls = levels.ifEmpty { JlptLevel.entries.toSet() }
        val q = query.trim()
        return when (tab) {
            BrowseTab.VOCAB -> alive().filterIsInstance<ReviewCardUi.Vocab>().filter { it.direction == VocabDirection.JA_TO_KO && it.level in ls }
                .filter { c -> q.isEmpty() || c.written.orEmpty().contains(q) || c.reading.contains(q) || c.meanings.any { it.textKo.contains(q) } }
                .map { BrowseItem.Word(it.cardId.substringAfter(':'), it.written, it.reading, it.meanings.firstOrNull()?.textKo, it.level) }
            BrowseTab.KANJI -> alive().filterIsInstance<ReviewCardUi.Kanji>().filter { it.level in ls }
                .filter { c -> q.isEmpty() || c.literal.contains(q) || c.koreanReadings.any { it.contains(q) } }
                .map { BrowseItem.Kanji(it.literal, it.koreanReadings.firstOrNull(), it.level) }
        }
    }
    override suspend fun vocabDetail(id: String): VocabDetail? =
        alive().filterIsInstance<ReviewCardUi.Vocab>().firstOrNull { it.cardId == "vr:$id" }?.let {
            VocabDetail(it.written, it.reading, it.ruby, it.level, it.pos, it.meanings, it.written.orEmpty().filter { ch -> ch.code > 0x4E00 }.map(Char::toString))
        }
    override suspend fun kanjiDetail(literal: String): KanjiDetail? =
        alive().filterIsInstance<ReviewCardUi.Kanji>().firstOrNull { it.literal == literal }?.let {
            KanjiDetail(it.literal, it.level, it.strokeCount, it.on, it.kun, it.koreanReadings, it.words, null)
        }
    override suspend fun meaningReviewCounts() = com.jlptstudy.app.data.content.ReviewCounts(0, 0, 0, 0, 0, 0)
}

/** 메모리 학습 기록. applyReview 실패·지연을 시험이 끼워 넣을 수 있다. */
class MemStudy(settings: UserSettings = UserSettings(newCardsPerDay = 5, onboardingDone = true)) : StudyRepository {
    class Log(val id: Long, val result: ScheduleResult, var undone: Boolean = false)

    val settingsFlow = MutableStateFlow(settings)
    val states = LinkedHashMap<String, CardState>()
    val logs = mutableListOf<Log>()
    var failApply: Throwable? = null
    var applyGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var statesOfKindsCalls = 0

    override suspend fun applyReview(result: ScheduleResult) {
        applyGate?.await()
        failApply?.let { failApply = null; throw it }
        states[result.state.cardId] = result.state
        logs += Log(logs.size + 1L, result)
    }
    override suspend fun maxLogId() = logs.size.toLong()
    override suspend fun undoLast(afterLogId: Long): UndoResult? {
        val log = logs.lastOrNull { it.id > afterLogId && !it.undone } ?: return null
        val before = log.result.record.before
        if (before.phase == Phase.NEW) states.remove(log.result.state.cardId) else states[before.cardId] = before
        log.undone = true
        return UndoResult(before.cardId, before.takeIf { it.phase != Phase.NEW })
    }
    override suspend fun startedCardIds() = states.keys.toSet()
    override suspend fun states(cardIds: Collection<String>) = states.filterKeys { it in cardIds }
    override suspend fun statesOfKinds(kinds: Set<CardKind>): List<CardState> {
        statesOfKindsCalls++
        return states.values.filter { CardKind.fromCode(it.cardId.substringBefore(':')) in kinds }
    }
    override suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long) =
        logs.filter { !it.undone && it.result.record.phaseBefore == Phase.NEW && it.result.record.reviewedAtEpochMs in fromEpochMs until toEpochMs }
            .map { it.result.record.cardId }
    override fun settings(): Flow<UserSettings> = settingsFlow
    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) { settingsFlow.value = transform(settingsFlow.value) }
}

/** 복습 단계 카드(시험용 직접 구성). */
fun reviewState(cardId: String, dueAt: Instant, lastReview: Instant = dueAt.minus(Duration.ofDays(5))) = CardState(
    cardId = cardId, phase = Phase.REVIEW, step = null, stability = 5.0, difficulty = 5.0,
    dueAtEpochMs = dueAt.toEpochMilli(), lastReviewAtEpochMs = lastReview.toEpochMilli(), reps = 3, lapses = 0,
    schedulerVersion = Fsrs6Scheduler().version,
)
