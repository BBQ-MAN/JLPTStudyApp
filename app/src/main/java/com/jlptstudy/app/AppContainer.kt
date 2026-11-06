package com.jlptstudy.app

import android.content.Context
import com.jlptstudy.app.data.attribution.AssetAttributionRepository
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.content.ContentDatabase
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.GrammarRepository
import com.jlptstudy.app.data.content.NoGrammarRepository
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.content.RoomContentRepository
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.speech.DeviceTtsSpeaker
import com.jlptstudy.app.speech.Speaker
import com.jlptstudy.srs.Fsrs6Scheduler
import com.jlptstudy.srs.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/**
 * 수동 DI(architecture.md 1절). 시계·시간대는 주입한다(System.currentTimeMillis 직접 호출 금지).
 * 두 DB 열기는 openContent/openStudy가 IO에서 한다. 실패하면 AppState의 실패 상태가 된다(S90).
 */
class AppContainer(
    val clock: Clock = Clock.systemUTC(),
    val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val scope: CoroutineScope,
    private val openContent: suspend () -> ContentRepository,
    private val openStudy: suspend () -> StudyRepository,
    /** 기본값은 늘 실패한다(시험용 컨테이너가 첫 안내 카드 없이 동작). 실제 앱은 create()가 에셋 읽기를 준다. */
    val attributionRepository: AttributionRepository = object : AttributionRepository {
        override suspend fun load() = throw java.io.IOException("no attribution")
        override suspend fun openSourceLicenses() = throw java.io.IOException("no licenses")
    },
    /** 청해 ViewModel이 생성 때 하나 만들고 onCleared에서 그것만 shutdown한다. 앱 범위 인스턴스는 두지 않는다. */
    val speakerFactory: () -> Speaker = { error("speakerFactory not provided") },
) {
    val scheduler: Scheduler = Fsrs6Scheduler()

    var contentRepository: ContentRepository? = null
        private set
    var studyRepository: StudyRepository? = null
        private set

    /** 콘텐츠 저장소가 문법 조회도 구현하면 그것, 아니면 빈 문법(시험용 가짜 저장소). 열기 전에는 null. */
    var grammarRepository: GrammarRepository? = null
        private set

    /** 콘텐츠·기록 저장소가 연습 조회·저장을 구현하면 그것(시험용 가짜 저장소는 null). */
    var quizContentRepository: QuizContentRepository? = null
        private set
    var quizRepository: QuizRepository? = null
        private set

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    /** user_settings.theme. MainActivity가 JlptTheme에 넘긴다. */
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    private var themeJob: Job? = null

    private val _state = MutableStateFlow<AppState>(AppState.Opening)
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var job: Job? = null

    /** 처음 한 번만 연다. */
    fun start() { if (job == null) retry() }

    /** "다시 시도": 같은 열기를 다시 한다. 열기가 진행 중이면 무시. */
    fun retry() {
        if (job?.isActive == true) return
        _state.value = AppState.Opening
        job = scope.launch { _state.value = open() }
    }

    /** S01 '시작' 저장 성공 뒤: 같은 프로세스에서 Activity를 다시 만들어도 홈으로 시작한다. */
    fun onboardingCompleted() { _state.update { if (it is AppState.Ready) AppState.Ready(needsOnboarding = false) else it } }

    /** 화면의 콘텐츠 읽기 실패: S90으로 보낸다. 재시도가 DB를 다시 열도록 캐시를 비운다. */
    fun contentReadFailed() { openedContent = null; contentRepository = null; grammarRepository = null; quizContentRepository = null; _state.value = AppState.ContentDbFailed }

    // 열린 DB는 재시도 사이에 재사용한다(같은 파일을 또 열어 연결이 쌓이지 않게).
    private var openedContent: ContentRepository? = null
    private var openedStudy: StudyRepository? = null

    private suspend fun open(): AppState {
        val content = openedContent ?: try { openContent() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            return AppState.ContentDbFailed
        }.also { openedContent = it }
        val study = openedStudy ?: try { openStudy() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            return AppState.RecordDbFailed
        }.also { openedStudy = it }
        val needsOnboarding = try { !study.settings().first().onboardingDone } catch (e: CancellationException) { throw e } catch (_: Exception) {
            return AppState.RecordDbFailed
        }
        contentRepository = content
        grammarRepository = content as? GrammarRepository ?: NoGrammarRepository
        studyRepository = study
        quizContentRepository = content as? QuizContentRepository
        quizRepository = study as? QuizRepository
        if (themeJob == null) themeJob = scope.launch { study.settings().map { it.theme }.collect { _theme.value = it } }
        return AppState.Ready(needsOnboarding)
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): AppContainer {
            val app = context.applicationContext
            val grammar = java.util.concurrent.atomic.AtomicReference<GrammarRepository?>()
            return AppContainer(
                scope = scope,
                attributionRepository = AssetAttributionRepository.create(app),
                speakerFactory = { DeviceTtsSpeaker(app, CoroutineScope(SupervisorJob() + Dispatchers.Main)) },
                openContent = { RoomContentRepository(ContentDatabase.open(app).contentDao()).also { grammar.set(it as? GrammarRepository) } },
                openStudy = { RoomStudyRepository(StudyDatabase.open(app).studyDao()) { grammar.get()?.levels().orEmpty().isNotEmpty() } },
            )
        }
    }
}
