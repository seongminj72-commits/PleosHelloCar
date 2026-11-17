# 예약 전달 서명 release 검증

기준 브랜치 `codex/scheduled-dispatch`, 코드 기준 커밋 `817f753`. Pleos Connect v2.1.0 에뮬레이터에서 서명된 `automotive-release.apk`를 2026-11-17 설치해 진행했다.

## 검증 결과

| 시나리오 | 결과 | 근거 |
|---|---|---|
| 서명 release 설치 및 첫 화면 | 성공. 앱 실행과 예약 화면 이동 확인 | 에뮬레이터, 앱 로그 |
| 내비 전면 + ETA 급감 | 30분 → 5분 시연 ETA 변경 뒤 약 25초에 예약 전달 발생. 결과에 지연 안내 포함 | `probe-logs/scheduled-final/drive.log`, [내비 전면 1](validation-scheduled-dispatch/02-navigation-dispatch-1.png), [내비 전면 2](validation-scheduled-dispatch/03-navigation-dispatch-2.png) |
| 주문 상태 | Coffee A 주문이 전달됨으로 표시되고 “약 1분 늦게 준비될 수 있습니다” 표시 | [주문 상태와 음성 안내 오류](validation-scheduled-dispatch/04-order-delivered-with-tts-error.png) |
| TTS 음성 안내 | 실패. 초기화/리스너 등록 및 한국어 로캘 설정 로그 뒤 20초 제한에서 `ON_DEVICE TTS failed`; 실제 음성 출력은 관측되지 않음 | `probe-logs/scheduled-final/drive.log` |
| D·속도 > 0 검증 | 미실행. Virtual Console 상태에서 `bridge_ready=false`, `emulator_active=false`, DDS 입력 준비 상태 전체 false였고 gear/controls 요청은 HTTP 503. 실제 D 및 속도 > 0을 만들지 못함 | Virtual Console `/vehicle/state` 응답, 요청 결과 |
| ETA 증가, 통신 단절/복구, 마감 실패, 주행 중 취소 | 미실행. 필수 D·속도 조건이 구성되지 않아 중단 | 해당 시나리오 수행 로그 없음 |
| 크래시 확인 | 수집한 테스트 구간에서 예약 흐름의 크래시는 관측되지 않음. 주행 반복 시험은 미실행 | `probe-logs/scheduled-final/drive.log` |

이 회차는 Virtual Console의 P·0 상태에서 진행됐다. 따라서 백그라운드 전달 및 주문 상태는 관찰했지만 주행 중 자동 전달 완료 기준을 충족하지 않는다.

## TTS 후속 수정

기존 release 검증에서 ON_DEVICE 엔진 초기화 성공 뒤 `onReady`가 오지 않아 음성 요청이 시작되지 않고 실패하는 것을 관찰했다. 검증 프로브에서는 초기화 뒤 지연된 `speak` 호출 후 `onReady`가 관찰됐다. 이를 근거로 `DispatchVoice`가 초기화와 로캘 설정 뒤 3초 대기 후 음성 요청을 한 번 보내도록 보완했다. 이후 `./gradlew test`와 `:automotive:assembleRelease`는 성공했다.

이 보완 빌드는 **서명되지 않았고**, 에뮬레이터에서 아직 검증하지 않았다. 새 서명 APK에서 음성 시작/완료 콜백과 실제 청취를 확인해야 한다. D·속도 > 0 Virtual Console 입력이 복구되면 미실행 시나리오도 다시 시험한다.

## 재현에 사용한 확인 정보

- 에뮬레이터: Pleos Connect v2.1.0, API 34
- 서명 release 테스트에서 주문 전달 로그: `데모 전달 완료. 약 1분 늦게 준비될 수 있습니다`
- TTS 로그: 초기화 및 이벤트 리스너 설정 이후, 20초 제한에서 `ON_DEVICE TTS failed`
- Virtual Console: `gear.bridge_ready=false`, `controls.emulator_active=false`, DDS 입력 `all=false`; gear/controls PUT 응답 HTTP 503
- 미검증: 실제 차량, 다른 호스트, 실제 매장/POS, 실제 결제, release 실제 TMAP ETA, D·속도 > 0에서의 시나리오

