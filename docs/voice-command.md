# 명령 해석기·재주문·음성 안내 (2026-11-18)

브랜치 `codex/voice-command`는 `main`의 PR #14 병합 커밋 `cdec2bf`에서 시작했다. 기존 결제·매장·차량 안전 정책은 유지한다. 실제 주문/POS 연동은 없다.

## 0단계: STT 실인식 판정

`claude/pleos-sdk-probe`에서 만든 별도 debug APK를 설치하고 `--ez navi false --ez tts false --es stt listen`으로 시작했다. Android 활성 사용자 10에서 `RECORD_AUDIO`와 `pleos.car.permission.STT_SERVICE` 허용을 확인했다. [11월 18일 프로브 로그](validation-voice-command/stt-probe-2026-11-18.log)에는 ON_DEVICE 초기화 성공, 리스너 등록, `request()`, `onReady()`가 있다. 당시 한국어 최종 결과가 없었던 이유는 호스트 마이크 입력이 연결되지 않았기 때문이다.

2026-11-24 사용자가 에뮬레이터 Extended Controls → Microphone → Enable Host Microphone Access를 켜고 macOS 마이크 권한을 허용한 뒤 재시험했다. 사용자 보고에 따르면 `onReady → onUpdatedRms → onStartedRecognition → onUpdated(부분, false) 반복 → onEndedRecognition → onUpdated("아메리카노 1잔 주문 주문해줘", true) → onUpdatedEpdData` 순서로 콜백이 왔다. 엔진 EPD가 발화 종료를 자동 감지했다. 이 보고를 근거로 **GO(한국어 최종 인식 확인)**로 판정한다. 이 회차의 원본 로그는 아직 저장소에 없으므로 독립 재현은 release 실기 검증에서 수행한다.

## 2026-11-24 STT 입력 연결

`codex/stt-input`에서 ON_DEVICE SpeechToText 3.3.1을 앱 범위 한 인스턴스로 연결했다. 공식 가이드의 초기화 → 리스너 등록 → 요청 순서를 따른다. 사용한 메서드와 콜백 시그니처는 Gradle 캐시의 `SpeechToText-3.3.1.aar` 안 `ai.pleos.playground.stt.SpeechToText`, `ResultListener`, `OnInitializeListener` 클래스를 `javap`로 확인했다. 명령 화면의 “말로 명령하기”는 정차 중에만 열리고 `RECORD_AUDIO` 권한을 요청한다. 부분 결과는 300ms 이상 간격으로 표시하며, 10초 안에 최종 결과가 없거나 오류·주행 상태가 오면 인식을 멈추고 결과를 버린다. 최종 문장은 기존 명령 확인 화면을 거쳐서만 실행된다. TTS 재생 중에는 인식을 시작하지 않는다.

명령 해석기는 시점이 없어도 `주문` 또는 `줘`가 있으면 즉시 주문으로 분류한다. `도착 N분 전`은 숫자 1~180분과 한글 한자어 1~99분(예: `십`, `이십`, `구십구`)을 지원한다. 범위 밖 한글 분 표현은 추정하지 않고 안내 문구와 함께 `Unknown`으로 처리한다.

### STT 연결 검증 기록

| 항목 | 결과 |
|---|---|
| 사용자 프로브 한국어 최종 결과 | 2026-11-24 사용자 보고로 확인. 원본 로그 미수령 |
| `./gradlew test :automotive:assembleDebug` | 통과. JUnit XML: shared 104, automotive 10, mobile 1, 합계 115 |
| `./gradlew :automotive:assembleRelease` | 서명 속성 없이 R8 빌드 통과. 산출물은 미서명 |
| 서명 release 실기 발화 2종·무음·주행 중 중단 | 미실행. 사용자 서명 빌드 및 에뮬레이터 시험 대기 |
| release logcat 크래시·OverLimitFlowViolation | 미실행. 위 실기 시험에서 확인 예정 |

실기 시험은 Pleos Connect v2.1.0에서 호스트 마이크 접근과 macOS 마이크 권한을 켜고 진행한다. 각 화면 캡처는 `docs/validation-voice-command/`에 저장한다. 실제 인식 결과는 확인 화면의 내용을 검토한 후 정차 상태에서만 실행한다.

## 구현

