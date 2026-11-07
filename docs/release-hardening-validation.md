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


## 2026-11-07 R8 수정 재검증

승인된 stash 규칙 원문을 PR 본문에 기록한 후 `-keep class kotlin.collections.** { *; }`를 적용했다. optimization 블록은 유지했다. 주석에 오류 원문, 날짜 및 packageScope 클래스 이동 접근 위반 방지 목적을 기록했다.

- 검증 1 통과. Gradle test 및 automotive/mobile assembleDebug 성공. Kotlin 35개(shared 33, automotive 1, mobile 1), 실패 0.
- 검증 2 미실행. 수정 후 미서명 assembleRelease 성공. 사용자 터미널에서 서명 속성 주입 후 빌드 대기.
- 검증 3 통과. 생성된 mapping.txt에서 부모와 자식이 같은 패키지에 유지됨.

```text
kotlin.collections.CollectionsKt__IterablesKt -> kotlin.collections.CollectionsKt__IterablesKt:
kotlin.collections.CollectionsKt__IteratorsKt -> kotlin.collections.CollectionsKt__IteratorsKt:
```

- 검증 4 미실행. 수정된 서명 APK 대기.
- 검증 5 미실행. 시나리오 1~5, DRIVING 제한, 결제 실패·재시도, Real 프록시 설정 필요 화면은 수정된 서명 APK로 확인 예정.
- 검증 6 통과. git diff --check 및 변경 내용 검토, 비밀값 없음.

위 2026-11-06 기록은 수정 전 APK의 결과다. 수정 후 전체 완료 기준은 아직 충족되지 않았다.


## 2026-11-08 최종 검증 — R8 수정본

검증 대상 코드 `8fa9272`, Pleos_Connect_v2.1.0 API 34, emulator-5554, AAOS user 10. 에뮬레이터 시각과 실제 검증일은 다를 수 있다.

| 검증 | 최종 결과 |
|---|---|
| 1 테스트·Debug 빌드 | 2026-11-07 동일 코드에서 Kotlin 35개 통과, 자동차·모바일 Debug 빌드 성공 |
| 2 서명 release | 사용자 숨김 입력 빌드 후 apksigner verify --verbose --print-certs 통과, v2 true. 업데이트 설치 성공 |
| 3 R8 매핑 | 위 인용처럼 CollectionsKt__IterablesKt와 CollectionsKt__IteratorsKt 모두 kotlin.collections 패키지 유지 |
| 4 시작·예외 로그 | 설치 후 logcat을 비우고 앱을 새로 실행. 전체 검증 후 `adb logcat -d`에서 `IllegalAccess|ClassNotFound|NoSuchMethod|FATAL` 일치 0건 |
| 5 화면 흐름 | 아래 시나리오 전부 통과 |
| 6 변경 검사 | git diff --check 통과, 비밀값 없음 확인 |

| 시나리오 | 실제 화면 관찰 | 결과 |
|---|---|---|
| 1 홈·PARKED | UNKNOWN 홈 정상 렌더링, 서비스 상태에서 PARKED 전환 | 통과 |
| 2 매장·메뉴·추천 | Coffee A, 아메리카노 1개 3,500원. ETA 8분·준비 6분·여유 1분에서 약 1분 후 주문 추천 | 통과 |
| 3 DRIVING 제한 | DRIVING에서 매장 A 선택 시 정차 상태 확인 필요 토스트, 메뉴 진입 차단. UNKNOWN을 거쳐 PARKED로 복귀 후 메뉴 진입 | 통과 |
| 4 결제 실패·재시도 | 다음 결제 실패 설정 후 결제 진행에서 모의 결제 실패·청구 없음 토스트. 같은 확인 화면에서 결제 재시도 성공, 주문번호와 실제 청구 없음 문구 확인 | 통과 |
| 5 주문 상태 | 내 주문에서 상품 준비 중 → 픽업 준비 완료 → 픽업 완료, 각 텍스트 알림 확인 | 통과 |
| Real 설정 실패 | ADB pm grant로 coarse/fine 위치 권한 부여 후 REAL 전환. `[TMAP ETA] 프록시 설정 필요` 표시 | 통과 |

차량 제한 검증은 Mock DRIVING이며 호스트는 P/0km/h였다. 위치 권한 부여는 설정 오류 경로 검증을 위한 ADB 조작이며 권한 대화상자 검증으로 간주하지 않는다. 이번 release 검증에서 실제 TMAP 요청 성공은 검증하지 않았다.

이 최종 결과가 이전 날짜의 미실행·시작 실패 상태를 대체한다. 요청한 R8 수정 검증 1~6 및 시나리오 1~5 완료.

검증 APK SHA-256: `4f5faf3e5a54847438ca5fd4122dfaa90fddbdca3e0e746e0b6bfe255d2a1f4f`
