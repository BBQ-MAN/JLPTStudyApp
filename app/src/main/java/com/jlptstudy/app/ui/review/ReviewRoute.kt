package com.jlptstudy.app.ui.review

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jlptstudy.app.R
import kotlinx.coroutines.launch

/** ViewModel을 화면에 잇는 얇은 층: 스낵바(저장 실패 "다시 시도", 되돌림 알림)만 여기서 띄운다. */
@Composable
fun ReviewRoute(vm: ReviewViewModel, onExit: () -> Unit, onChipClick: ((MeaningUi) -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.review_save_failed)
    val retry = stringResource(R.string.action_retry)
    val undone = stringResource(R.string.review_undone)
    LaunchedEffect(state.saveFailCount) {
        if (state.saveFailed != null) {
            if (snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) vm.retry()
        }
    }
    ReviewScreen(
        state, prefs, snackbar,
        onClose = onExit,
        onUndo = {
            vm.undo()
            scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(undone, duration = SnackbarDuration.Short) }
        },
        onToggleFurigana = vm::toggleFurigana, onReveal = vm::reveal, onRate = vm::rate, onStudyMore = vm::studyMore,
        onChipClick = onChipClick,
    )
}
