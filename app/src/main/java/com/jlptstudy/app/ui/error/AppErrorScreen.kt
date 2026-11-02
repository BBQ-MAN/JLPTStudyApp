package com.jlptstudy.app.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jlptstudy.app.AppState
import com.jlptstudy.app.R
import com.jlptstudy.app.ui.common.StatusAction
import com.jlptstudy.app.ui.common.StatusScreen

/** S90. 콘텐츠 DB 실패에는 "출처 보기"가 있고, 학습 기록 DB 실패에는 없다. 기록을 지우는 버튼은 두지 않는다. */
@Composable
fun AppErrorScreen(state: AppState, onRetry: () -> Unit, onOpenAppInfo: () -> Unit, onOpenSources: () -> Unit) {
    val content = state is AppState.ContentDbFailed
    StatusScreen(
        title = stringResource(if (content) R.string.error_content_title else R.string.error_record_title),
        body = stringResource(if (content) R.string.error_content_body else R.string.error_record_body),
        icon = painterResource(R.drawable.ic_ms_error),
        actions = buildList {
            add(StatusAction(stringResource(R.string.action_retry), onRetry))
            add(StatusAction(stringResource(R.string.action_app_info), onOpenAppInfo))
            if (content) add(StatusAction(stringResource(R.string.action_sources), onOpenSources))
        },
    )
}
