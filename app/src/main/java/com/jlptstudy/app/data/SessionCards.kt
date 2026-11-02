package com.jlptstudy.app.data

import com.jlptstudy.app.CardKind
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.srs.CardState

/**
 * 홈 수치와 복습 대기열(SessionPlanner.plan의 existing)이 쓰는 유일한 카드 집합(architecture.md 8.2).
 * = card_state 중 kind ∈ kinds ∧ 콘텐츠에 있고 retired 아님 ∧ 급수 ∈ levels. 기한 판정은 하지 않는다.
 * 꺼진 종류·뺀 급수의 기록은 지우지 않고 여기서만 걸러 낸다(다시 켜면 원래 dueAt 그대로 돌아온다).
 * shortcut: 호출마다 켜진 종류의 card_state 전부를 읽는다. 홈 열기가 300 ms를 넘으면 content_version별 급수 캐시로 바꾼다.
 */
class SessionCards(private val study: StudyRepository, private val content: ContentRepository) {
    suspend fun load(kinds: Set<CardKind>, levels: Set<JlptLevel>): List<CardState> {
        if (kinds.isEmpty() || levels.isEmpty()) return emptyList()
        val states = study.statesOfKinds(kinds)
        val levelOf = content.cardLevels(states.map { it.cardId })
        return states.filter { levelOf[it.cardId] in levels }
    }
}
