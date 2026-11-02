package com.jlptstudy.app.data.attribution

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream

// attribution.json(architecture.md 6.6)을 그대로 옮긴 값. 화면 코드는 출처·고지 문구를 쓰지 않고 이 값만 보인다.
data class NoticeUi(val id: String, val title: String, val body: String)
data class LinkUi(val label: String, val url: String)
data class CreditUi(val text: String, val url: String?)
data class SourceUi(
    val id: String, val name: String, val version: String?, val attribution: String, val license: LinkUi,
    val homepage: String?, val modifications: String, val links: List<String>, val extraCredits: List<CreditUi>,
)
data class AttributionData(
    val contentVersion: String, val kind: String, val dbLicense: LinkUi, val levelListName: String?,
    val modelNames: List<String>, val reviewPolicy: String?, val sources: List<SourceUi>, val notices: List<NoticeUi>,
    val firstNotice: NoticeUi?, val meaningSourceTexts: Map<String, String>,
)

/** assets/licenses/libraries.json 한 줄과 전문. fullText는 fullTextAsset 파일 내용(없으면 null). */
data class LicenseUi(val name: String, val license: String, val url: String?, val fullTextAsset: String?, val fullText: String? = null)

interface AttributionRepository {
    /** assets/content/attribution.json. 읽기·파싱 실패는 예외(부분 표시 없음). */
    suspend fun load(): AttributionData
    /** assets/licenses/(손으로 커밋): libraries.json 중 distributed인 항목 + 전문 파일. 읽기 실패는 예외. */
    suspend fun openSourceLicenses(): List<LicenseUi>
}

class AssetAttributionRepository(
    private val openLicense: (String) -> InputStream = { throw java.io.FileNotFoundException(it) },
    private val open: () -> InputStream,
) : AttributionRepository {
    override suspend fun openSourceLicenses(): List<LicenseUi> = withContext(Dispatchers.IO) {
        fun read(name: String) = openLicense(name).bufferedReader().use { it.readText() }
        val texts = HashMap<String, String>()
        json.decodeFromString(ListSerializer(LibDto.serializer()), read(LIBRARIES_ASSET)).filter { it.distributed }.map {
            LicenseUi(it.name, it.license, it.url, it.fullText, it.fullText?.let { f -> texts.getOrPut(f) { read(f) } })
        }
    }

    override suspend fun load(): AttributionData = withContext(Dispatchers.IO) {
        val text = open().bufferedReader().use { it.readText() }
        json.decodeFromString(Dto.serializer(), text).toData()
    }

    companion object {
        const val ASSET_PATH = "content/attribution.json"
        const val LIBRARIES_ASSET = "libraries.json"
        fun create(context: Context): AssetAttributionRepository {
            val assets = context.applicationContext.assets
            return AssetAttributionRepository({ assets.open("licenses/$it") }) { assets.open(ASSET_PATH) }
        }
        private val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable private class LibDto(val name: String, val license: String, val url: String? = null, val distributed: Boolean, val fullText: String? = null)
    @Serializable private class LinkDto(val name: String, val url: String)
    @Serializable private class CreditDto(val text: String, val url: String? = null)
    @Serializable private class NoticeDto(val id: String, val title: String, val body: String)
    @Serializable private class FirstNoticeDto(val title: String, val body: String)
    @Serializable private class SourceDto(
        val id: String, val name: String, val version: String? = null, val attribution: String, val license: String,
        val licenseUrl: String, val homepage: String? = null, val modifications: String,
        val links: List<String> = emptyList(), val extraCredits: List<CreditDto> = emptyList(),
    )
    @Serializable private class Dto(
        val contentVersion: String, val kind: String, val dbLicense: LinkDto, val levelListName: String? = null,
        val modelNames: List<String> = emptyList(), val reviewPolicy: String? = null, val sources: List<SourceDto>,
        val notices: List<NoticeDto>, val firstNotice: FirstNoticeDto? = null, val meaningSourceTexts: Map<String, String>,
    ) {
        fun toData() = AttributionData(
            contentVersion, kind, LinkUi(dbLicense.name, dbLicense.url), levelListName, modelNames, reviewPolicy,
            sources.map { s ->
                SourceUi(s.id, s.name, s.version, s.attribution, LinkUi(s.license, s.licenseUrl), s.homepage, s.modifications,
                    s.links, s.extraCredits.map { CreditUi(it.text, it.url) })
            },
            notices.map { NoticeUi(it.id, it.title, it.body) },
            firstNotice?.let { NoticeUi("firstNotice", it.title, it.body) },
            meaningSourceTexts,
        )
    }
}