## 후속 서명 APK 시험 (2026-11-17)

새 수정본을 기존 앱 위에 서명 업데이트 설치했고 `apksigner verify`의 v2 서명이 통과했다. Mock 차량은 PARKED, Mock ETA는 3분, 예약 시연 ETA는 25초 뒤 30→5분 설정이었다.

| 시각 | 결과 |
|---|---|
| 21:48:06.987 | Navigation 앱이 전면인 상태에서 예약 자동 전달 완료. 앱 로그에 `데모 전달 완료. 약 1분 늦게 준비될 수 있습니다` |
| 21:48:06.988 | ON_DEVICE TTS 초기화 시작 |
| 21:48:10.012 | 3초 지연 요청 뒤 `onReady`, `onStart`; 앱 로그 `ON_DEVICE TTS started` |
| 21:48:14.251 | `onDone`; 앱 로그 `ON_DEVICE TTS completed` |
| 시험 종료 | Navigation이 전면 앱으로 유지됨. 크래시 및 `FATAL`, `IllegalAccess`, `ClassNotFound`, `NoSuchMethod`, `NoClassDefFound`, `VerifyError`, `AbstractMethodError`, `OverLimitFlowViolation` 로그 0건 |
| Virtual Console | 여전히 gear bridge/DDS 입력 준비 안 됨(`bridge_ready=false`, `emulator_active=false`, `dds_ready.all=false`), 속도 0. 주행 조건은 미검증 |

[음성 안내가 끝난 시점의 Navigation 화면](validation-scheduled-dispatch/05-navigation-foreground-tts-completed.png)

이 시험으로 서비스가 백그라운드에 있는 동안 전달 음성의 SDK 완료 콜백까지 처리되는 것을 확인했다. 에뮬레이터의 실제 소리 청취 여부는 별도 확인하지 않았다. ETA 급증, 통신 단절·복구·마감 실패, 주행 중 취소 역시 아직 미실행이다.

빌드/설치 확인: 수정본은 서명 환경에서 release APK로 생성됐고 `apksigner verify` v2 및 `adb install -r`가 통과했다. `./gradlew test` 결과 XML 합계는 104개(실패 0, 오류 0). 최종 화면은 콘솔 P·0, 앱 Mock/PARKED 홈이며 [최종 상태 화면](validation-scheduled-dispatch/06-final-mock-parked-home.png)으로 남겼다.

## 도착 시각 기준 전달 보정 및 Virtual Console 재연결 (2026-11-17)

### 코드와 자동화 검증

- 전달 판단은 수신 당시 ETA 값의 분(minute) 차이가 아니라 `arrivalAt`, `dispatchAt`, 현재 단조 시각을 사용한다. ETA 8분·준비 6분이면 예약 후 1분(준비 6분 + 여유 1분)에 전송 가능한 상태가 된다.
- 3분 신선도 만료는 NAVIGATION ETA에만 적용한다. MOCK 예약은 예약한 도착 시각을 기준으로 시간이 흐르며, DROP/RISE/DEADLINE 시연 샘플이 들어오면 도착 시각을 갱신한다.
- 주문 상태에는 `Mock ETA 기준`과 현재 시점 기준 도착까지 남은 분을 표시한다. 늦음 분수는 도착까지 남은 시간을 올림 계산한 뒤 준비 시간과 비교한다.
- 알림 권한이 없으면 ETA 설정의 정차 전용 `알림 권한 허용` 행으로 요청한다. 거부해도 주문 전달 판단은 권한을 검사하지 않는다.
- `./gradlew test`: JUnit XML 107개, 실패 0, 오류 0. `:automotive:assembleRelease`: 성공. 이 변경 후 산출물은 미서명이다.

