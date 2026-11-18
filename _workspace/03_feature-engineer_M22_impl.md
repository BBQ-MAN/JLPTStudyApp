# M22 Speaker 인터페이스·기기 TTS
- speech/Speaker.kt, speech/DeviceTtsSpeaker.kt(TtsEngine 껍데기로 TextToSpeech 감쌈 — 가짜 엔진 테스트용), ui/listening/ListeningViewModel.kt(M22 뼈대: Speaker 생성/onCleared shutdown만, M23이 확장)
- AppContainer: speakerFactory만(인스턴스 필드 없음). 매니페스트 <queries> TTS_SERVICE, 새 권한 없음.
- 테스트: DeviceTtsSpeakerTest, SpeakerStateTest, ListeningViewModelTest, StartExternalTest(Robolectric checkActivities(true)), ManifestTest 보강.
- 다른 점: DeviceTtsSpeaker 생성자가 9.3.2의 (context, scope, initTimeoutMs) 외에 테스트용 (scope, timeout, engineFactory) 주 생성자를 가짐. 초기화 시간 초과는 시계가 아니라 scope 가상 시간(9.3.2대로).
- 실기기 확인(DEV-2)은 unverified, 개발 종료 후 일괄.
