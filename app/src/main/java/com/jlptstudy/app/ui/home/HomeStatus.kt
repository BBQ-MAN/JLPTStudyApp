package com.jlptstudy.app.ui.home

/** architecture.md 7절 S02 상태. M4는 세션 EMPTY의 이유(ALL_DONE·NEW_LIMIT_REACHED)에만 쓰고, 나머지는 홈 마일스톤(M5)이 쓴다. */
enum class HomeStatus { LOADING, READY, NO_LEVEL_SELECTED, NO_CONTENT_FOR_LEVELS, ALL_DONE, NEW_LIMIT_REACHED }
