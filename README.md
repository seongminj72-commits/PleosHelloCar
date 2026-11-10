# Pleos DriveFlow

## 공모전 제출 정보

### 앱 이름과 한 줄 설명

Pleos DriveFlow — 도착 예정 시간과 매장 준비 시간을 맞춰 주문 시점을 추천하는 차량용 픽업 주문 앱

### 실행 방법

저장소 루트에서 연결된 에뮬레이터 한 대를 대상으로 제출용 release APK를 실행합니다.

```bash
adb install -r "apk/조성민_PleosDriveFlow_release.apk"
adb shell am start -n com.example.pleoshellocar/androidx.car.app.activity.CarAppActivity
```

1. 홈 → 서비스 상태에서 Mock 차량 상태를 PARKED로 전환합니다.
2. 홈 → 매장 찾기 → 메뉴 → 주문 확인으로 이동합니다.
3. 모의 결제 → 주문번호 확인 → 내 주문에서 픽업 상태를 확인합니다.

### 테스트 환경과 필요 설정

Pleos_Connect_v2.1.0 AVD(API 34), 호스트 기어 P·0km/h에서 검증했습니다. 추가 설정 없이 Mock 모드로 전체 시연이 가능하며, 결제는 모의 결제로 실제 청구가 없습니다.

Real TMAP ETA는 debug 빌드 + 로컬 프록시 + 운영자 TMAP 키가 필요합니다. 제출용 release APK에서 Real로 바꾸면 "프록시 설정 필요"가 표시되는 것이 의도된 동작입니다. 정확한 위치 권한이 없으면 권한 안내가 먼저 표시됩니다. 자세한 내용은 [릴리스 서명 방법](#릴리스-서명-방법) 아래 설명과 [TMAP 연동 안내](docs/tmap-integration.md)를 참고하세요.

### APK 위치

- 경로: `apk/조성민_PleosDriveFlow_release.apk`
- 파일 크기: 3,734,970 bytes (50MB 이하).
- SHA-256: `f0faf2afeb610c86a7b6e5c97993157b431e0861ded86ee429b8f8175448496a`
- 빌드·복사 절차: [apk/README.md](apk/README.md)
- 파일이 50MB를 넘으면 저장소에 커밋하지 않고 GitHub Releases에 업로드한 뒤, 이 항목을 해당 Release 페이지 URL과 실제 APK의 SHA-256으로 갱신합니다.

Android Automotive OS & Pleos Connect Application Development

차량의 예상 도착시간과 가상 매장의 준비시간을 비교해 주문 시점을 추천하고, 정차 상태에서 메뉴 선택·모의 결제·픽업 안내를 시연하는 MVP입니다. PleosHelloCar의 v0.1.0 Hello World 기준점을 보존합니다.

## 해결 문제와 기능

도착시간과 매장 준비시간을 따로 확인해야 하는 불편을 줄이는 것이 목표입니다. 홈, 가상 매장 3곳, 커피 메뉴 4종, 수량·합계·주문 확인·취소, ETA 추천, 모의 결제와 주문번호, 픽업 상태·텍스트 알림을 구현했습니다. 차량 상태가 UNKNOWN/DRIVING이면 도메인에서 주문 조작을 제한합니다.

차량 상태와 ETA의 기본값은 Mock이며, 매장·결제·주문 상태는 시뮬레이션입니다. 설정에서 TMAP 실제 ETA(개발 프록시)로 전환할 수 있습니다. 실제 매장 API와 금융 결제는 미연동입니다. 데모 결제 — 실제 금액이 청구되지 않습니다. 카드·계좌·결제 토큰을 수집하지 않습니다. 주문은 세션 메모리이며 재시작하면 초기화됩니다.

## 구조 및 환경

- automotive: Automotive 진입점과 앱 리소스.
- shared: car 화면, domain 계산·정책, data 저장소, vehicle/navigation Provider, payment 시뮬레이터.
- mobile: 기존 Compose/Android Auto projected 구성 유지.
- docs: 설계, SDK 조사, 테스트, 시연 및 검증 기록.
- macOS Apple Silicon / Android Studio / Kotlin / Gradle Kotlin DSL (shared는 Groovy).
- Gradle9.6.0, AGP9.4.1, JDK25 toolchain. minSdk34, compileSdk/targetSdk37.
- Car App Library shared/projected 선언1.4.0, automotive1.7.0. Pleos Connect v2.1.0 API34 AVD에서 시연.

## 빌드·테스트·실행

SDK Platform37과 필요한 도구 및 JDK25를 준비하고 local.properties의 sdk.dir 또는 ANDROID_HOME으로 로컬 SDK를 지정합니다.

```bash
./gradlew :automotive:assembleDebug :mobile:assembleDebug :shared:testDebugUnitTest :automotive:testDebugUnitTest :mobile:testDebugUnitTest
adb devices -l
adb -s emulator-5554 install -r automotive/build/outputs/apk/debug/automotive-debug.apk
adb -s emulator-5554 shell am start -n com.example.pleoshellocar/androidx.car.app.activity.CarAppActivity
```

실제 연결 serial을 사용하세요. debug APK 위치는 `automotive/build/outputs/apk/debug/automotive-debug.apk`입니다. 호스트를 P·0에 두고 서비스 상태에서 Mock UNKNOWN을 PARKED로 바꾼 뒤 주문합니다. **차량 상태 모드**에서 Mock/실제 차량을 선택할 수 있으며, 실제 차량 모드에 필요한 속도 권한은 같은 화면의 **차량 속도 권한 허용**에서 요청합니다. 실제 DRIVING 신호가 감지되면 Mock이 PARKED여도 주문을 차단합니다. 준비시간과 예상 지연은 **준비시간 / 예상 지연** 하위 화면에서 바꿉니다. 주문 상태는 내 주문의 진행 버튼으로 전환합니다.

## 검증과 SDK 활용 현황

v0.4.0 후보의 자동차·모바일 debug 빌드와 자동차 미서명 release 빌드가 성공했습니다. 이번 JUnit XML 기준 Kotlin 테스트 91개(shared 85, automotive 5, mobile 1)와 Python 서버 테스트 12개가 통과했습니다. 차량 기어·속도는 Pleos 공식 가이드가 안내하는 AAOS Car API(CarPropertyManager)로 수신하며 Pleos Connect v2.1.0 에뮬레이터에서 debug로 검증했습니다. Pleos Vehicle SDK 클래스는 미사용입니다. 현재 위치는 표준 Android 위치 API를 사용합니다. v0.4.0 서명 APK의 업데이트 설치와 에뮬레이터 실행을 검증했습니다. TTS 연동, 실차 동작과 출시 심사는 미검증입니다. [v0.4.0 릴리스 검증](docs/v0.4.0-release-validation.md) [SDK 조사](docs/sdk-feasibility.md) · [AAOS 검증](docs/aaos-vehicle-validation.md)

## v0.4.0 변경 요약

- 도착 전 준비, 동시 준비, 도착 후 대기처럼 결과를 먼저 보여주고 메뉴별 준비시간을 반영합니다. [추천 UX](docs/eta-recommendation-ux.md)
- 차량 상태를 확인할 수 없거나 주행 중이면 주문을 차단하는 기반과 Mock/실제 차량 모드를 추가했습니다. [차량 상태 기반 검증](docs/vehicle-sdk-validation.md)
- AAOS Car API로 실제 기어와 속도를 읽습니다. Pleos Connect 에뮬레이터에서 검증했으며 Pleos Vehicle SDK 클래스는 미사용입니다. [AAOS 차량 검증](docs/aaos-vehicle-validation.md)
- 앱에서 REAL→Mock 전환은 실제 PARKED일 때만 허용하며, Mock 모드에서도 실제 DRIVING 신호를 감지하면 주문을 차단합니다. Pleos 에뮬레이터 호스트에서 D·30km/h에도 정차 전용 행이 눌린 결과를 반영했습니다. [모드 전환 게이트 검증](docs/aaos-vehicle-validation.md)
- 화면 갱신 때 바뀌던 행 제목을 고정해 호스트가 갱신을 새 단계로 세면서 `OverLimitFlowViolationException`으로 종료되던 문제를 수정했습니다. v0.3.x ETA 연결 설정의 모드 전환에도 같은 잠재 문제가 있었습니다. [크래시 수정 및 반복 검증](docs/aaos-vehicle-validation.md)

## 문서

- [아키텍처](docs/architecture.md)
- [SDK 및 UI 가능성](docs/sdk-feasibility.md)
- [테스트 계획](docs/test-plan.md)
- [에뮬레이터 검증](docs/emulator-validation.md)
- [공모전 데모](docs/contest-demo.md)
- [TMAP 실 API 검증](docs/tmap-live-validation.md)
- [릴리스 하드닝 검증](docs/release-hardening-validation.md)

## 향후 개발

호환성이 확인된 Pleos 차량·ETA Provider, 호스트 검증 정책, 주문 영속화 및 서버 연동, 음성 안내를 단계적으로 검토합니다. 개발용 ALLOW_ALL_HOSTS_VALIDATOR는 출시 전에 교체해야 합니다. 실제 결제는 이번 MVP 범위에서 제외합니다. 템플릿 구동 성공만으로 음식 주문 UI의 심사 적합성을 보장하지 않습니다.


## v0.3.0 — TMAP ETA 확장 (로컬 개발)

Mock/Real 모드 전환, Android 현재 위치, 키를 서버에만 보관하는 TMAP 자동차 경로 프록시와 추천 자동 갱신을 추가했다. 기본값은 Mock이다. 실제 TMAP appKey를 이용한 실서비스 검증은 별도 필요하다. (개발 당시 기록. 2026-11-03 실 API 응답 확인: [docs/tmap-live-validation.md](docs/tmap-live-validation.md).) 실제 결제/POS는 구현하지 않았다.

- [TMAP 연결·Android Studio 실행 방법](docs/tmap-integration.md)
- [v0.3.0 개발 결과와 검증 범위](docs/v0.3.0-development-report.md)
- 프록시 실행: `python3 server/run_local_demo.py` (Python 3.10+, ADB 필요, 키는 숨김 입력)


## 릴리스 서명 방법

다음 Gradle 속성 네 개를 모두 주입하면 automotive release 서명이 활성화됩니다. 하나라도 없으면 미서명 release를 빌드합니다.

- `DRIVEFLOW_RELEASE_STORE_FILE`
- `DRIVEFLOW_RELEASE_STORE_PASSWORD`
- `DRIVEFLOW_RELEASE_KEY_ALIAS`
- `DRIVEFLOW_RELEASE_KEY_PASSWORD`

주입 위치는 사용자 전용 `~/.gradle/gradle.properties` 또는 각 속성 이름 앞에 `ORG_GRADLE_PROJECT_`를 붙인 환경변수입니다. 값은 저장소에 기록하지 않습니다.

Real TMAP ETA는 현재 debug 빌드 + 로컬 프록시 전용입니다. release는 HTTPS 프록시가 없으면 '프록시 설정 필요'로 표시되고 Mock 모드로 전체 데모가 가능합니다. 정확한 위치 권한이 없으면 권한 안내가 먼저 표시됩니다. release에서 실제 ETA를 사용하려면 HTTPS 프록시와 유효한 인증 설정을 앱에 전달하는 경로가 필요합니다. 근거는 `AndroidEtaRequest.kt`의 URL·설정 검증과 `server/run_local_demo.py`의 debug용 `run-as` 주입입니다.
