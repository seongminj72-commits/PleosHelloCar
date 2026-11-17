# 가상 매장 내비 경로

정차 상태에서 ETA 소스를 내비로 선택한다. 가상 매장을 고른 뒤 메뉴 화면의 **합계 / 매장 안내 → 이 매장으로 안내 시작**을 누른다. 매장 선택만으로는 경로를 요청하지 않는다. 안내 화면은 메뉴의 여섯 행 제한을 지키기 위해 별도로 두었다. 안내 시작 행은 앱이 PARKED로 판단할 때 표시하며, 탭 시 차량 상태를 다시 확인한다.

내비가 돌려준 목적지 좌표가 선택한 매장 좌표에서 300m 미만이고 경로 안내가 활성 상태이면 내비 ETA를 사용한다. 300m 이상 떨어져 있으면 **내비 목적지가 선택한 매장과 다릅니다**로 표시하고 해당 ETA를 추천에서 제외한다. 내비 경로를 요청할 때 매장 이름과 가상 매장 좌표를 전달한다. 좌표는 기존 값을 사용하며 실제 경로 탐색은 서명 release로 확인한다.

## SDK 근거

- [Pleos Destinations 가이드](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations)의 `requestRoute(RouteInfo)`, `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError`와 프로브의 요청 예시(`claude/pleos-sdk-probe`, `843d96c`)를 참고했다.
- [Pleos 권한 가이드](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions)에 따라 Automotive 매니페스트에 `pleos.car.permission.NAVI_ROUTE_SEARCH`를 추가했다.
- Maven SDK 소스의 `NaviHelperEventListener.kt`, `RouteInfo.kt`, `DestinationInfo.kt`에서 콜백 시그니처와 좌표 필드를 확인했다. 가상 매장에는 등록된 지도 POI 식별자가 없으므로 식별자와 주소에 빈 문자열을 전달한다. 이 입력을 Navigation 앱이 수용하는지는 서명 release 시험 항목이다.

## 검증

| 검증 | 결과 |
|---|---|
| `./gradlew test :automotive:assembleRelease` | 성공. JUnit XML 96개 통과(shared 85, automotive 10, mobile 1). 미서명 R8 release 빌드 성공. |
| 서명 release에서 매장 선택 → 안내 시작 → 경로 생성 → 매장명 추천 | 미실행. 사용자 서명 APK 대기 중. |
| Navigation에서 다른 목적지로 변경 → 불일치 표시 및 ETA 제외 | 미실행. 사용자 서명 APK 대기 중. |
