package com.jlptstudy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlptstudy.app.CardKind
import com.jlptstudy.app.data.content.GrammarRepository
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ThemeMode
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.level.LevelOption
import com.jlptstudy.app.ui.level.loadLevelOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** architecture.md 7절 S08. desiredRetention은 두지 않는다(UI-U06 A). */
data class SettingsUiState(
    val loading: Boolean = true,
    val targetLevels: Set<JlptLevel> = emptySet(),
    val newCardsPerDay: Int = 10,
    val dayBoundaryHour: Int = 4,
    val vocabRecognitionEnabled: Boolean = true,
    val kanjiCardsEnabled: Boolean = true,
    val vocabRecallEnabled: Boolean = false,
    val grammarCardsEnabled: Boolean = true,
    /** 계약 밖 필드: 번들에 문법 행이 있을 때만 문법 스위치를 보인다(architecture.md 9.1). */
    val grammarAvailable: Boolean = false,
    /** 계약 밖 필드: 번들에 청해 행이 있을 때만 "청해 대본 먼저 보기"를 보인다(ui-design 6.8). */
    val listeningAvailable: Boolean = false,
    val frontFurigana: Boolean = false,
    val bodyFurigana: Boolean = true,
    val listeningScriptFirst: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val contentVersion: String = "",
    val saveFailed: Boolean = false,
    /** 계약 밖 필드: 학습 급수 시트(LevelPicker)가 쓰는 급수별 개수·사용 가능 여부. */
    val levelOptions: List<LevelOption> = emptyList(),
    /** 계약 밖 필드: 카드 종류를 모두 끄려 했음("카드 종류를 하나 이상 켜세요"). 저장하지 않았다. */
    val kindsWarning: Boolean = false,
)

private data class Local(val contentVersion: String = "", val options: List<LevelOption> = emptyList(), val saveFailed: Boolean = false, val kindsWarning: Boolean = false, val grammarAvailable: Boolean = false, val listeningAvailable: Boolean = false, val loaded: Boolean = false)

private const val KINDS_REJECTED = "at least one card kind must be enabled"

/** 값을 바꿀 때마다 바로 저장한다(범위 밖·종류 모두 끔은 저장하지 않는다). 실패하면 저장된 값 그대로 보이고 saveFailed. */
class SettingsViewModel(private val study: StudyRepository, private val content: ContentRepository) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private var lastSave: ((UserSettings) -> UserSettings)? = null

    val state: StateFlow<SettingsUiState> = combine(study.settings(), local) { s, l ->
        SettingsUiState(
            loading = !l.loaded, targetLevels = s.targetLevels, newCardsPerDay = s.newCardsPerDay, dayBoundaryHour = s.dayBoundaryMinutes / 60,
            vocabRecognitionEnabled = s.vocabRecognitionEnabled, kanjiCardsEnabled = s.kanjiCardsEnabled,
            vocabRecallEnabled = s.vocabRecallEnabled, grammarCardsEnabled = s.grammarCardsEnabled,
            frontFurigana = s.frontFurigana, bodyFurigana = s.bodyFurigana, listeningScriptFirst = s.listeningScriptFirst,
            theme = s.theme, contentVersion = l.contentVersion, saveFailed = l.saveFailed,
            levelOptions = l.options, kindsWarning = l.kindsWarning, grammarAvailable = l.grammarAvailable, listeningAvailable = l.listeningAvailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            try {
                val (options, _) = loadLevelOptions(content)
                val grammar = (content as? GrammarRepository)?.levels().orEmpty().isNotEmpty()
                val listening = (content as? QuizContentRepository)?.let { r -> try { r.hasListening() } catch (e: CancellationException) { throw e } catch (_: Exception) { false } } == true
                local.update { it.copy(options = options, contentVersion = content.meta().contentVersion, grammarAvailable = grammar, listeningAvailable = listening, loaded = true) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                local.update { it.copy(loaded = true) }
            }
        }
    }

    fun setLevels(levels: Set<JlptLevel>) { if (levels.isNotEmpty()) save { it.copy(targetLevels = levels) } }
    fun setNewCards(n: Int) { if (n in 5..50 && n % 5 == 0) save { it.copy(newCardsPerDay = n) } }
    fun setDayBoundaryHour(h: Int) { if (h in 0..12) save { it.copy(dayBoundaryMinutes = h * 60) } }
    fun setFrontFurigana(v: Boolean) = save { it.copy(frontFurigana = v) }
    fun setBodyFurigana(v: Boolean) = save { it.copy(bodyFurigana = v) }
    fun setTheme(t: ThemeMode) = save { it.copy(theme = t) }
    fun setListeningScriptFirst(v: Boolean) = save { it.copy(listeningScriptFirst = v) }

    fun setKind(kind: CardKind, on: Boolean) {
        val next = { s: UserSettings ->
            when (kind) {
                CardKind.VR -> s.copy(vocabRecognitionEnabled = on)
                CardKind.VP -> s.copy(vocabRecallEnabled = on)
                CardKind.KR -> s.copy(kanjiCardsEnabled = on)
                CardKind.GR -> s.copy(grammarCardsEnabled = on)
            }
        }
        // 검사는 저장 트랜잭션 안(현재 저장값 기준)에서 한다. 문법 카드는 번들에 문법이 있을 때만 센다(저장소 검증과 같은 규칙).
        local.update { it.copy(kindsWarning = false) }
        save({ s -> next(s).also { require((if (local.value.grammarAvailable) it.enabledKinds else it.enabledKinds - CardKind.GR).isNotEmpty()) { KINDS_REJECTED } } })
    }

    fun retrySave() { lastSave?.let { save(it) } }
    fun dismissSaveFailed() = local.update { it.copy(saveFailed = false) }

    private fun save(transform: (UserSettings) -> UserSettings) {
        lastSave = transform
        viewModelScope.launch {
            try {
                study.updateSettings(transform)
                local.update { it.copy(saveFailed = false) }
            } catch (e: CancellationException) { throw e } catch (e: IllegalArgumentException) {
                if (e.message == KINDS_REJECTED) local.update { it.copy(kindsWarning = true) } else local.update { it.copy(saveFailed = true) }
            } catch (_: Exception) {
                local.update { it.copy(saveFailed = true) }
            }
        }
    }
}
