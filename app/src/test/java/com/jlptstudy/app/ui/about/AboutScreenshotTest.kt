package com.jlptstudy.app.ui.about

import com.jlptstudy.app.data.content.ReviewCounts
import com.jlptstudy.app.ui.home.HomeShots
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S09 스크린샷(ui-design 12절 이름 attribution_*). 자료 항목은 매니페스트 값으로 만든 시험 픽스처다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AboutScreenshotTest : HomeShots() {
    private val loaded: AboutUiState = runBlocking {
        val repo = AboutFixtures.repo()
        val d = repo.load()
        AboutUiState(
            AboutStatus.LOADED, d.contentVersion, d.dbLicense, d.notices, d.sources, ReviewCounts(28, 0, 14, 4, 8, 2),
            d.modelNames, repo.openSourceLicenses(),
        )
    }

    @Test fun attribution_loaded() = shots("attribution_loaded") { AboutScreen(loaded) }
    @Test fun attribution_loaded_sources() = shots("attribution_loaded_sources") { AboutScreen(loaded, anchor = "S-02") }
    @Test fun attribution_loaded_jmdict() = shots("attribution_loaded_jmdict") { AboutScreen(loaded, anchor = "S-01") } // 2026-11-13 변경 내용(자동 생성 문제 가공) 표시 확인
    @Test fun attribution_loaded_licenses() = shots("attribution_loaded_licenses") { AboutScreen(loaded, anchor = "h:other") }
    @Test fun attribution_loaded_font200() = shots("attribution_loaded", fontScale = 2f) { AboutScreen(loaded) }
    @Test fun attribution_error() = shots("attribution_error") { AboutScreen(AboutUiState(AboutStatus.FAILED)) }

    private val sheetMeaning = com.jlptstudy.app.ui.review.MeaningUi("뜻", "translated", "llm:model-x@2026-11-01", "unreviewed", null, null)
    private val sheetTexts = runBlocking { AboutFixtures.repo().load().meaningSourceTexts }
    @Test fun provenance_sheet() = shots("provenance_sheet") { ProvenanceSheetContent(sheetMeaning, sheetTexts) {} }
    @Test fun provenance_sheet_font200() = shots("provenance_sheet", fontScale = 2f) { ProvenanceSheetContent(sheetMeaning, sheetTexts) {} }
}
