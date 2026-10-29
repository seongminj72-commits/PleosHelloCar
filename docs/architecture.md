# DriveFlow 아키텍처

## 모듈과 데이터 흐름

- automotive: Automotive Manifest, CarAppActivity 진입점, 표시 이름.
- shared: CarAppService → MyCarAppSession → HomeScreen. Session이 Controller와 Mock 구현을 생성한다.
- mobile: 기존 Compose 기본 화면 및 projected 구성 유지. DriveFlow 모바일 전용 UX는 구현하지 않았다.

화면 → DriveFlowController → VehicleStateProvider 안전 검사 → StoreRepository/OrderRepository → PaymentSimulator → 화면 갱신 순서로 동작한다. NavigationEtaProvider는 독립적인 OrderTimingCalculator의 입력을 제공한다. Screen은 Car App Library 템플릿만 구성하고 도메인 계산과 상태 전이는 순수 Kotlin으로 분리한다.

## 주문 상태

ORDER_CREATED → PAYMENT_SIMULATED → PREPARING → READY_FOR_PICKUP → COMPLETED.
완료 전 각 상태에서 CANCELLED로 전환할 수 있다. 종료 상태는 변경 불가다. 요청 ID가 같은 재시도는 같은 주문을 반환하고, 활성 주문이 있으면 새 주문을 차단한다. 결제 실패는 ORDER_CREATED를 유지한다. 상품별 수량은 0~3개이며 0개 항목은 주문에서 제외한다.

## 추천 계산

필요시간 = max(준비시간, 최소 처리시간) + 안전 여유 + 예상 지연.
ETA가 최소 처리시간 이하이거나 입력이 유효하지 않으면 도착 후 주문을 권장한다. 그 외 ETA − 필요시간이 양수이면 해당 시간 후, 아니면 지금 주문을 권장한다. 모두 추정이며 자동 주문을 실행하지 않는다.

## SDK 경계와 수명주기

VehicleStateProvider, NavigationEtaProvider, PaymentSimulator, StoreRepository, OrderRepository는 생성자 주입한다. 현재 구현은 전부 Mock이다. DemoSettingsScreen은 Mock으로 직접 형변환하므로 실제 Provider 도입 시 설정 화면 분리도 필요하다. SDK 구현체에는 권한·연결·콜백·오래된 데이터 처리·해제 정책이 추가로 필요하다.

## 저장 및 안전

주문은 세션 메모리에만 저장하며 앱 재시작 복원·서버 동기화는 없다. 금융정보·인증정보를 수집하지 않는다. UNKNOWN 기본값과 도메인 PARKED 검사, 호스트의 ParkedOnlyOnClickListener를 함께 사용한다. Mock PARKED는 실제 안전 신호가 아니다. 기존 MyCarAppScreen Hello World 파일과 v0.1.0 태그를 보존한다.
