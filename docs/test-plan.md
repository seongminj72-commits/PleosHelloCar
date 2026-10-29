# 테스트 계획과 결과

## 자동 검증

```bash
./gradlew :automotive:assembleDebug :mobile:assembleDebug :shared:testDebugUnitTest :automotive:testDebugUnitTest :mobile:testDebugUnitTest
```

DriveFlowTest의 20개 테스트는 ETA 범위·누락·최소 처리시간·지연, UNKNOWN/DRIVING 조작 차단, 수량과 합계, 주문 불가 매장, 결제 실패 재시도, 중복 방지, 전체 상태 전이 조합과 위조 메뉴 거부를 검사한다. automotive/mobile 기본 테스트는 각 1개다. 2026-10-29 재확인 결과 총 22개 통과. XML 결과는 각 모듈 `build/test-results/testDebugUnitTest/`에 생성된다.

## 수동 시나리오

| 시나리오 | 절차/기대 결과 | 확인 범위 |
|---|---|---|
| A 정상 주문 | 실제 호스트 정차 + Mock PARKED → 매장 A → 메뉴 1개 → 확인 → 모의 결제 → 홈 → 상태 진행 | 이전 직접 화면 검증으로 픽업 준비까지 확인 |
| B 주행 제한 | Mock DRIVING/UNKNOWN에서 주문 변경 거부, PARKED 복귀 후 허용 | 도메인 자동 검증, DRIVING 매장 선택 차단 화면 확인 |
| C ETA 변경 | ETA 10/준비5/여유1 → 4분 후, ETA3 → NOW | 도메인 자동 검증. 모든 변경 조합 화면 재검증은 미완료 |
| D 예외 | ETA 없음, 매장 C 불가, 0개, 취소, 결제 실패·재시도, 중복 주문 | 자동 검증. 결제 실패·재시도는 화면도 확인 |
| E 메시지 길이 | 완료 화면에서 주문번호와 데모 결제 안내 두 줄 모두 표시 | 수정본 수동 재검증 필요 |
| F 세션 재생성 | 재시작 시 UNKNOWN·주문 없음으로 초기화 | 설계상 한계. 영속화 미구현 |

설치 성공, Activity 실행, 실제 템플릿 렌더링, 전체 예외 흐름 검증을 각각 구분한다. 에뮬레이터 시간은 호스트와 다를 수 있다. 실차·실제 결제·실제 SDK 검증은 수행하지 않았다.
