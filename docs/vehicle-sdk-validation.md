# 차량 상태 기반 작업 및 Vehicle SDK 조사

작업일: 2026-11-08~09(KST). 기준 main `2aab241`, 제출 태그 v0.3.1. 브랜치 `codex/vehicle-state-sdk`.

## 결과

2단계 **중단**: 공식 Vehicle SDK 문서에서 기어·차속을 읽는 정확한 API를 특정하지 못했다. 공식 SDK API 정책은 속도·기어를 AAOS Car API 대상이라고 안내한다. 사용자 지정 중단 조건에 따라 의존성·권한·SDK 어댑터를 추가하지 않았다. 실제 신호 수신 성공으로 표현하지 않는다.

실제로 사용한 Pleos SDK 클래스·메서드·권한: **없음**. SDK 조사 시작은 2026-11-08 23:49 KST이며 같은 날 중단 조건을 확인했다. 하루 시한을 채우기 위한 불필요한 탐색은 진행하지 않았다.

## 공식 조사 근거

| 항목 | 확인 결과 | 근거 |
|---|---|---|
| 의존성 | 문서 좌표 `ai.pleos.playground:Vehicle:2.3.2` | [프로젝트 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) |
| 저장소·인증 | `https://nexus-playground.pleos.ai/repository/maven-releases/`. 문서 예시에 인증 설정 없음. 해당 2.3.2 POM은 무인증 HTTP 200 확인. 전체 Gradle 의존성 해석은 미실행 | 같은 설정 문서 |
| API 분류 | 속도·기어 등 표준 데이터는 AAOS Car API, 제조사 특화 기능은 Pleos SDK로 구분 | [SDK API 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api) |
| 기어·차속 | Vehicle API 및 listener 목록에서 필요한 getter·콜백을 특정하지 못함. 단위·값 범위·개별 권한·콜백 스레드 역시 미확인 | [API 목록](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/entry), [listener 목록](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/entry) |
| 표준 속도 대안 | 공식 FAQ는 계기판과 같은 속도에 `CarPropertyManager`의 `PERF_VEHICLE_SPEED` 사용을 안내함. 이번 범위에서는 구현하지 않음 | [플랫폼 FAQ](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq) |
| 일반 생명주기 | `Vehicle(context)`, `initialize()`, 도메인 조회, listener 등록·해제, `release()`. 초기화 반환만으로 서비스 연결을 단정할 수 없음 | [도메인 객체](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object), [샘플](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app) |
| 일반 콜백 | 등록한 값이 변경될 때 알림. 기어·속도 개별 계약과 스레드는 확인되지 않음 | [Callback](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/callback) |
| 권한 | API별 `@RequiresPermission` 확인 및 manifest 선언 필요. 일반 샘플의 권한 목록을 복사하지 않음. 기어·속도용 권한은 미확정 | [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission) |
| AVD·서비스 | 공식 샘플 기준 SDK 2.3.2 / Service 2.3.22 / Pleos_Connect_v2.1 Android 14. 로컬 AVD v2.1.0 API34에 `ai.pleos.playground.service.vehicle` 2.3.22(versionCode 74) 설치 확인. 이는 개별 API 지원/최소 버전 보증이 아님 | [샘플 환경](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app), [호환성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) |

## 1단계 구현과 판정 규칙

`VehicleStateResolver`는 Android에 의존하지 않는 Kotlin 판정기다. 앱 자체 Gear/Connection enum과 속도(km/h), 수신 시각 및 현재 시각(ms)을 받는다. 시각은 동일한 단조 증가 시계를 사용해야 한다.

우선순위:
1. 연결 해제·권한 거부·초기화 실패 → UNKNOWN 및 사유.
2. 시각 없음/음수/미래, 만료, 속도 없음/음수/NaN/무한대 → UNKNOWN.
3. P 기어는 속도 ≤1.0일 때만 PARKED, 그보다 빠르면 상충 UNKNOWN.
4. N은 UNKNOWN. D/R은 DRIVING. 그 외 기어는 속도 >3.0일 때 DRIVING, 나머지는 UNKNOWN.

기본 신선도는 3,000ms이며 정확히 3,000ms가 되면 만료한다. 생성자로 변경 가능하다. **현재 이 판정기는 실제 SDK에 연결되지 않은 기반 로직**이다. 일반 SDK 문서가 변경 시 콜백임은 확인했으나 기어·속도 API와 연결 확인 방식이 불명확하므로, 실제 어댑터에서 시간 만료 또는 연결 유지 방식 중 하나를 확정·적용하지 않았다. 후속 구현에서 변경 시 알림만 제공한다면 연결 유지 확인과 마지막 값을 사용하고, getter가 제공되면 1초 재조회로 보강해야 한다. 타이머만으로 수신 시각을 갱신하거나 한 신호의 갱신으로 다른 오래된 신호를 신선하게 취급하면 안 된다.

## Provider와 화면