| 단계 | 구현 | 근거 |
|---|---|---|
| 1 명령 해석 | `VoiceCommandParser`가 예약·즉시·재주문·취소를 분리한다. 메뉴는 현재 매장의 실제 목록과 대조하며 없는 메뉴·모호한 문장은 `Unknown`을 반환한다. 수량 1~9와 라테/라떼·공백 차이를 처리한다. 범위 밖 수량이나 여러 수량을 지정한 문장은 거절한다. 명령 실행 시 기존 장바구니 항목을 제거한다. | `shared/domain/VoiceCommandParser.kt`, `DriveFlowController.kt` 및 단위 테스트 |
| 1 예약 시점 | 사용자 지정 `도착 N분 전`을 `ScheduledOrder.customLeadMinutes`에 저장하고 `arrivalAt` 기준 절대 시각으로 판단한다. 값이 없으면 기존 준비시간+여유 1분 정책을 사용한다. 준비 시간보다 짧은 N에는 경고한다. | `ScheduledOrder.kt`, `ScheduledDispatchTest.kt` |
| 2 재주문 | 완료 상태로 전환된 주문의 매장 ID·메뉴 ID·수량만 SharedPreferences에 저장한다. 홈의 `지난 주문 다시` 행이 현재 매장 영업/메뉴를 확인하고 장바구니를 채워 기존 주문 확인 화면으로 이동한다. 홈은 최대 5행이다. | `PreferencesCompletedOrderStore.kt`, `DriveFlowController.kt`, `DriveFlowScreens.kt` |
| 3 명령 화면 | 정차 전용 `명령` 화면에 예시 3개와 SearchTemplate 텍스트 입력을 둔다. 해석 결과는 별도 확인 화면에서 보여 주고 정차 중 확인해야 실행한다. 주행 중 기존 예약 취소 경로는 그대로 둔다. | `DriveFlowScreens.kt`, AndroidX Car App Library 1.7.0 `SearchTemplate.SearchCallback` 소스 |
| 3 음성 안내 | 공통 `VoiceAnnouncer`를 `DispatchVoice`로 구현했다. 해석·실행 결과를 화면 문구와 ON_DEVICE TTS로 안내하고, 백그라운드 예약 전달도 같은 인터페이스를 사용한다. | `CompletedOrderStore.kt`, `DispatchVoice.kt`, `ScheduledDispatchService.kt` |

## 검증 상태

`./gradlew test :automotive:assembleDebug :automotive:assembleRelease`의 JUnit XML 합계는 114개(shared 103, automotive 10, mobile 1), 실패·오류 0이다. 사용자가 같은 브랜치의 release APK를 서명 빌드했다. `apksigner verify --verbose`에서 v2 서명 `true`를 확인했고 Pleos Connect v2.1.0에 `adb install -r`이 성공했다.

| 항목 | 상태 |
|---|---|
| 즉시 주문 예시 | [확인](validation-voice-command/immediate-confirm.png) → [모의 결제 성공](validation-voice-command/immediate-receipt.png), 9,000원 |
| 재주문 예시 | 완료 주문 저장 → 앱 강제 종료·재시작 후 [홈 제안 유지](validation-voice-command/reorder-after-restart.png) → [확인](validation-voice-command/reorder-confirm.png) → [새 모의 주문](validation-voice-command/reorder-receipt.png) |
| 도착 기준 예약 예시 | [확인](validation-voice-command/scheduled-confirm.png) → [데모 전달됨](validation-voice-command/scheduled-sent.png). Mock ETA 8분에 도착 10분 전 지정이라 전달 시점이 이미 지나 즉시 전달됨 |
| 없는 메뉴·모호한 명령 | 없는 메뉴는 [실행 버튼 없는 안내](validation-voice-command/unknown-menu.png) 확인. 모호한 문장은 단위 테스트 통과, 화면 미실행 |
| 주행 중 명령 화면 차단 | Mock DRIVING 상태에서 홈 명령 행을 탭해도 진입하지 않고 “정차 상태 확인 필요” 토스트 확인. [차단 화면](validation-voice-command/driving-blocked.png) |
| STT 한국어 발화 → 같은 결과 | 사용자 발화 미실행; 인식 콜백 미확인으로 미실행 |
| 음성 안내 | 2026-11-23 release 재시험의 명령 확인 화면에서 ON_DEVICE TTS `started`·`completed` 로그 확인. 사용자 청취 미확인. [로그 요약](validation-voice-command/release-log-summary-2026-11-23.log) |
| 크래시·OverLimitFlowViolation | 2026-11-18 전체 흐름의 logcat은 에뮬레이터 종료로 소실. 2026-11-23 재시험(시작→명령 확인→Mock DRIVING 차단)의 logcat에서 FATAL·OverLimitFlowViolation 등 오류 패턴 0건. [로그 요약](validation-voice-command/release-log-summary-2026-11-23.log) |
