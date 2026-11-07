# Pleos DriveFlow 서비스 소개서

## 서비스 소개
차량의 예상 도착시간과 매장의 준비시간을 비교하여 주문 시점을 추천하고, 정차 상태에서 주문·모의 결제·픽업 안내를 제공하는 차량용 MVP입니다.

## 문제와 핵심 가치
드라이브스루 이용자는 도착 시점과 준비시간을 따로 판단해야 합니다. DriveFlow는 ETA와 준비시간에 안전 여유·최소 처리시간·예상 지연을 반영해 주문 시점을 제안합니다. ETA 8분, 준비 6분, 여유 1분이면 약 1분 후 주문을 추천합니다. 추천은 추정값입니다.

## 구현 기능
- 가상 매장 3곳, 커피 메뉴 4종, 수량·합계·주문 확인
- ETA 기반 지금/잠시 후/도착 후 주문 추천
- UNKNOWN/PARKED/DRIVING 정책 및 호스트 정차 전용 클릭 제한
- 모의 결제 실패·재시도, 주문번호, 취소, 픽업 상태와 텍스트 알림

## 기술 구성과 검증
Android Automotive OS, Kotlin, Android for Cars App Library 기반입니다. automotive/mobile/shared 모듈과 순수 Kotlin 도메인, Provider 및 Repository 인터페이스로 구성했습니다. Pleos Connect v2.1.0 API34 에뮬레이터에서 구동하며 2026-11-06 Kotlin 테스트 35개(shared 33 = DriveFlowTest 20 + TmapNavigationEtaProviderTest 13, automotive 1, mobile 1)와 Python 서버 테스트 12개를 통과했습니다.

## 데모 범위
차량 상태, ETA, 매장, 결제, 주문 상태는 Mock입니다. 실제 Pleos SDK 신호·매장 서버·금융 결제·음성은 연결하지 않았습니다. 주문 상태는 버튼으로 진행하고 세션 재생성 시 초기화됩니다. 결제정보를 수집하거나 실제 금액을 청구하지 않습니다. 실차·출시 심사 적합성은 미검증입니다.

## 향후 계획
Pleos SDK 버전·권한·지원 신호를 확인한 후 Provider를 교체하고, 호스트 인증 정책, 주문 영속화 및 서버 연동을 검토합니다.
