package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.RoomQuizContentRepository
import com.jlptstudy.app.data.content.RoomQuizGenRepository
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.practice.QuizTypeLabels
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.gen.GenResult
import com.jlptstudy.quiz.gen.GenType
import com.jlptstudy.quiz.gen.QuizGen
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * 원어민 검수용 표본 파일(quizgen.md 7.3). 환경 변수 QUIZGEN_SAMPLE_OUT(절대 경로)이 있을 때만 돈다 — verify에서는 건너뛴다.
 * 실행: QUIZGEN_SAMPLE_OUT=$PWD/_workspace/quizgen_sample.md ./gradlew :app:testDebugUnitTest --tests '*QuizGenSampleTest'
 */
@RunWith(RobolectricTestRunner::class)
class QuizGenSampleTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val seed = 20261108L
    private val perType = 30

    private val typeNames = mapOf(
        GenType.KANJI_READING to "① 한자 읽기", GenType.ORTHOGRAPHY to "② 표기",
        GenType.MEANING_JA_KO to "③-1 뜻 고르기(일본어 → 한국어)", GenType.MEANING_KO_JA to "③-2 단어 고르기(한국어 → 일본어)",
    )
    private val kindNames = mapOf(
        "LONG_VOWEL" to "장음", "VOICING" to "탁음·반탁음", "SOKUON" to "촉음", "YOON" to "요음", "OTHER_READING" to "다른 단어의 읽기",
        "KANJI_SWAP" to "한자 바꿔치기", "OTHER_WRITTEN" to "다른 단어의 표기", "OTHER_MEANING" to "다른 단어의 뜻", "OTHER_WORD" to "다른 단어",
    )

    /** 루비 표기 {표기|읽기} → 표기(읽기). [[ ]]는 벗긴다. */
    private fun plain(s: String) = Furigana.parse(s).joinToString("") { it.base + (it.ruby?.let { r -> "($r)" } ?: "") }

    private fun instruction(type: GenType, target: String?) =
        QuizTypeLabels.instruction(Section.VOCAB, type.code).replace(QuizTypeLabels.TARGET, target.orEmpty())

    @Test fun `표본 파일을 쓴다`() = runBlocking {
        val out = System.getenv("QUIZGEN_SAMPLE_OUT")
        assumeTrue("QUIZGEN_SAMPLE_OUT 없음", !out.isNullOrBlank())
        val db = ContentDatabase.open(context)
        try {
            val dao = db.contentDao()
            val gen = RoomQuizGenRepository(dao)
            val content = RoomQuizContentRepository(dao, gen)
            val version = dao.meta().firstOrNull { it.key == "content_version" }?.value.orEmpty()
            val sb = StringBuilder()
            sb.appendLine("# 자동 생성 연습 문제 표본 — 원어민 검수용")
            sb.appendLine()
            sb.appendLine("- 콘텐츠 DB 판: `$version`")
            sb.appendLine("- 표본 시드: `$seed` (같은 DB 판·같은 시드면 같은 문항이 다시 만들어집니다)")
            sb.appendLine("- 급수 N5·N4 × 유형 4개 × 최대 ${perType}문항. 대상 단어가 ${perType}개보다 적은 유형은 만들 수 있는 문항 전부를 싣고 그 사실을 적었습니다.")
            sb.appendLine("- 보기 4개 중 정답은 ✔ 표시 한 개입니다. 오답이 사실은 정답으로 볼 수 있는지(동음이의·동의어·이체자 등), 뜻풀이가 맞는지 봐 주세요.")
            sb.appendLine("- 검수 칸: `ok`이면 `[x]`, 문제가 있으면 `fix:` 뒤에 어떤 보기가 왜 문제인지 적어 주세요.")
            sb.appendLine()
            for (level in listOf(JlptLevel.N5, JlptLevel.N4)) {
                val bank = gen.bank(level)
                val avail = gen.availability(level).associate { it.type to it.targets }
                sb.appendLine("## ${level.name}")
                sb.appendLine()
                for (type in GenType.entries) {
                    val ids = gen.newSession(level, type, seed, perType)
                    val items = content.contents(ids)
                    sb.appendLine("### ${level.name} ${typeNames.getValue(type)}")
                    sb.appendLine()
                    sb.append("대상 단어 ${avail.getValue(type)}개 중 ${ids.size}문항.")
                    if (ids.size < perType) sb.append(" 만들 수 있는 문항이 ${perType}개보다 적어(대상이 적거나 오답이 모자라 건너뜀) 전부를 실었습니다.")
                    sb.appendLine()
                    sb.appendLine()
                    for ((n, id) in ids.withIndex()) {
                        val c = items.getValue(id)
                        val q = (QuizGen.regenerate(bank, id) as GenResult.Ok).q
                        val v = bank.vocab.first { it.id == q.vocabId }
                        sb.appendLine("#### ${level.name} ${typeNames.getValue(type).substringBefore(' ')} · %02d".format(n + 1))
                        sb.appendLine()
                        sb.appendLine("| 항목 | 내용 |")
                        sb.appendLine("|------|------|")
                        sb.appendLine("| ID | `$id` |")
                        sb.appendLine("| 지시문 | ${instruction(type, q.target)} |")
                        sb.appendLine("| 지문 | ${plain(c.stem)} |")
                        sb.appendLine("| 출처 단어 ID | `${v.id}` (${v.written ?: "-"} / ${v.reading}) |")
                        sb.appendLine("| 해설 | ${c.explanationKo.replace("\n\n", " / ")} |")
                        sb.appendLine()
                        sb.appendLine("| 번호 | 보기 | 정답 | 오답 이유 종류 | 오답 이유 | 출처 단어 ID |")
                        sb.appendLine("|------|------|------|----------------|-----------|--------------|")
                        for (i in 0..3) {
                            val d = q.why[i]
                            val right = i == c.answerIndex
                            sb.appendLine(
                                "| ${i + 1} | ${plain(c.choices[i])} | ${if (right) "✔ 정답" else ""} | " +
                                    "${d?.let { kindNames.getValue(it.kind.name) } ?: ""} | ${c.whyWrongKo[i].orEmpty()} | ${if (right) "`${v.id}`" else d?.sourceVocabId?.let { "`$it`" } ?: ""} |",
                            )
                        }
                        sb.appendLine()
                        sb.appendLine("- [ ] ok / fix: ")
                        sb.appendLine()
                    }
                }
            }
            File(out!!).apply { parentFile?.mkdirs() }.writeText(sb.toString())
        } finally {
            db.close()
        }
    }
}
