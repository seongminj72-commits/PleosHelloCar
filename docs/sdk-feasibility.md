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
