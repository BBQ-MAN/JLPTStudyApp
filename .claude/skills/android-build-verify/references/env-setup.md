# 개발 환경 준비 (macOS 기준)

2026-11-01 이 컴퓨터에서 확인: Android Studio 내장 JBR(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`), SDK `~/Library/Android/sdk`(platforms `android-37.0`, build-tools `36.0.0`), Gradle 래퍼 9.8.2(TimemoreDotScaleApp과 같은 판), `local.properties`(`sdk.dir`). 이 상태로 `./gradlew help`, `:app:assembleDebug`, `test`가 통과했다. `verify.sh`가 종료 코드 2를 낼 때만 아래를 확인한다. 새 컴퓨터는 아래 순서로 준비한다. 설치는 사용자 컴퓨터를 바꾸는 작업이므로 에이전트는 명령을 제안하고, 실행은 사용자 승인 후에 한다.

## 1. JDK

AGP가 요구하는 JDK 버전(현재 AGP 릴리스 노트에서 확인, 최소 17)을 쓴다.

- `verify.sh`는 `JAVA_HOME`이 비어 있으면 Android Studio 내장 JBR을 자동으로 쓴다. 직접 `./gradlew`를 돌릴 때는 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 또는 `brew install --cask temurin@17` (버전은 AGP 요구에 맞춘다)
- 확인: `"$JAVA_HOME/bin/java" -version`. `/usr/bin/java`만 있고 실제 JDK가 없으면 macOS가 설치 안내만 띄운다.

## 2. Android SDK

둘 중 하나:

- Android Studio 설치 후 SDK Manager로 설치. 기본 경로 `~/Library/Android/sdk`
- 명령줄: `brew install --cask android-commandlinetools` 후
  `sdkmanager "platform-tools" "platforms;android-<compileSdk>" "build-tools;<버전>"`

경로 지정(둘 중 하나):

- 셸 프로필에 `export ANDROID_HOME="$HOME/Library/Android/sdk"`
- 프로젝트 루트 `local.properties`에 `sdk.dir=<위 경로>` — 이 파일은 컴퓨터마다 다르므로 커밋하지 않는다(`.gitignore`에 있음)

`verify.sh`는 경로 아래에 `platforms/`가 있어야 SDK로 인정한다.

## 3. Gradle 래퍼

래퍼(`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)는 저장소에 있다. 시스템 Gradle과 섞으면 빌드 결과가 달라지므로 `./gradlew`만 쓴다. 래퍼가 없어졌으면 `brew install gradle` 후 프로젝트 루트에서 `gradle wrapper --gradle-version <AGP가 요구하는 버전>`(사용자 승인 후).

## 4. python3 (콘텐츠 파이프라인)

콘텐츠 파이프라인은 기본적으로 Python 3 표준 라이브러리만 쓴다(`content-pipeline` 스킬 2절). macOS의 `python3`(Xcode Command Line Tools) 또는 `brew install python`. 확인: `python3 --version`. 서드파티 패키지를 설치하지 않는다.

## 5. adb와 실기기

- `adb`는 `$ANDROID_HOME/platform-tools`에 있다. `PATH`에 추가한다.
- 휴대폰: 개발자 옵션 → USB 디버깅 켜기, 또는 무선 디버깅(`adb pair <ip:port>` 후 `adb connect`).
- 확인: `adb devices`에 기기가 `device` 상태로 보여야 한다.
- 청해(TTS)는 기기에 일본어 음성 데이터가 있어야 소리가 난다: 설정 → 시스템 → 언어 → 텍스트 음성 변환 출력 → 기본 엔진 설정 → 음성 데이터 설치 → 일본어. 에뮬레이터 이미지에는 없을 수 있다.

## 6. 확인 순서

```bash
"${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin/java" -version
sed -n 's/^sdk\.dir=//p' local.properties; ls ~/Library/Android/sdk/platforms
python3 --version
adb devices
.claude/skills/android-build-verify/scripts/verify.sh
```
