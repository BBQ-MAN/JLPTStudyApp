package com.jlptstudy.app.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.attribution.LicenseUi
import com.jlptstudy.app.data.attribution.LinkUi
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.attribution.SourceUi
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.ReviewCounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AboutStatus { LOADING, LOADED, FAILED }

/** architecture.md 7절 S09. */
data class AboutUiState(
    val status: AboutStatus = AboutStatus.LOADING,
    val contentVersion: String = "",
    val dbLicense: LinkUi? = null,
    val notices: List<NoticeUi> = emptyList(),
    val sources: List<SourceUi> = emptyList(),
    val reviewCounts: ReviewCounts? = null,
    val modelNames: List<String> = emptyList(),
    val openSourceLicenses: List<LicenseUi> = emptyList(),
)

/**
 * status는 AttributionRepository.load() 하나로 정한다. 집계·라이선스 목록이 실패해도(또는 콘텐츠 저장소가 없어도: S90에서 온 경우)
 * LOADED를 유지하고 해당 줄만 비운다.
 */
class AboutViewModel(private val attribution: AttributionRepository, private val content: ContentRepository?) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init { load() }

    fun retry() { _state.value = AboutUiState(); load() }

    private suspend fun <T> orNull(block: suspend () -> T?): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private fun load() {
        viewModelScope.launch {
            val data = orNull { attribution.load() }
            if (data == null) {
                _state.value = AboutUiState(AboutStatus.FAILED)
                return@launch
            }
            val licenses = orNull { attribution.openSourceLicenses() }.orEmpty()
            val counts = orNull { content?.meaningReviewCounts() }
            _state.value = AboutUiState(
                AboutStatus.LOADED, data.contentVersion, data.dbLicense, data.notices, data.sources, counts, data.modelNames, licenses,
            )
        }
    }
}
