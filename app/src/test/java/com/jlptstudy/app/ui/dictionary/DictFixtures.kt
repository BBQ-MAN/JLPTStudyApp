package com.jlptstudy.app.ui.dictionary

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.review.WordRef

// 시험 픽스처(앱 콘텐츠가 아니다): 화면 모양을 확인하기 위한 가짜 항목.
fun mean(text: String, source: String, method: String, status: String, reviewer: String?) =
    MeaningUi(text, source, method, status, if (reviewer != null) "2026-11-01" else null, reviewer)

/** 검수 상태 다섯 가지(생성 × 검수 조합). */
val fiveMeanings = listOf(
    mean("먹다", "jmdict-translated", "llm:model@2026-11-01", "unreviewed", null),
    mean("〜さん보다 정중한 호칭", "authored", "human", "reviewed", "human"),
    mean("생활하다, 살아가다", "authored", "llm:model@2026-11-01", "reviewed", "agent"),
    mean("표본 검수한 묶음의 뜻", "jmdict-translated", "human", "sampled", null),
    mean("검수자가 기록되지 않은 뜻", "jmdict-translated", "llm:model@2026-11-01", "reviewed", null),
)

val sampleWords = listOf(
    BrowseItem.Word("w1", "食べる", "たべる", "먹다", JlptLevel.N5),
    BrowseItem.Word("w2", "今日", "きょう", "오늘", JlptLevel.N5),
    BrowseItem.Word("w3", "コミュニケーション", "コミュニケーション", "의사소통, 소통, 커뮤니케이션을 하는 일 전반을 가리키는 아주 긴 뜻풀이", JlptLevel.N3),
    BrowseItem.Word("w4", null, "ありがとう", "고맙다는 인사", JlptLevel.N5),
    BrowseItem.Word("w5", "直接", "ちょくせつ", "직접", JlptLevel.N4),
    BrowseItem.Word("w6", "お茶", "おちゃ", "차(마시는 것)", JlptLevel.N5),
)

val sampleKanji = listOf("直" to "직", "骨" to "골", "角" to "각", "日" to "일", "生" to "생", "食" to "식", "今" to "금", "接" to "접", "茶" to "다", "語" to null)
    .map { (k, ko) -> BrowseItem.Kanji(k, ko, JlptLevel.N5) }

val sampleWordRefs = listOf(
    WordRef("w5", "直接", "ちょくせつ", "직접"), WordRef("w7", "正直", "しょうじき", "정직"), WordRef("w8", "直す", "なおす", "고치다, 〜さん을 바로잡다"),
)
