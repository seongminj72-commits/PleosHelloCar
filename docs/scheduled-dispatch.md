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

서명 검증 및 업데이트 설치 성공. Pleos Connect v2.1.0(API 34), Coffee A, 콘솔 P·0 기준이다. `./gradlew test :automotive:assembleRelease` 성공.

| 시각 | 관측 |
|---|---|
| 15:48:15.050 | 사용자 안내 시작 탭에서 Foreground Service 시작. `dumpsys activity services`의 시작 근거는 `PROC_STATE_TOP`. |
| 15:48:15.700 | StoreRouteScreen onStop 후 Session onStop. Navigation이 전면으로 전환됐다. |
| 15:48:23.488 | PID 6955의 서비스 소유 리스너에서 첫 매장 일치 ETA 2,340초 수신. |
| 15:51:15.796 | Session onDestroy. 다음 ETA 콜백(15:51:16.105)은 서비스 리스너에서 수신했다. |
| 15:58:46.520 | 동일 PID의 서비스가 ETA 2,400초 수신. 첫 수신 대비 **10분 23.032초**, Session 파괴 뒤 **7분 30.724초**. |
| 최종 확인 | Navigation이 전면, PID 6955는 `prcp / FGS (fg-service)`, 서비스는 `isForeground=true`, `types=40000000`. [화면](validation-scheduled-dispatch/01-navigation-after-10min.png) |

ADB 스냅숏을 합쳐 서비스 수신 기록 35건을 확보했다. Session 파괴 후 확인한 연속 구간에서는 약 15초 간격이었다. 로그 링 버퍼 회전과 스냅숏 사이에 누락이 있어 전체 구간에서 모든 조회에 응답했는지는 미확인이다. 수집한 앱 로그에서 크래시 패턴은 관측되지 않았다.

- Foreground Service 진행 알림 711은 중요도 2로 Android에 등록됐다. 20초 시점의 별도 중요도 4 알림은 녹화에서 heads-up이 확인되지 않았다. 40초 시점 CarToast는 호스트 억제 로그가 반복됐다.
- **백그라운드 ETA 실행 방식은 B를 선택한다.** A는 Session 파괴 시 ETA를 잃었고, B는 동일 프로세스에서 Session 파괴를 지나 10분 넘게 수신했다.
- **운전자 안내 수단은 미확정.** 두 시험 모두 P·0이었다. D·속도 > 0에서 자동 전달 및 즉시 안내, 다른 호스트, 실차, 배포 적격성은 미검증이다. 단계 1 이후 진행은 사용자 확인을 기다린다.

## 예약 전달 구현

Automotive Application이 매장 일치 ETA 공급자와 예약·주문 저장소를 소유한다. 화면 세션과 서비스가 이를 공유하고, 각각 내비 사용을 요구할 수 있다. 예약이 없다면 마지막 사용자가 나갈 때 내비 연결을 해제한다. 앱 프로세스가 종료되면 메모리의 예약은 복구되지 않는다.

- 정차 상태에서 주문 확인의 **도착에 맞춰 자동 전달**을 선택하면 시작한다. 내비 또는 Mock ETA에서 예약할 수 있다. 내비 ETA가 없거나 만료되면 보류하며, TMAP은 이 서비스의 예약 소스로 제공하지 않는다.
- 서비스와 화면은 같은 요청 ID와 예약을 읽는다. 전달·실패·취소 상태에서 서비스가 종료된다. 서비스 상한은 도착 예정 시각 + 10분과 예약 뒤 3시간 중 빠른 쪽이다. 전달 마감은 도착 예정 시각이며, 도착이 지난 주문은 실패한다. 다음 재시도는 각 실패 뒤 5·10·20초다.
- Pleos TextToSpeech 3.3.1의 ON_DEVICE, 초기화 콜백, EventListener, 한국어 로캘, speak 및 release를 사용한다. 공식 Maven AAR의 클래스 시그니처와 0단계 이전 프로브 기록을 대조했다. 서비스에서 재생을 요청하고, 예약이 끝나 서비스가 종료돼도 Application에서 완료·오류 콜백을 처리한다. 실패는 주문 화면에 표시한다. [Pleos TTS 이벤트 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)
- 알림은 기록용이다. Android 알림 권한을 허용해야 표시될 수 있으며 Pleos heads-up 표시는 0단계에서 확인되지 않았다. Foreground Service specialUse 배포 적격성과 실제 한국어 청취는 미확인이다.
- 정차 중 **서비스 상태 → ETA 연결 설정 → 예약 전달 시연**에서 25초 뒤 30→5분, 25초 뒤 10→30분, 마감까지 25초 시연과 가상 매장 통신 단절을 설정할 수 있다. 시연 ETA는 화면에 구분해 표시되며 선택한 내비 소스의 실 ETA는 변경하지 않는다.

### 예약 전달 release 검증

2026-11-17 서명 release에서 P·0 상태의 내비 전면 자동 전달을 확인했다. 실제 전달 로그와 주문 상태에 지연 안내가 남았다. TTS는 실패했고 Virtual Console은 D·속도 입력에 HTTP 503을 반환해 주행 조건을 만들지 못했다. TTS 요청 시점 보완 후속 서명 release에서는 Navigation 전면 상태에서 자동 전달과 ON_DEVICE TTS onReady→onStart→onDone 콜백을 확인했다. 회차별 상세, 캡처와 미실행 항목은 [예약 전달 release 검증 기록](validation-scheduled-dispatch.md)을 참조한다.
