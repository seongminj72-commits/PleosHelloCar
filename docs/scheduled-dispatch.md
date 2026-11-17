# 예약 전달 0단계

기준 `main`은 PR #13 병합 커밋 `7bb73c9`이다. 기존 앱은 Navigation 전환으로 Session이 중단되면 NaviHelper 리스너를 해제한다. 검증 빌드는 Session 생성부터 파괴까지 ETA 연결을 유지하고 단계별 생명주기와 매장에 일치한 ETA 콜백을 `DispatchProbe`로 기록한다. 예약 생성이나 전송은 이 검증에 포함하지 않는다.

## 서명 release 시험

1. Pleos Connect v2.1.0에서 서명 APK를 업데이트 설치한다. 사용자 10의 `POST_NOTIFICATIONS` 권한을 허용하고, Coffee A에서 내비 안내를 시작한다.
2. Navigation을 전면에 둔 채 10분 이상 `adb logcat -v threadtime -s DispatchProbe DriveFlowETA AndroidRuntime`을 수집한다. 테스트 시작·중간·종료에 같은 PID와 프로세스 상태를 `adb shell dumpsys activity processes`로 확인한다.
3. Session이 중단된 뒤 20초에 일반 알림, 40초에 CarToast를 각각 한 번 요청한다. Navigation 전면 상태에서 녹화로 실제 표시를 확인하고 NotificationManager 기록과 대조한다.
4. Session과 화면의 종료 및 복귀 순서를 기록한다. ETA 유지와 알림 결과를 근거로 Session 유지와 Foreground Service 중 설계를 선택한다.

[CarAppExtender](https://developer.android.com/reference/androidx/car/app/notification/CarAppExtender)는 차량 화면용 확장과 높은 채널 중요도를 안내한다. AAOS에서 heads-up 표시는 호스트의 정책과 주행 상태에 따라 억제될 수 있으므로 Pleos 화면에서 관측한 결과를 판정에 쓴다. [AAOS 알림 제한](https://source.android.com/docs/automotive/hmi/notifications/throttle-suppress)

## 결과

### A. Session에서 유지

서명 검증과 업데이트 설치에 성공했다. Pleos Connect v2.1.0(API 34), Coffee A 안내, 콘솔 P·0 기준이다.

| 시각 | 관측 |
|---|---|
| 15:15:28 | Session onCreate → onStart → HomeScreen onStart |
| 15:17:09.124 | Navigation 전면 전환, StoreRouteScreen onStop → Session onStop |
| 15:17:17.124–15:20:02.234 | PID 13091에서 매장 일치 ETA 2,460초를 계속 수신. 15초 간격, 중복 초기 수신 포함 13건. |
| 15:20:09.187 | StoreRouteScreen → MenuScreen → StoreScreen → HomeScreen → Session onDestroy. onStop 이후 약 180초. |
| 파괴 후 | Navigation이 전면에 있었고 PID 13091은 previous 상태로 남았다. ETA 수신은 종료됐다. |

`dumpsys activity processes`에서 Navigation 전면 초반 DriveFlow 프로세스는 서비스 바인딩에 의해 `prcp / FGS (service)`로 분류됐다. 검증 APK A는 자체 Foreground Service를 시작하지 않았다. ProcessRecord가 남아도 Session 파괴 이후에는 ETA가 지속되지 않았다. **A는 10분 기준 미충족.**

| 안내 수단 | 결과 |
|---|---|
| CarNotificationManager + CarAppExtender | 15:17:29.142 요청. 사용자 10의 Android NotificationRecord에 중요도 4로 등록됐으나 전후 녹화에서 Navigation 위 heads-up을 확인하지 못했다. |
| CarToast | 15:17:49.135 요청. NotificationService가 `Suppressing toast from package com.android.car.templates.host by user request.`로 남겼고 녹화에 표시되지 않았다. |

`./gradlew test` 및 `:automotive:assembleRelease`가 성공했다. D·속도 > 0 시험은 이 단계에서 실행하지 않았다.

### B. Foreground Service 재시험 준비

A가 10분 기준을 충족하지 못해 Automotive에 12분 한도의 검증 서비스만 추가했다. 정차 상태의 사용자 안내 시작 탭에서 명시적으로 시작한다. 서비스는 별도 NaviHelper를 소유하고 콜백에 `owner=service`를 기록한다. 서비스 종료 시 연결을 해제하며 자동 재시작하지 않는다.

이 실험은 별도 위치 센서 접근이나 데이터 동기화 없이 사용자가 시작한 내비 경로의 남은 시간만 읽으므로 API 34의 `specialUse` 유형을 시험한다. `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`와 사용 목적을 선언하고 `startForeground`에 낮은 중요도의 진행 알림을 넘긴다. 사용자가 화면을 떠나기 전 명시적 조작으로 서비스를 시작한다. 이 구성의 Pleos 배포 적격성은 확인되지 않았다. [Android Foreground Service 유형](https://developer.android.com/develop/background-work/services/fgs/service-types) · [Android 시작 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

새 서명 release에서 서비스 시작, Session 파괴 후 ETA 지속, Navigation 전면 10분 유지, Foreground Service 알림 게시는 미실행이다. A에서 시험한 일회성 안내의 운전자 화면 표시도 미확인 상태다. `./gradlew test :automotive:assembleRelease` 성공.
