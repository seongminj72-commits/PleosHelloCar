# 가상 매장 내비 경로

정차 상태에서 ETA 소스를 내비로 선택한다. 가상 매장을 고른 뒤 메뉴 화면의 **합계 / 매장 안내 → 이 매장으로 안내 시작**을 누른다. 매장 선택만으로는 경로를 요청하지 않는다. 안내 화면은 메뉴의 여섯 행 제한을 지키기 위해 별도로 두었다. 안내 시작 행은 앱이 PARKED로 판단할 때 표시하며, 탭 시 차량 상태를 다시 확인한다.

내비가 돌려준 목적지 좌표가 선택한 매장 좌표에서 300m 미만이고 경로 안내가 활성 상태이면 내비 ETA를 사용한다. 300m 이상 떨어져 있으면 **내비 목적지가 선택한 매장과 다릅니다**로 표시하고 해당 ETA를 추천에서 제외한다. 내비 경로를 요청할 때 매장 이름과 가상 매장 좌표를 전달한다. Coffee A는 기존 좌표로 서명 release 경로 검증을 통과했다. Coffee B도 기존 좌표로 통과했다. Coffee C는 영업 종료 상태라 경로 생성은 미실행이다.

## SDK 근거

- [Pleos Destinations 가이드](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations)의 `requestRoute(RouteInfo)`, `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError`와 프로브의 요청 예시(`claude/pleos-sdk-probe`, `843d96c`)를 참고했다.
- [Pleos 권한 가이드](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions)에 따라 Automotive 매니페스트에 `pleos.car.permission.NAVI_ROUTE_SEARCH`를 추가했다.
- Maven SDK 소스의 `NaviHelperEventListener.kt`, `RouteInfo.kt`, `DestinationInfo.kt`에서 콜백 시그니처와 좌표 필드를 확인했다. 가상 매장에는 등록된 지도 POI 식별자가 없으므로 식별자와 주소에 빈 문자열을 전달한다. Coffee A 경로에서 Navigation이 이 입력을 수용했다.

## 검증

| 검증 | 결과 |
|---|---|
| `./gradlew test :automotive:assembleRelease` | 성공. JUnit XML 96개 통과(shared 85, automotive 10, mobile 1). 미서명 R8 release 빌드 성공. |
| 좌표만으로 경로 생성 | 통과. Coffee A 좌표 `37.5665, 126.9780`과 빈 `poiId`·`address`로 요청했다. SDK 목적지 콜백 좌표가 일치했고 `remainTime=3240`초였다. 앱 메뉴에는 Coffee A까지 54분과 47분 후 주문 추천이 표시됐다. [Navigation 경로](validation-navi-store-route/02-navigation.png) · [매장명 추천](validation-navi-store-route/04-store-eta.png) |
| 매장 안내 상태 자동 변경 | 부분 확인. DriveFlow 로그에서 요청 중을 확인했다. Navigation이 약 0.7초 후 전면에 표시되면서 DriveFlow는 연결을 해제했다. 경로 탐색과 진행은 Navigation 화면에서 관측했고, 돌아오면 같은 매장 안내 화면에 ETA 54분이 자동 표시됐다. 화면에 머문 채 중간 상태 전환은 미확인이다. 화면은 이미 ETA 리스너에서 `invalidate()`를 호출한다. [복귀 화면](validation-navi-store-route/03-status.png) |
| 탐색 중 중복 탭 | 통과. 안내 시작을 빠르게 두 번 탭하자 `매장 경로를 탐색 중입니다.` 토스트가 표시됐다. DriveFlow발 SDK 요청은 로그에서 1건이었고 크래시는 없었다. [녹화 프레임](validation-navi-store-route/01-duplicate-request.png) |
| 내비 연결 전 탭 | 미실행. 정상 연결이 화면 조작 전에 완료됐고 AVD 셸이 내비 서비스 컴포넌트의 일시 비활성화를 거부했다. 코드는 연결 전 요청을 검사하고 `내비 연결을 확인하세요.` 예외 메시지를 화면 공통 탭 처리에서 토스트로 변환한다. |
| 다른 목적지로 변경 | 통과. Navigation에서 다른 목적지(`37.42908888, 127.13363333`)를 설정한 뒤 DriveFlow에서 `내비 목적지가 선택한 매장과 다릅니다`와 도착 후 주문 추천을 확인했다. [불일치 화면](validation-navi-store-route/05-mismatch.png) |

2026-11-17 Pleos Connect v2.1.0(API 34)에서 서명 검증 및 업데이트 설치를 완료했다. 13:52:47에 DriveFlow가 요청을 전달했고, 13:52:55에 호스트 로그에서 Coffee A `onRouteStarted` 이벤트가 확인됐다. 13:53:16에 일치 좌표와 3,240초를 수신했고, 13:53:51에 목적지 불일치를 기록했다. 이 시험에서 DriveFlow의 `onError` 및 크래시는 관측되지 않았다. Coffee A의 좌표는 유지했다.

### 빈 장바구니 보완

메뉴 선택 화면의 주문 추천 행은 비어 있는 장바구니에 `메뉴를 선택하면 주문 시점을 추천합니다`를 표시한다. ETA 상태 문구는 유지한다. 컨트롤러 테스트에서 빈 장바구니로 시작할 때와 마지막 메뉴를 제거했을 때 ETA를 조회하지 않고 안내 문구를 반환함을 확인했다. `./gradlew test` 97개 통과.

### 빈 장바구니 보완 후 서명 release 재검증

| 항목 | 결과 |
|---|---|
| Coffee B 좌표 경로 | 통과. 빈 `poiId`·`address`로 요청했고, 콜백에서 기존 좌표 `37.5700, 126.9830`과 `remainTime=2700`초를 확인했다. 아메리카노 1개를 고르자 45분 ETA와 39분 후 주문 추천이 표시됐다. [추천 화면](validation-navi-store-route/09-coffee-b-recommendation.png) |
| 빈 장바구니 | 통과. 같은 Coffee B 경로에서 45분 ETA와 `메뉴를 선택하면 주문 시점을 추천합니다`가 함께 표시됐다. [빈 장바구니 화면](validation-navi-store-route/08-coffee-b-empty-eta.png) |
| Coffee C 경로 | 미실행. 영업 종료로 표시되며, 선택 시 `현재 주문할 수 없는 매장입니다.`가 표시돼 안내 화면에 진입할 수 없었다. [영업 종료 화면](validation-navi-store-route/10-coffee-c-closed.png) |

Pleos Connect v2.1.0에서 APK 서명 검증과 업데이트 설치 성공. 시험 로그에서 FATAL EXCEPTION, OverLimitFlowViolation, ClassCastException, IllegalAccessError 검색 결과 0건.