- 인터페이스 기본 no-op listener/source/reason으로 기존 구현 호환 유지. Mock setter는 값이 바뀔 때 알림.
- Switchable 기본 MOCK, 명시적 REAL 전환 시 Provider 부재·예외·잘못된 출처는 UNKNOWN. Mock으로 자동 대체하지 않음.
- 모드 및 활성 Provider 변경 알림, listener 해제와 세션 종료 정리.
- FlowScreen은 onStart/onStop에서 차량 listener 등록/해제, 변경 시 화면 무효화.
- REAL 행은 읽기 전용. Mock 순환 함수는 호출 시에도 현재 모드를 검사.
- 모드 전환은 호스트 정차 전용. REAL UNKNOWN에서 사용자가 명시적으로 Mock으로 복귀할 수 있도록 차량 도메인 PARKED 검사는 적용하지 않음. 주문 조작의 InteractionPolicy는 그대로 유지.
- 설정 6행 상한: 준비시간/지연은 하위 2행 화면으로 묶음. 기존 각각의 조정 기능 유지.
- 설정·결제의 안전 캐스트, 실제 차량 모드에서 준비시간·지연·실패 시뮬레이션 변경도 PARKED 검사.
- 실제 SDK가 없으므로 main의 차량 신호 미연동 설명과 README는 변경하지 않음.

## 검증

| 번호 | 결과 |
|---|---|
| 1 Gradle test + automotive/mobile assembleDebug | 통과. Kotlin 64개 = 기존 35개(수정 없음) + 신규 29개(Resolver 17, Switchable/도메인 차단 12). 실패·오류 0 |
| 2 Python unittest discover -s server | 12개 통과. 최초 sandbox 소켓 권한 제한으로 2개 오류 발생 후 권한 있는 환경에서 재실행하여 통과 |
| 3 Mock 시나리오 1~5 | 통과. 2026-11-09 화면별 결과는 아래 표 참조 |
| 4 실제 SDK/Virtual Console 신호 | 미실행 — 2단계 중단, 실제 어댑터 없음 |
| 5 실제 SDK release·서명·REAL 시작 | 미실행 — 2단계 중단. 제출 APK 재빌드·교체 안 함 |
| 6 diff·비밀값 | 통과. git diff --check, 보호 경로 변경 없음 확인, 추가 코드·문서에 비밀값 없음 |

## 남은 범위

실제 차량 API 단위·기어값·권한·capability·스레드·연결/해제·R8 동작은 미검증이다. 후속 작업으로 AAOS CarPropertyManager 경로를 채택할지 사용자가 결정해야 한다. 기본 Mock 변경이나 제출 APK 갱신은 이번 작업에 포함하지 않았다.


## 2026-11-09 화면별 검증 (debug)

사용자 승인 후 기존 설치 앱을 삭제하고 debug APK를 설치했다. AVD 재시작 및 간헐적인 ADB offline 때문에 실패한 조작은 현재 화면 확인 후 재실행했다. 호스트는 P·0km/h이며 DRIVING은 앱 Mock 상태를 사용했다. 실제 Vehicle 신호 검증과 구분한다.

| 시나리오 | 관찰 결과 | 증거 |
|---|---|---|
| 1 홈·PARKED | 기본 MOCK UNKNOWN, 서비스 상태에서 PARKED 전환 | [홈](validation-vehicle-sdk/home.png), [설정](validation-vehicle-sdk/settings.png) |
| 2 메뉴·추천 | 매장 A, 아메리카노 1개 3,500원. ETA8/준비6/여유1 → 약 1분 후 주문 | [메뉴](validation-vehicle-sdk/menu.png) |
| 3 DRIVING 차단 | Mock DRIVING 상태에서 매장 A 선택 시 정차 확인 필요 토스트, 메뉴 진입 차단. UNKNOWN을 거쳐 PARKED 복귀 | [DRIVING](validation-vehicle-sdk/driving.png), [차단](validation-vehicle-sdk/driving-blocked.png) |
| 4 모의 결제 | 실패 토스트 후 재시도 성공. 주문번호 DF-3A14D5F6·실제 청구 없음 | [실패](validation-vehicle-sdk/payment-failure.png), [성공](validation-vehicle-sdk/receipt.png) |
| 5 픽업 상태 | 같은 주문으로 상품 준비 중 → 픽업 준비 완료 → 픽업 완료 | [준비](validation-vehicle-sdk/preparing.png), [픽업 준비](validation-vehicle-sdk/ready.png), [완료](validation-vehicle-sdk/completed.png) |
| 추가: 설정 하위 화면 | 준비시간 5분·지연 2분 변경 확인, 상위 6행/하위 2행 | [설정 변경](validation-vehicle-sdk/timing-changed.png) |
| 추가: REAL 미연동 | UNKNOWN·차량 신호 없음·SDK 미연동 표시. 실제 상태 행은 읽기 전용. 매장 선택 차단 | [REAL 상태](validation-vehicle-sdk/real-final.png), [차단](validation-vehicle-sdk/real-blocked-final.png) |
| 추가: 명시적 Mock 복귀 | 모드 행을 직접 탭하여 Mock PARKED 복귀. 자동 대체 아님 | [복귀](validation-vehicle-sdk/mock-restored.png) |
| debug 오류 로그 | 앱 재시작 전 logcat 초기화 후 위 흐름의 `FATAL/IllegalAccess/ClassNotFound/NoSuchMethod` 일치 0건 | adb logcat -d 검사 |

제출 APK SHA-256은 `b2537264dc23e58711a883354ecb33793e83b823f16dede132ebb8a86dd13453`으로 작업 전후 동일하다. apk/, 기존 테스트, 주문 도메인, TMAP navigation/, server/, Gradle·Manifest·keep 규칙, README는 변경하지 않았다.
