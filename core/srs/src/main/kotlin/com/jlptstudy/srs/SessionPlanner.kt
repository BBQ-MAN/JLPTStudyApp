package com.jlptstudy.srs

import java.time.Duration
import java.time.Instant

object SessionPlanner {
    /** 카드 ID `"<종류>:<콘텐츠ID>"`(architecture.md 4.1)에서 콘텐츠 ID. */
    private fun contentIdOf(cardId: String) = cardId.substringAfter(':')

    /**
     * 도입 순서대로 놓인 새 카드 후보에서 오늘 도입할 카드를 고른다.
     * 뺀다: startedCardIds에 있는 카드, 콘텐츠 ID가 introducedTodayCardIds의 콘텐츠 ID와 같은 카드,
     * 같은 콘텐츠 ID의 두 번째 이후 카드(같은 학습일에 한 콘텐츠는 한 종류만 — 후보 순서상 vr이 vp보다 앞).
     * 개수 = max(0, effectiveNewCardsPerDay − introducedTodayCardIds.size).
     */
    fun selectNew(
        orderedCandidates: List<String>, startedCardIds: Set<String>,
        introducedTodayCardIds: List<String>, effectiveNewCardsPerDay: Int,
    ): List<String> {
        val taken = introducedTodayCardIds.mapTo(HashSet(), ::contentIdOf)
        return orderedCandidates
            .filter { it !in startedCardIds && taken.add(contentIdOf(it)) }
            .take((effectiveNewCardsPerDay - introducedTodayCardIds.size).coerceAtLeast(0))
    }

    /**
     * 기한 카드(복습: 오늘 학습일 끝 전, 학습·재학습: now 이하)를 dueAt 오름차순, 그 뒤 newCardIds, 맨 뒤에 now 뒤~now+learnAhead 사이 학습·재학습 카드(learn-ahead).
     * pinned가 있으면 맨 앞에 두고 중복을 뺀다(되돌리기 직후 그 카드).
     * 결과가 비면 세션 끝(now+learnAhead를 넘는 학습 카드만 남은 경우 포함, 기다리지 않는다).
     */
    fun plan(
        existing: List<CardState>, newCardIds: List<String>, now: Instant,
        learnAheadMinutes: Int, rule: StudyDayRule, pinned: String? = null,
    ): List<String> {
        val endOfToday = rule.startOfStudyDay(rule.studyDayOf(now) + 1).toEpochMilli()
        val learnAheadLimit = now.plus(Duration.ofMinutes(learnAheadMinutes.toLong())).toEpochMilli()
        fun List<CardState>.ids() = sortedBy { it.dueAtEpochMs }.map { it.cardId }
        val nowMs = now.toEpochMilli()
        val due = existing.filter {
            when (it.phase) {
                Phase.REVIEW -> it.dueAtEpochMs < endOfToday
                Phase.LEARNING, Phase.RELEARNING -> it.dueAtEpochMs <= nowMs
                Phase.NEW -> false // 새 카드는 newCardIds로만 들어온다
            }
        }.ids()
        // learn-ahead: 아직 기한 전인 학습 카드는 새 카드 뒤에 둔다(버튼에 보인 간격을 지키고, 남은 카드가 그것뿐일 때만 앞당긴다).
        val ahead = existing.filter {
            (it.phase == Phase.LEARNING || it.phase == Phase.RELEARNING) && it.dueAtEpochMs > nowMs && it.dueAtEpochMs <= learnAheadLimit
        }.ids()
        // 이미 평가한 카드는 새 카드로 다시 넣지 않는다.
        val started = existing.filter { it.phase != Phase.NEW }.mapTo(HashSet()) { it.cardId }
        return (listOfNotNull(pinned) + due + newCardIds.filter { it !in started } + ahead).distinct()
    }
}
