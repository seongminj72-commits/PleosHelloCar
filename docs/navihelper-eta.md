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
| 서명 release 설치 및 활성 목적지 ETA 반영 | 미실행. 사용자 서명 APK 대기 중. |
| ETA 변경에 따른 주문 추천 갱신 | 미실행. 사용자 서명 APK 대기 중. |
| 목적지 취소 후 경로 없음 상태 | 미실행. 사용자 서명 APK 대기 중. |
| release 크래시 및 keep 규칙 전후 비교 | 미실행. 실제 앱 release에서 크래시가 재현될 때 비교한다. |

서명 APK에서 확인할 때는 Navigation 앱의 안내를 시작한 뒤 내비 모드를 선택하고, 매장과 메뉴를 골라 내비 ETA 및 주문 추천을 확인한다. 목적지를 더 가까운 곳으로 바꾸고 ETA 및 추천이 갱신되는지 확인한 뒤 안내를 취소한다. 각 단계의 화면을 `docs/validation-navihelper/`에 기록한다.