### Virtual Console 환경 재확인 (수정 전 서명 APK)

Pleos_Connect_v2.1.0을 `-no-snapshot`으로 cold boot한 뒤 콘솔 브라우저를 다시 열고 ADB 포트 8088을 forward했다. 페이지에 `DDS ONLINE`이 표시됐으며, API `/vehicle/state`에서도 `bridge_ready=true`, `emulator_active=true`, `dds_ready.all=true`로 확인했다.

Virtual Console에서 P·0 → D·ACCEL로 속도를 만들었다. 콘솔 계기에는 56km/h, 앱이 받은 시점의 AAOS 화면에는 D·34km/h가 표시됐고, DriveFlow의 실제 차량 모드는 `DRIVING (AAOS 차량 API · 주행 기어 또는 속도 감지)`였다. Coffee A 선택 탭에서 정차 필요 안내와 주문 조작 차단을 확인했다. 화면은 [D·34km/h에서 차단](validation-scheduled-dispatch/07-virtual-console-d34-order-blocked.png)에 있다. 이 환경 시험은 앞선 서명본 `f1c8595` 설치에서 수행했고, 전달 로직 변경본 검증으로 간주하지 않는다.

시험을 마친 뒤 Virtual Console과 앱을 P·0으로 되돌렸다. API 응답은 Park·0km/h, DDS 전체 준비 상태 true였다.

### 수정 release APK에서 남은 수동 시험

아래 항목은 새 코드가 포함된 서명 release APK 설치 후 실행한다. 기존 서명본의 결과를 대신 쓰지 않는다.

| 항목 | 상태 |
|---|---|
| D·속도 > 0 + Navigation 전면 자동 전달 및 음성 안내 | 미실행 — 현재 코드 변경본의 서명 APK 대기 |
| RISE ETA 변경 후 전달 시점 재계산 | 미실행 — 동일 |
| 통신 단절→복구 후 1회만 전달 | 미실행 — 동일 |
| 단절 유지→마감 실패 | 미실행 — 동일 |
| 주행 중 예약 취소 | 미실행 — 동일 |
| Mock 모드 예약 자동 전달, 화면의 Mock ETA 기준 표시 | 미실행 — 동일 |
| 알림 권한 거부 뒤에도 예약 전달 동작 | 미실행 — 현재 사용자 권한은 허용 상태, 코드 변경 전 설치본 |

알림은 보조 수단이다. 앱은 알림 권한을 전달 조건으로 사용하지 않는다. 수정본 서명 설치 이후 권한 거부 행과 실제 전달 동작을 함께 확인한다.

## 도착 시각 보정본 서명 APK 확인 (2026-11-17)

사용자가 서명한 이번 APK를 재빌드하지 않고 설치했다. `apksigner verify --verbose`에서 v2 서명 검증이 통과했고 `adb install -r` 및 첫 홈 화면 진입도 성공했다. Mock 상태를 PARKED로 전환할 수 있었고, 설정에서 Mock ETA 값을 8분에서 10분/3분으로 바꾸는 화면 갱신을 확인했다.

| 항목 | 결과 |
|---|---|
| 자동 전달 예약 시작 | 미완료. 예약 확인 화면에서 전달 버튼을 눌렀을 때 `최신 도착 예상시간을 확인하세요.` 안내가 표시됐다. 예약 검증에 사용 가능한 최신 ETA가 없어서 예약은 생성되지 않음 |
| Virtual Console 주행 입력 | 미실행. `http://127.0.0.1:8088/vehicle/state`에서 `bridge_ready=false`, `emulator_active=false`, `dds_ready.all=false`, Park·0km/h를 확인. D·속도 > 0을 만들 수 없어 주행 시나리오를 중단 |
| ETA 급증(RISE), 통신 단절→복구, 계속 단절→마감 실패 | 미실행 — 먼저 유효한 ETA와 예약이 필요하고, 실제 주행 조건도 준비되지 않음 |
| 주행 중 예약 취소 및 Mock 예약 자동 전달 | 미실행 — 예약 생성 및 주행 상태 미충족 |
| 실행 오류 패턴 | `logcat` 수집 구간에서 `FATAL`, `IllegalAccess`, `ClassNotFound`, `NoSuchMethod`, `NoClassDefFound`, `VerifyError`, `AbstractMethodError`, `OverLimitFlowViolation` 0건 |

