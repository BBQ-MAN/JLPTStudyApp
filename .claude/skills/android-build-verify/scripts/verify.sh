#!/usr/bin/env bash
# Android 빌드 검증: 단위 테스트 + lint + 디버그 빌드(+ 콘텐츠 파이프라인 검사)를 실행하고 PASS/FAIL과 로그 경로를 출력한다.
# 인자로 Gradle 태스크를 넘기면 그 태스크만 실행한다. 종료 코드: 0 통과, 1 실패, 2 환경 없음.
set -uo pipefail
cd "$(dirname "$0")/../../../.." || exit 2   # .claude/skills/android-build-verify/scripts → 프로젝트 루트
setup=".claude/skills/android-build-verify/references/env-setup.md"
content_check="content/pipeline/check.sh"   # content-pipeline 스킬 5절. 파이프라인이 생기기 전에는 건너뛴다.

studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -x "$studio_jbr/bin/java" ]; then export JAVA_HOME="$studio_jbr"; fi   # 셸 설정 없이도 Android Studio 내장 JDK 사용
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$java_bin" -version >/dev/null 2>&1; then
  echo "ENV: JDK 없음(JAVA_HOME 또는 PATH의 java). $setup 1절 참고."
  exit 2
fi
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}}"
if [ -z "$sdk" ] || [ ! -d "$sdk/platforms" ]; then
  echo "ENV: Android SDK 없음(ANDROID_HOME/ANDROID_SDK_ROOT/local.properties sdk.dir='$sdk'). $setup 2절 참고."
  exit 2
fi
if [ ! -x ./gradlew ]; then
  echo "ENV: ./gradlew 없음. $setup 3절의 래퍼 부트스트랩을 먼저 한다."
  exit 2
fi
if [ -e "$content_check" ] && ! command -v python3 >/dev/null 2>&1; then
  echo "ENV: python3 없음(콘텐츠 파이프라인 검사에 필요). $setup 4절 참고."
  exit 2
fi

# test: Android 모듈 단위 테스트와 순수 JVM 모듈(:core:*) 테스트를 함께 돈다(testDebugUnitTest는 JVM 모듈을 빠뜨린다).
full=0
[ $# -eq 0 ] && { full=1; set -- test lintDebug assembleDebug; }

mkdir -p _workspace/build
log="_workspace/build/verify_$(date +%Y%m%d_%H%M%S).log"
ran="$*"
status=0
: >"$log"
if [ $full -eq 1 ] && [ -e "$content_check" ]; then
  ran="$content_check + $ran"
  echo "== $content_check" >>"$log"
  bash "$content_check" >>"$log" 2>&1 || status=1
fi
./gradlew --console=plain --continue "$@" >>"$log" 2>&1 || status=1
if [ $status -eq 0 ]; then
  echo "PASS: $ran (로그: $log)"
  exit 0
fi
echo "FAIL: $ran (로그: $log)"
grep -niE '^e: |FAILED|FAIL:|error:|What went wrong|Lint found|Traceback' "$log" | head -40 || tail -20 "$log"
exit 1
