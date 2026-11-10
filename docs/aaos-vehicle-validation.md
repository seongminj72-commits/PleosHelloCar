# AAOS 차량 기어·속도 연동 검증

검증: 2026-11-09~10 (KST). 기준 main `614c1da`, 작업 브랜치 `codex/aaos-vehicle-signal`.

## 결과: 부분 성공

Pleos Connect v2.1.0 / API 34에서 AAOS `CarPropertyManager`의 실제 에뮬레이터 기어·속도 신호를 수신했다. P·0은 PARKED, D·0/D·30/N·30은 DRIVING, 권한 거부는 UNKNOWN이다. 이는 에뮬레이터 VHAL 신호 연동이며 실차 검증 또는 Pleos Vehicle SDK 연동 완료를 뜻하지 않는다.

**호스트 정차 전용 버튼 비활성화는 실패했다.** D·30km/h에서도 홈의 `서비스 상태` 행을 눌러 설정에 진입했다. 앱의 주문 도메인 차단은 D·0 및 Mock DRIVING에서 확인했다. 호스트 측 차단과 앱 측 차단을 구분한다. `InteractionPolicy`, `ParkedOnlyOnClickListener`, HostValidator는 변경하지 않았다. 따라서 주행 중 모드 전환 등 호스트만을 신뢰하는 데모 설정의 안전성을 보증하지 않으며, REAL 시연/제출물 갱신 전에 별도 검토가 필요하다.

지정된 중단 조건(라이브러리 빌드 실패, Car 서비스/속성 미지원, 시스템 전용 권한, 콘솔 변경 미수신)은 발생하지 않았다. 전체 성공으로 보고하지 않고 Draft PR로 검토한다.

## API 근거와 호환성