이 회차에서는 서명 및 앱 시작만 확인했다. 위 수동 시나리오를 통과한 것으로 간주하지 않는다. Virtual Console DDS 입력이 다시 준비되고 최신 ETA가 생성되는 조건에서 재시험해야 한다.

## 예약 ETA 보정 서명 APK 재시험 (2026-11-18)

### 환경 및 확인 결과

- 수정된 `codex/scheduled-dispatch` 빌드의 사용자 서명 APK를 재빌드하지 않고 설치했다. 앱이 실행되어 DriveFlow 화면에 진입했다.
- Pleos Connect v2.1.0 / API 34에서 Virtual Console을 cold boot 후 다시 열고 연결했다. Console 상태에서 `bridge_ready=true`, `emulator_active=true`, `dds_ready.all=true`, P·0을 확인했다.
- Mock 차량을 PARKED로 두고 Coffee A에서 아메리카노 1잔을 예약했다. 확인 화면에 `[Mock ETA]`, 도착 약 8분, 준비 약 6분 및 주문 추천 문구가 표시됐다.
- 자동 전달 예약을 만들고 기다린 뒤 주문 화면에서 `모의 결제 완료`, `데모 전달 완료`, 예약상태 `데모 전달 · 전달됨` 및 `Mock ETA 기준`을 확인했다. 주문은 이후 `픽업 완료`까지 진행했다. 이는 직전 서명 APK에서 재현되지 않았던 Mock 예약 거절의 수정 경로를 확인한 결과다.
- 내비 목적지 경로 안내를 시작해 Navigation 앱에서 Coffee A 경로를 확인했다. DriveFlow 화면의 ETA 상태에 `[내비 ETA] DriveFlow Coffee A까지 약 27분`이 표시됐다.
- 실제 차량 모드에서 Virtual Console D·53km/h를 AAOS 신호로 확인했다. 이 상태에서 REAL→Mock 전환을 시도해 정차 전용 거부 안내를 확인했다. 콘솔은 시험 종료 시 P·0으로 돌렸다.
- 예약 전달 구간에서 크래시, `FATAL`, `OverLimitFlowViolation`, `IllegalAccess`, `ClassNotFound`, `NoSuchMethod`, `NoClassDefFound`, `VerifyError`, `AbstractMethodError`는 관찰되지 않았다.

### 이번 회차 판정

| 항목 | 결과 |
|---|---|
| Virtual Console 재연결 및 bridge/DDS 준비 | 통과 |
| Mock ETA 자동 전달 및 `Mock ETA 기준` 표시 | 통과 |
| Coffee A 내비 경로와 내비 ETA 표시 | 통과 |
| 실제 D·53km/h 신호 및 REAL→Mock fail-closed | 통과 |
| 내비 ETA를 이용한 주행 중·Navigation 전면 자동 전달 및 음성 안내 | 미실행 — 이번 예약 자동 전달은 Mock ETA 시나리오였음 |
| ETA RISE 후 전달 시점 재계산 | 미실행 |
| 매장 통신 단절 후 복구 시 정확히 1회 전달 | 미실행 |
| 매장 통신 단절 유지 후 마감 실패 | 미실행 |
| 주행 중 예약 취소 | 미실행 |
| 알림 권한 거부 상태에서 전달 | 미실행 — 이번 회차 알림 권한은 허용 상태 |

이 결과는 서명 APK에서 Mock 자동 전달의 ETA 시계 보정을 확인하지만, 위 미실행 항목은 통과로 간주하지 않는다. 실제 결제/POS, 실차 및 다른 호스트는 검증하지 않았다.
