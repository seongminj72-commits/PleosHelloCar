# Pleos NaviHelper ETA

Automotive의 **서비스 상태 / 데모 설정 → ETA 연결 설정 → ETA 모드**에서 정차 상태로 Mock, TMAP, 내비를 순환한다. 내비 모드는 Navigation 앱의 활성 목적지까지 남은 시간을 사용한다. DriveFlow에서 고른 가상 매장과 내비 목적지가 자동으로 연결되지는 않는다. 내비에서 시연 목적지를 직접 설정한다.

## SDK 근거

- 공식 Maven `NaviHelper-3.0.5-sources.jar`의 `NaviHelper.kt`, `NaviHelperEventListener.kt`에서 초기화·종료, 리스너 등록·해제, `getRouteStateInfo()`, `getDestinationInfo()`, 대응 콜백의 시그니처를 확인했다.
- `navi-data-3.0.4-sources.jar`의 `DestinationInfo.kt`는 `remainTime`을 초 단위 남은 시간으로 정의한다. Pleos Connect v2.1.0 프로브에서 내비 표시 1시간 30분과 `remainTime=5400`이 일치했다. `RouteStateInfo.naviMode`는 활성 안내에서 `ROUTE_GUIDING`, 목적지가 없는 상태에서 `SAFE_GUIDING`이었다.
- 앱 세션이 보이는 동안 직접 구현한 리스너를 등록한다. 15초마다 경로 상태와 목적지 ETA를 조회하고 Binder 이벤트를 메인 스레드에서 처리한다. 마지막 ETA는 60초 뒤 만료한다. 소스 전환·세션 중단 시 리스너와 연결을 해제한다.

## 배포 후보 검증

| 검증 | 결과 |
|---|---|
| `./gradlew test :automotive:assembleRelease` | 성공. JUnit XML 94개(기존 91 + 신규 3, shared 85 / automotive 8 / mobile 1). 서명 속성 없이 R8 release 빌드. |
| 서명 release 설치 및 활성 목적지 ETA 반영 | 통과. Pleos Connect v2.1.0(API 34), P·0에서 `apksigner verify` 및 기존 앱 위 업데이트 설치 성공. [메뉴에서 ETA 80분·73분 후 주문 추천 확인](validation-navihelper/01-active-eta.png). |
| ETA 변경에 따른 주문 추천 갱신 | 통과. Navigation에서 더 가까운 목적지로 안내를 시작하고 같은 메뉴 화면으로 돌아오자 아메리카노 1개가 유지된 상태에서 [ETA 19분·12분 후 주문 추천](validation-navihelper/02-changed-eta.png)으로 갱신. |
| 목적지 취소 후 경로 없음 상태 | 통과. 안내 종료 후 [경로 없음·도착 후 주문 추천](validation-navihelper/03-no-route.png) 표시. |
| R8 release 실행 | 시험 구간에서 크래시 없음. `FATAL EXCEPTION\|OverLimitFlowViolation\|ClassCastException\|IllegalAccessError\|NoSuchMethodError\|NoClassDefFoundError` 로그 검색 0건. Pleos keep 규칙 비교는 크래시가 재현되지 않아 미실행. |

2026-11-17 시험에서 `DriveFlowETA` 로그는 10:08:07에 80분, 10:09:14에 19분, 10:09:32에 경로 없음을 남겼다. ETA 변경과 취소는 Navigation 앱에서 조작한 뒤 DriveFlow로 돌아오는 흐름으로 확인했다. 이 시험에서는 주행 중 ETA 변화와 런타임 만료·연결 실패를 유도하지 않았다.
