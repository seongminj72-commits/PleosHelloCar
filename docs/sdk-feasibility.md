# SDK 및 UI 구현 가능성 조사

조사일: 2026-10-29. 로컬 Gradle 의존성과 캐시에서 앱용 Pleos SDK를 확인하지 못했다. API 34 Pleos 시스템 이미지와 AVD는 존재한다. 아래는 공식 문서에서 확인한 기능이며 이 앱에 실제 연결했다는 의미가 아니다.

| 기능 | 문서상 API/의존성 | 권한 및 실행 검증 | MVP |
|---|---|---|---|
| 차량 상태 | Vehicle 의존성 예시 2.3.2. 기어/속도용 정확한 클래스·메서드 미확인 | 기능별 권한·capability와 서비스 버전 확인 필요. 실제 신호 수신 미검증 | MockVehicleStateProvider |
| 내비게이션 | NaviHelper 3.0.5: initialize, addListener, removeListener, release | 구버전 설정 문서는 NAVI_ROUTE 등 권한 예시 제공. ETA 정확 필드/권한·현재 AVD 호환성 미확인 | MockNavigationEtaProvider |
| 위치 | FusedLocation 1.1.2: isSupported, initialize, getLastKnownLocation, registerFusedLocationCallback, release | pleos.car.permission.FUSED_LOCATION. null/실패 처리 필요. 위치 수신 미검증 | 위치 기능 미구현 |
| 음성 | TextToSpeech 3.3.1: ai.pleos.playground.tts.TextToSpeech, initialize, speak, release | pleos.car.permission.TTS_SERVICE. 모드·등록·엔진 지원 확인 필요. 음성 출력 미검증 | 텍스트 알림 대체 |

SDK Maven 저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`이다. 문서의 SDK 버전과 설치된 Pleos Connect v2.1.0의 호환성을 버전 이름만으로 추정하지 않는다. SDK 다운로드 실패를 확인한 것은 아니며, 정확한 신호 API·권한·호환성을 확인하지 못한 범위를 Mock으로 격리했다. 확인되지 않은 Pleos 메서드는 코드에 작성하지 않았다.

Virtual Console 공식 가이드는 Motion의 Gear Selection(P/R/N/D)을 설명한다. 이 앱의 Mock 설정은 Virtual Console 신호와 연결되지 않는다. 내비게이션 ETA·위치·TTS의 콘솔 재현 가능 여부는 미검증이다.

## Car App Library

기존 POI 서비스 카테고리를 유지하고 List/Pane/Message 템플릿으로 제한했다. 일반 쇼핑·장바구니·결제 UI가 POI 카테고리에서 자동으로 승인된다고 볼 수 없다. 공식 구매 예시는 간단한 기존 결제수단 흐름이며 음식 주문의 포괄적 허용 근거가 아니다. 메뉴·수량·모의 결제는 정차 전용 데모로 구현했고, 출시 전 Pleos 심사 및 호스트의 허용 범위 확인이 필요하다. 앱 자체 행 상한을 SDK 전역 한도로 설명하지 않는다.

## 공식 근거

- [SDK 개요](https://document.pleos.ai/en/api-reference/connect-sdk-pleos)
- [Vehicle 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [Vehicle 샘플과 서비스 호환성](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
- [NaviHelper 소개](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro)
- [NaviHelper 의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [구버전 NaviHelper 권한 예시](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/project-setup)
- [FusedLocation 소개 및 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [TTS 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [TTS 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions)
- [Virtual Console](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)
- [Car App Library](https://developer.android.com/training/cars/apps/library)
- [구매 흐름 예시](https://developers.google.com/cars/design/create-apps/sample-flows/purchase-with-existing-method)

실제 PG 결제는 본 MVP 요구 범위에서 제외한다. 배포 단계에서는 개발용 ALLOW_ALL_HOSTS_VALIDATOR 교체와 호스트 인증 정책 검증이 필요하다.


## v0.3.0 추가 조사 — 2026-10-30

| 항목 | 확인 결과 | 이번 버전 판단 |
|---|---|---|
| NaviHelper 역할 | 차량 Navigation 서비스에 목적지·경유지·경로 요청, 정보 조회, 이벤트 수신 | 현재 차량 경로와 결합하려면 향후 별도 어댑터 필요 |
| 공식 SDK | `ai.pleos.playground:NaviHelper:3.0.5` | 실제 의존성으로 추가하지 않음 |
| 권한 | `NAVI_ROUTE`: get*Info/requestTrip, `NAVI_ROUTE_SEARCH`: requestRoute 등, `NAVI_CUSTOM_MAP`: requestNavigationScreen | 개별 메서드 @RequiresPermission 확인 필요 |
| 실제 AVD | Pleos_Connect_v2.1.0, Android API 34 | 새 APK 설치·실행 확인 |
| 설치 Navi 서비스 | `ai.pleos.playground.service.navi` versionName 3.0.4, versionCode 55, min31/target34 | SDK 3.0.5와 서비스 버전 번호는 서로 다른 구성요소. 호환/비호환을 번호만으로 판단하지 않음 |
| 설치 Navigation 앱 | `ai.umos.maps.android.navigation.app` 3.3.2-rc5-hotfix.3 | NaviHelper 초기화·이벤트/ETA 필드 수신은 미검증 |
| SDK 이용 상태 | 공식 프로젝트 생성 가이드: Vehicle State/NaviHelper Ready to Use, FusedLocation/TTS/STT/Vehicle Control Not Available Yet | 개별 프로젝트 포털 상태와 문서 시점에 따라 재확인 |
| 위치 구현 | Android LocationManager.getCurrentLocation, API 31 overload (min34에 포함), FINE/COARSE 런타임 권한 | Pleos FusedLocation API를 임의 작성하지 않고 표준 API 사용 |
| TMAP REST | 서버 기반 자동차 경로 계산, appKey 인증 | NaviHelper에 의존하지 않음. 차량의 활성 안내 경로와 동일하다는 보장 없음 |

에뮬레이터 2.1.0부터 CRN 불필요라는 공식 안내는 확인했으나 모든 SDK와 서비스 조합의 호환 보증으로 해석하지 않는다. 현재 공식 문서에서 SDK 3.0.5 ↔ AVD 2.1.0의 명시적 호환표를 확인하지 못했다. 실제 SDK 연동 승인 전 초기화·서비스 연결·권한·정확한 ETA 필드·리스너·해제를 해당 AVD에서 검증해야 한다.

추가 근거:
- [NaviHelper 의존성 3.0.5](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [현재 권한 선언](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [SDK 사용 신청/CRN/이용 상태](https://document.pleos.ai/en/docs/pleos-only/create-my-project)
- [Android LocationManager](https://developer.android.com/reference/android/location/LocationManager)
- [CarContext 권한 요청](https://developer.android.com/reference/androidx/car/app/CarContext)
- [TMAP 구현과 실행 방법](tmap-integration.md)