| 항목 | 확인/선택 | 근거 |
|---|---|---|
| 연결 | `Car.createCar(context, handler, CAR_WAIT_TIMEOUT_DO_NOT_WAIT, listener)`, `Car.PROPERTY_SERVICE`, `getCarManager`, `disconnect` | [Car](https://developer.android.com/reference/android/car/Car) |
| 기어 | `VehiclePropertyIds.GEAR_SELECTION`, Int, GLOBAL area 0, ONCHANGE. 사용자 선택 기어가 필요한 앱이므로 CURRENT_GEAR 대신 사용. 콘솔 P/D/N 수신 확인 | [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds#GEAR_SELECTION) |
| 기어 값 | `android.car.VehicleGear.GEAR_PARK/GEAR_DRIVE/GEAR_REVERSE/GEAR_NEUTRAL`; 숫자 하드코딩 없음, 미매핑 UNKNOWN | [VehicleGear](https://developer.android.com/reference/android/car/VehicleGear) |
| 속도 | `PERF_VEHICLE_SPEED`, Float, GLOBAL area 0, CONTINUOUS, m/s → km/h ×3.6 | [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED) |
| 권한 | `android.car.permission.CAR_POWERTRAIN` normal, `android.car.permission.CAR_SPEED` dangerous. Pleos 이미지의 `pm list permissions -f -g`에서도 동일 | [AOSP Car manifest](https://android.googlesource.com/platform/packages/services/Car/+/master/service/AndroidManifest.xml) |
| 구독 | `registerCallback`, `unregisterCallback`, `SENSOR_RATE_ONCHANGE`, `SENSOR_RATE_NORMAL`, `getProperty` | [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager) |
| 상태/오류 | `CarPropertyValue.STATUS_AVAILABLE`만 반영, `CarPropertyEventCallback.onChangeEvent/onErrorEvent` | [callback](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager.CarPropertyEventCallback), [value](https://developer.android.com/reference/android/car/hardware/CarPropertyValue) |
| VHAL 경로 | 표준 속성을 우선 사용하고 vendor 속성으로 복제하지 않음 | [VHAL special properties](https://source.android.com/docs/automotive/vhal/special-properties) |
| 템플릿 앱 | CAL은 UI/운전자 주의 분산 제약을 담당. 본 앱의 AAOS 서비스에서 실제 Car API 접근 성공. 다른 호스트/실차 허용까지 일반화하지 않음 | [CAL](https://developer.android.com/training/cars/apps/library) |
| 콘솔 | 최소 v2.1.0. `adb forward tcp:8088 tcp:8088` 후 로컬 콘솔의 P/D/N 및 ACCEL 사용 | [설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [조작](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide) |

컴파일 SDK 37.0의 optional/android.car.jar를 `useLibrary("android.car")`로 사용한다. Manifest uses-library는 required=false. Android 14 SDK android.car.jar를 javap으로 확인했으며 registerCallback/getProperty/unregisterCallback이 있고 subscribePropertyEvents는 없다. 최신 문서에서는 registerCallback이 deprecated지만 API 34 호환성과 동일 속도의 주기 수신을 위해 기존 API를 선택했다. 의존성 버전 변경 없음.

PERF_VEHICLE_SPEED는 공식적으로 후진 시 음수일 수 있다. 이번 요구사항대로 음수를 절댓값으로 바꾸지 않고 null→UNKNOWN으로 차단한다. 실제 후진 상태의 DRIVING 표시 보장은 별도 검토 대상이다.

## 구현과 신선도

- `AaosVehicleStateProvider`/`VehicleApplication`과 android.car 참조는 automotive에만 존재한다. shared에는 `VehicleSignalFactory`/`ManagedVehicleStateProvider` 주입 계약만 추가했다. mobile은 공급자를 생성하지 않는다.
- 기본 MOCK 유지. REAL 실패 시 자동 Mock 대체 없음. source는 VEHICLE_SDK라는 기존 enum을 재사용하지만 화면에는 `AAOS 차량 API`라고 표시한다.
- 기어 매핑과 속도 변환은 Android 클래스 없는 Kotlin 함수로 분리했다. NaN/음수/무한대/변환 overflow는 null.
- 상태 판정은 기존 `VehicleStateResolver`를 재사용한다. N·속도>3만 DRIVING으로 보완하고, N≤3은 UNKNOWN이다.
- 마지막 속도 수신 시각에 `SystemClock.elapsedRealtime()`을 사용한다. 3초 만료 Handler 알림을 매번 재예약하고 stop 시 취소한다. 기어는 연결 중 마지막 값을 유지하며 초기 값이 없으면 getProperty로 한 번 읽는다.
- 메인 Handler로 콜백 상태를 반영한다. 세션 start 연결/구독, stop 구독 해제 및 UNKNOWN, destroy disconnect. 이전 구독 콜백은 식별자로 제외한다.
- 서비스 단절/권한 오류/초기화 오류는 UNKNOWN 사유로 변환한다. 권한 요청은 차량 모드 하위 메뉴의 ParkedOnly 행이며, UNKNOWN에서 권한을 복구할 수 있도록 주문용 PARKED 판정과 분리했다.
- 권한 다이얼로그는 이 이미지에서 “위치 정보” 그룹으로 표시된다. 요청한 권한은 CAR_SPEED이며 이 변경으로 GPS 권한을 함께 요청하지 않는다.

## 검증 1~7

| # | 항목 | 결과 |
|---|---|---|
| 1 | `./gradlew test :automotive:assembleDebug :mobile:assembleDebug` | 통과. 85개 = shared 79 + automotive 5 + mobile 1 |
| 2 | Mock 시나리오 1~5 | 통과. UNKNOWN→PARKED, A/아메리카노1/ETA8 추천, DRIVING 매장 차단, 결제 실패→재시도, 준비→픽업 준비→완료 |
| 3 | REAL 콘솔/권한 | 신호/상태 전환 확인, 호스트 버튼 비활성화 실패. 아래 표 참조 |
| 4 | 미서명 `:automotive:assembleRelease` | 통과, automotive-release-unsigned.apk 생성. 사용자 서명 release 실행은 미실행(서명 작업을 요청하지 않음). debug 검증 전체 오류 패턴 0건 |
| 5 | mobile 설치/실행 | Medium_Phone_API_37.0에서 MainActivity 시작 및 Hello Android 화면 확인, 오류 패턴 0건. Android Auto 호스트 세션은 미실행 |
| 6 | diff/check/비밀값 | git diff --check 통과. 변경 파일과 추가 텍스트 검토, 비밀값 없음. 금지 대상/README/apk 변경 없음 |
| 7 | 제출 APK 복구 | 통과. debug 삭제→기존 제출 APK 설치→홈/Mock PARKED 전환→홈 복귀 확인. 복구 후 오류 패턴 0건 |

기준 브랜치의 실제 테스트는 80개다(shared 78 = DriveFlow 20 + TMAP 13 + VehicleState 29 + RecommendationUx 16, automotive/mobile 각1). 요청서의 78개와 차이는 추천 테스트가 14개가 아닌 16개이기 때문이다. 신규 5개는 N 주행 경계 1개와 변환 4개다. JUnit XML의 tests/failures/errors로 집계했다.

오류 검색: `FATAL|IllegalAccess|ClassNotFound|NoSuchMethod|NoClassDefFound`. 서명 release 실행 검증과 debug 결과를 혼동하지 않는다.

## REAL 화면별 관찰

| 콘솔 조건 | 앱 상태 | 주문 조작 | 호스트 정차 전용 행 | 반영 시간 |
|---|---|---|---|---|
| P·0 | PARKED | 매장 선택→메뉴 진입 가능 | 클릭 가능 | 수신 확인, 정밀 계측 미실행 |
| D·0 | DRIVING | 매장 선택 시 제한 토스트 | 클릭 전달 후 앱 정책이 차단 | 수신 확인, 정밀 계측 미실행 |
| D·30 | DRIVING | 앱 정책상 차단; 30 정확한 순간의 주문 탭은 미실행 | 서비스 상태 진입 가능: 비활성화 기대 불충족 | 연속 캡처에서 상태 확인, 정밀 계측 미실행 |
| N·30 | DRIVING | 앱 정책상 차단; 해당 순간 주문 탭은 미실행 | 비활성화 별도 미확인 | 연속 캡처에서 상태 확인, 정밀 계측 미실행 |
| P·0 복귀 | PARKED | 매장 선택→메뉴 진입 재확인 | 클릭 가능 | 복귀 확인, 정밀 계측 미실행 |
| 권한 거부 | UNKNOWN · 권한 필요 | 판정상 차단 | 호스트 P에서 재요청 가능 | 거부 후 화면 확인, 정밀 계측 미실행 |

30km/h는 콘솔 ACCEL 후 자연 감속 구간의 **에뮬레이터 계기판 표시값**이다. 30 고정 속도 주입이나 정확한 부동소수점 원시 속도 계측은 아니다. 캡처는 [validation-aaos-vehicle](validation-aaos-vehicle/)에 저장했다. ADB 재연결로 콘솔 포워딩이 끊어진 경우 재연결했으며, 이를 앱 Car 서비스 연결 실패로 기록하지 않았다.

호스트 조사에서 CarDrivingStateService는 moving 전환을 기록했다. UxRestrictions 설정의 moving/idling은 모두 0x50이었고, 정차 전용 설정 행 클릭이 전달됐다. 원인 확정이나 호스트 설정 변경은 이번 범위에 포함하지 않았다.

## 남은 확인과 사용자 결정

- 제출 APK 갱신 여부 및 REAL 시연 사용 여부는 사용자 결정이다. 기존 제출 APK/Release는 변경하지 않는다.
- 호스트 정차 게이트 및 모드 전환 안전성 확인 전에는 실제 주행 환경용 완료 버전으로 취급하지 않는다.
- 신호 강제 단절/3초 만료의 기기 UI 재현, 정밀 전파 지연 계측, 사용자 서명 R8 실행은 미실행. 신선도/잘못된 값/연결 상태 판정은 기존 JVM 테스트가 포함한다.
- 실차, 다른 호스트, 후진 음수 속도, Android Auto 차량 호스트 세션은 미검증이다.

## 제출본 복구

기존 `apk/조성민_PleosDriveFlow_release.apk`를 다시 설치했다. SHA-256 `b2537264dc23e58711a883354ecb33793e83b823f16dede132ebb8a86dd13453`는 작업 전과 동일하다. 호스트 P·0, 앱 Mock PARKED 홈 화면으로 복구했으며 저장소의 제출 파일 내용은 변경하지 않았다.
