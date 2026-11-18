# 명령 해석기·재주문·음성 안내 (2026-11-18)

브랜치 `codex/voice-command`는 `main`의 PR #14 병합 커밋 `cdec2bf`에서 시작했다. 기존 결제·매장·차량 안전 정책은 유지한다. 실제 주문/POS 연동은 없다.

## 0단계: STT 실인식 판정

`claude/pleos-sdk-probe`에서 만든 별도 debug APK를 설치하고 `--ez navi false --ez tts false --es stt listen`으로 시작했다. Android 활성 사용자 10에서 `RECORD_AUDIO`와 `pleos.car.permission.STT_SERVICE` 허용을 확인했다. [프로브 로그](validation-voice-command/stt-probe-2026-11-18.log)에는 ON_DEVICE 초기화 성공, 리스너 등록, `request()`, `onReady()`가 있다. 한국어가 담긴 `onUpdated(…, completed=true)`는 아직 관찰되지 않았다. 사용자에게 에뮬레이터 Extended controls의 호스트 마이크 입력과 발화를 요청했다. **판정 대기**이며 STT 입력 행은 추가하지 않았다. 확인되지 않은 SDK 기능을 지원한다고 표시하지 않는다.

## 구현

| 단계 | 구현 | 근거 |
|---|---|---|
| 1 명령 해석 | `VoiceCommandParser`가 예약·즉시·재주문·취소를 분리한다. 메뉴는 현재 매장의 실제 목록과 대조하며 없는 메뉴·모호한 문장은 `Unknown`을 반환한다. 수량 1~9와 라테/라떼·공백 차이를 처리한다. 범위 밖 수량이나 여러 수량을 지정한 문장은 거절한다. 명령 실행 시 기존 장바구니 항목을 제거한다. | `shared/domain/VoiceCommandParser.kt`, `DriveFlowController.kt` 및 단위 테스트 |
| 1 예약 시점 | 사용자 지정 `도착 N분 전`을 `ScheduledOrder.customLeadMinutes`에 저장하고 `arrivalAt` 기준 절대 시각으로 판단한다. 값이 없으면 기존 준비시간+여유 1분 정책을 사용한다. 준비 시간보다 짧은 N에는 경고한다. | `ScheduledOrder.kt`, `ScheduledDispatchTest.kt` |
| 2 재주문 | 완료 상태로 전환된 주문의 매장 ID·메뉴 ID·수량만 SharedPreferences에 저장한다. 홈의 `지난 주문 다시` 행이 현재 매장 영업/메뉴를 확인하고 장바구니를 채워 기존 주문 확인 화면으로 이동한다. 홈은 최대 5행이다. | `PreferencesCompletedOrderStore.kt`, `DriveFlowController.kt`, `DriveFlowScreens.kt` |
| 3 명령 화면 | 정차 전용 `명령` 화면에 예시 3개와 SearchTemplate 텍스트 입력을 둔다. 해석 결과는 별도 확인 화면에서 보여 주고 정차 중 확인해야 실행한다. 주행 중 기존 예약 취소 경로는 그대로 둔다. | `DriveFlowScreens.kt`, AndroidX Car App Library 1.7.0 `SearchTemplate.SearchCallback` 소스 |
| 3 음성 안내 | 공통 `VoiceAnnouncer`를 `DispatchVoice`로 구현했다. 해석·실행 결과를 화면 문구와 ON_DEVICE TTS로 안내하고, 백그라운드 예약 전달도 같은 인터페이스를 사용한다. | `CompletedOrderStore.kt`, `DispatchVoice.kt`, `ScheduledDispatchService.kt` |

## 검증 상태

`./gradlew test :automotive:assembleDebug :automotive:assembleRelease`의 JUnit XML 합계는 114개(shared 103, automotive 10, mobile 1), 실패·오류 0이다. Automotive debug APK 및 **미서명** release APK가 생성됐다. 서명 release APK의 수동 시연은 사용자 서명 빌드 뒤 진행한다.

| 항목 | 상태 |
|---|---|
| 예시 명령 3종 → 확인 → 예약/주문/재주문 | 서명 release 수동 검증 대기 |
| 없는 메뉴·모호한 명령 | 순수 Kotlin 테스트 통과, 화면 검증 대기 |
| 앱 재시작 후 재주문 행 유지 | 서명 release 수동 검증 대기 |
| 주행 중 명령 화면 차단 | 서명 release 수동 검증 대기 |
| STT 한국어 발화 → 같은 결과 | 실제 인식 콜백 미확인으로 미실행 |
| 크래시·OverLimitFlowViolation | 서명 release 수동 검증 대기 |
