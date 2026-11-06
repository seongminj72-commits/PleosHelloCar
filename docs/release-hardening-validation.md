# 제출용 릴리스 하드닝 1단계 검증

2026-11-06, 기준 main `0de8488`, 브랜치 `codex/release-hardening`.

| 번호 | 검증 | 결과 |
|---|---|---|
| 1 | `./gradlew test :automotive:assembleDebug :mobile:assembleDebug --rerun-tasks` | 통과. Kotlin 35개(shared 33 = DriveFlowTest 20 + TmapNavigationEtaProviderTest 13, automotive 1, mobile 1), 실패 0. Debug APK 2개 생성 |
| 2 | `python3 -m unittest discover -s server -v` | 통과. 12개, OK |
| 3 | 서명 속성 없이 `./gradlew :automotive:assembleRelease` | 통과. unsigned APK 생성. apksigner 검증 실패로 미서명 확인. 같은 명령 재실행 시 configuration cache 재사용 |
| 4 | 네 서명 속성 주입 후 release 빌드 및 `apksigner verify --print-certs` | 통과. 사용자 터미널에서 네 속성을 주입해 BUILD SUCCESSFUL 확인. apksigner verify --print-certs 재검증 통과(v2 서명) |
| 5 | 이번 브랜치의 서명 APK 설치 및 시나리오 1~5 | 업데이트 설치 성공. 시작 시 IllegalAccessError 재현, 홈 진입 실패 |
| 6 | release APK Manifest 및 리소스 검사 | 통과. aapt2 XML 확인 결과 usesCleartextTraffic, networkSecurityConfig 속성 없음. debug localhost 설정 리소스 없음. targetSdk 37 |
| 7 | `git diff --check` 및 변경 내용 비밀값 검토 | 통과. 실제 키 경로·비밀번호·토큰·TMAP 키 없음 |

## 화면별 실행 기록

| contest-demo 시나리오 | 화면 | 결과 |
|---|---|---|
| 1 | 홈 → Mock PARKED | 실패. 시작 시 Kotlin 컬렉션 클래스 접근 오류로 종료 |
| 2 | 매장 A → 아메리카노 → ETA 추천 | 시작 충돌로 실행 불가 |
| 3 | DRIVING 제한 → UNKNOWN → PARKED | 시작 충돌로 실행 불가 |
| 4 | 주문 확인 → 모의 결제 → 주문번호 | 시작 충돌로 실행 불가 |
| 5 | 내 주문 → 준비 중 → 픽업 준비 → 완료 | 시작 충돌로 실행 불가 |

## 기존 R8 수정과 구분

작업 시작 시 다른 브랜치에 있던 `automotive/src/main/keepRules/rules.keep` 수정은 stash에 보존했다. 이번 허용 파일 범위에 포함되지 않아 적용하지 않았다. 이전 APK에서는 이 수정 적용 후 시작 충돌이 해결됐지만, 그 결과를 이번 브랜치 검증으로 사용하지 않는다. 기준 코드의 R8 매핑에서 발생했던 Kotlin 컬렉션 클래스 접근 오류는 이번 release 수동 검증에서 재확인해야 한다.

검증 4는 통과했다. 검증 5는 설치 후 시작 실패 결과를 기록했으며, 전체 시연 성공은 미달성이다.

2026-11-06 재현 오류: `java.lang.IllegalAccessError: Class kotlin.collections.f extended by class l3 is inaccessible`. 이번 브랜치의 R8 규칙에는 기존 로컬 수정이 포함되지 않는다.
