# 제출용 릴리스 하드닝 1단계 검증

2026-11-06, 기준 main `0de8488`, 브랜치 `codex/release-hardening`.

| 번호 | 검증 | 결과 |
|---|---|---|
| 1 | `./gradlew test :automotive:assembleDebug :mobile:assembleDebug --rerun-tasks` | 통과. Kotlin 35개(shared 33 = DriveFlowTest 20 + TmapNavigationEtaProviderTest 13, automotive 1, mobile 1), 실패 0. Debug APK 2개 생성 |
| 2 | `python3 -m unittest discover -s server -v` | 통과. 12개, OK |
| 3 | 서명 속성 없이 `./gradlew :automotive:assembleRelease` | 통과. unsigned APK 생성. apksigner 검증 실패로 미서명 확인. 같은 명령 재실행 시 configuration cache 재사용 |
| 4 | 네 서명 속성 주입 후 release 빌드 및 `apksigner verify --print-certs` | 미실행. 사용자 터미널에서 키 비밀번호 숨김 입력 필요 |
| 5 | 이번 브랜치의 서명 APK 설치 및 시나리오 1~5 | 미실행. 4번 완료 후 진행 |
| 6 | release APK Manifest 및 리소스 검사 | 통과. aapt2 XML 확인 결과 usesCleartextTraffic, networkSecurityConfig 속성 없음. debug localhost 설정 리소스 없음. targetSdk 37 |
| 7 | `git diff --check` 및 변경 내용 비밀값 검토 | 통과. 실제 키 경로·비밀번호·토큰·TMAP 키 없음 |

## 화면별 실행 기록

| contest-demo 시나리오 | 화면 | 결과 |
|---|---|---|
| 1 | 홈 → Mock PARKED | 미실행 |
| 2 | 매장 A → 아메리카노 → ETA 추천 | 미실행 |
| 3 | DRIVING 제한 → UNKNOWN → PARKED | 미실행 |
| 4 | 주문 확인 → 모의 결제 → 주문번호 | 미실행 |
| 5 | 내 주문 → 준비 중 → 픽업 준비 → 완료 | 미실행 |

## 기존 R8 수정과 구분

작업 시작 시 다른 브랜치에 있던 `automotive/src/main/keepRules/rules.keep` 수정은 stash에 보존했다. 이번 허용 파일 범위에 포함되지 않아 적용하지 않았다. 이전 APK에서는 이 수정 적용 후 시작 충돌이 해결됐지만, 그 결과를 이번 브랜치 검증으로 사용하지 않는다. 기준 코드의 R8 매핑에서 발생했던 Kotlin 컬렉션 클래스 접근 오류는 이번 release 수동 검증에서 재확인해야 한다.

완료 기준 중 4번과 5번은 아직 충족되지 않았다.
