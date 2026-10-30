# TMAP ETA 연동 — v0.3.0

조사·개발일: 2026-10-30. 이 버전은 실제 TMAP 요청 구현을 포함하지만, 발급된 appKey를 사용한 실 API 검증은 아직 수행하지 않았다. 기본값은 오프라인 Mock이다. 매장·주문·결제·차량 상태는 계속 가상 데이터이며 실제 결제/POS 연동은 없다.

## 구조와 적용 범위

`LocationManager → AndroidEtaRequest → 인증된 ETA 프록시 → TMAP 자동차 경로 API → TmapNavigationEtaProvider → OrderTimingCalculator → 화면 invalidate`

- 기존 `NavigationEtaProvider.getEstimatedArrivalMinutes(): Int?`와 주문 추천 계산식을 유지한다. 목적지 선택·상태 설명·변경 리스너를 기본 메서드로 확장했다.
- `MockNavigationEtaProvider`는 그대로 존재한다. 세션 시작은 MOCK이며 Real 실패 시 자동 Mock 대체는 없다.
- TMAP 경로는 출발 위치에서 가상 매장의 테스트 좌표로 계산한 별도 경로다. **차량 Navigation 앱이 현재 안내 중인 경로의 잔여 ETA가 아니다.** 경유지·현재 차량 경로 일치도는 보장하지 않는다.
- 가상 A(37.5665,126.9780), B(37.5700,126.9830), C(37.5600,126.9750)는 서울의 테스트 목적지다. 실제 매장/진입로/영업/주문 가능 여부를 의미하지 않는다. 실제 장소 검색은 하지 않는다.
- Android 표준 GPS, 없으면 Network 제공자를 사용한다. 정밀 위치 권한이 필요하다. 10초 내 위치, 30초 이하 나이, 정확도 100m 이하만 허용한다. 제공자가 실패하면 안전하게 ETA 없음으로 끝내며 다른 제공자로 재시도하지 않는다. 에뮬레이터 좌표는 실측 위치가 아니다.
- 주행 중 ETA 수신은 가능하지만 모드 변경·권한 요청·수동 갱신·주문 조작은 기존 정차 정책을 적용한다. 차량 상태 자체는 여전히 Mock이다.

## 공식 요청·응답 계약

[자동차 경로안내](https://tmap-skopenapi.readme.io/reference/자동차-경로안내), [공식 자동차 경로 샘플](https://tmapapi.tmapmobility.com/webservice/sample/WebSampleRoutes.html)을 확인했다.

```http
POST https://apis.openapi.sk.com/tmap/routes?version=1
Content-Type: application/json
appKey: <서버에서만 주입>
```

```json
{"startX":126.97,"startY":37.55,"endX":126.978,"endY":37.5665,"reqCoordType":"WGS84GEO","resCoordType":"WGS84GEO","searchOption":0}
```

X는 경도, Y는 위도다. searchOption 0은 교통최적+추천이다. 공식 샘플은 `features[0].properties.totalTime`을 60으로 나눠 분으로 표시한다. 따라서 프록시는 해당 초 값을 엄격히 파싱한다. `totalValue=2`의 축약 응답 구조를 추측하지 않도록 기본 전체 응답을 요청한다. 지도/경로 도형은 앱으로 전달하지 않는다.

앱→프록시 `POST /v1/eta` 요청은 `origin`/`destination`에 각각 `latitude`/`longitude` 숫자를 보내며 `Authorization: Bearer <단기 세션 토큰>`을 사용한다. 응답 계약:

```json
{"source":"TMAP","durationSeconds":481}
```

481초는 올림하여 9분으로 계산한다. 유효 범위는 기존 추천 알고리즘에 맞춰 0~10,800초(180분)다. 음수·문자·소수 초·NaN·범위 초과·필드 누락은 거부한다. 서버가 반환한 오류 본문은 사용자에게 그대로 노출하지 않는다.

## 갱신·오류 정책

앱 세션이 foreground일 때 5초마다 상태를 확인하고, 실제 조회는 최소 30초 간격·한 번에 한 요청만 실행한다. 화면 렌더링은 네트워크 요청을 만들지 않는다. 결과는 메모리에만 보관하며 60초 후 무효다. 새 조회를 시작하면 이전 ETA를 비운다. 목적지·모드 변경, background 전환은 진행 중 요청을 취소하고 세대 번호로 늦은 응답을 무시한다. 사용자 화면은 리스너로 다시 그려지고 추천은 최신 값을 읽어 다시 계산한다.

| 상황 | 동작 |
|---|---|
| 위치 권한 거부/대략적 위치만 허용 | 정확한 위치 권한 필요, ETA null |
| GPS 비활성·위치 없음·오래됨·부정확 | 최신 위치 확인 불가, ETA null |
| 프록시 설정 없음/잘못된 URL/로컬 토큰 만료 | 프록시 설정 필요 |
| 프록시 401/403 | 인증 만료 또는 거부 |
| 프록시/TMAP 429 | 호출 한도 초과, 자동 즉시 재시도 없음 |
| 연결 실패/타임아웃 | 네트워크 연결 실패 |
| 서버 4xx/5xx·TMAP 인증 실패 | 경로 서버 오류; TMAP 인증 실패는 서버 운영자가 확인 |
| 잘못된 JSON/출처/ETA | ETA 응답 오류 |
| 모든 Real 실패 | Mock으로 대체하지 않음, 기존 알고리즘의 도착 후 주문 추천 |

연결 타임아웃 5초, 읽기 타임아웃 8초, 전체 요청 감시 25초다. 앱 응답 최대 8KB, 프록시 입력 2KB, TMAP 응답 2MB로 제한한다. 프록시는 리디렉션을 따라가지 않는다. 정상 로그는 출처/상태만 기록하고 좌표·응답 본문·토큰·키는 기록하지 않는다.

## 키 보호와 인증 범위

TMAP appKey는 소스, Gradle BuildConfig, 리소스, APK, 앱 설정 파일에 넣지 않는다. `server/eta_proxy.py`만 서버 환경변수 `TMAP_APP_KEY`를 읽는다. 개발 실행 도구는 환경변수가 없을 때 화면에 표시하지 않는 입력으로 키를 받아 서버 메모리에만 유지한다. 키를 채팅/스크린샷/명령행 인자로 전달하지 않는다.

앱의 `files/eta-proxy.json`은 URL·단기 프록시 토큰·만료 시각만 포함한다. 개발 실행 도구가 ADB run-as로 **현재 AAOS 사용자**에 주입한다. 이 토큰은 추출 가능하며 영구 비밀이 아니다. 서버에서도 만료를 강제한다. 기본 30분, 최대 1시간, 세션당 최대 100회·분당 2회이며 실패 호출도 예산에 포함한다. 종료하면 파일과 adb reverse를 제거한다. 설정 파일은 Git 제외 대상이며 백업에서도 제외한다.

이 Python 서버는 **로컬 개발 도구**다. 127.0.0.1에만 바인딩하며 공용 서비스로 배포하지 않는다. 재시작/여러 인스턴스 전체 일일 사용량은 관리하지 못한다. 운영 시에는 HTTPS 인증 게이트웨이, 사용자 로그인(OIDC/OAuth + PKCE 등), 짧은 사용자별 토큰 검증·권한 범위·폐기, 서버 비밀 관리, 영구 사용자별/전체 일일 할당량, 공급자 비용 상한과 모니터링이 필요하다. 인증되지 않은 공용 프록시로 공개하면 안 된다. 운영 로그인·게이트웨이 배포는 이번 로컬 구현에 포함되지 않는다.

APK release 코드는 HTTPS만 허용한다. debug에서만 `http://localhost`를 허용해 `adb reverse`를 이용한다. 임의 IP cleartext, TLS 검증 우회, 하드코딩된 공용 bearer token은 없다. 기존 개발용 HostValidator는 별개의 출시 제한으로 남는다.

## 요금·약관 확인

2026-10-30 공식 [요금표](https://openapi.sk.com/products/calc?menuSeq=5&svcSeq=4), [TMAP API 약관](https://tmapapi.tmapmobility.com/terms.html) 기준:

- 경로안내 Free: 일 1,000건. 한도 초과 시 차단으로 안내된다.
- 자동차 경로안내 Premium: 건당 11원, VAT 포함. Lite: 월 2,200,000원(VAT 포함), 경로안내 일 10,000건.
- 경로안내 그룹의 공유 한도·계약별 조건은 실제 프로젝트 대시보드에서 재확인한다. 단순 계산상 30초 갱신은 시간당 최대 약 120회이며 여러 차량이면 빠르게 소진된다.
- 취득 데이터는 저장 후 24시간 이상 사용할 수 없다고 명시되어 있다. 동일 서비스를 여러 프로젝트로 나눠 무료 한도를 우회해서는 안 된다.
- 이 구현은 영구 캐싱을 하지 않고 응답에 `Cache-Control: no-store`를 설정한다. 앱 메모리 ETA의 최대 수명은 60초다. 약관을 임의의 재배포/가공/장기 저장 허가로 해석하지 않는다. 상용 적용 전에 서비스 목적·좌표 전송 안내·계약 조건을 확인한다.

## Android Studio 실행

1. 프로젝트를 열고 Gradle Sync. JDK는 Android Studio 내장 JBR을 사용한다. automotive 모듈, Pleos_Connect_v2.1.0(API 34)을 선택해 Run.
2. 오프라인 데모: 서비스 상태 → 차량 상태를 PARKED로 설정 → 매장 선택. 기본 Mock ETA=8분이다.
3. Real 준비: 서버 터미널에서 아래 실행. appKey는 숨김 입력창에 직접 입력한다. 실제 조회는 공급자 사용량에 포함된다.

```sh
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
python3 server/run_local_demo.py
```

4. 서비스 상태 → ETA 연결 설정 → 위치 권한 허용 → 앱 사용 중 허용 → 모드를 REAL로 변경. 현재 위치가 프록시와 TMAP에 전송된다는 화면 안내를 확인한다.
5. 가상 매장 A/B를 선택한다. 에뮬레이터 Extended Controls의 Location에서 서울 좌표를 설정하거나 다음 명령을 사용한다. GPS 나이 제한 때문에 조회 직전에 입력하고 필요 시 반복한다.

```sh
"$ADB" emu geo fix 126.9700 37.5500
```

6. 30초 호출 간격 내에는 즉시 재시도하지 않는다. `[TMAP ETA] 실제 경로 응답`과 추천 변경을 확인한다. 에뮬레이터/모의 위치는 별도 표시된다. 앱 재시작 시 모드는 MOCK으로 초기화한다.
7. 서버 종료 Ctrl+C 후 Real 실패/ETA 없음, Mock으로 명시 전환했을 때 오프라인 동작을 확인한다. 임시 토큰은 30분 뒤 만료되므로 필요하면 서버 도구를 재시작한다.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew test :automotive:assembleDebug :mobile:assembleDebug
python3 -m unittest discover -s server -v
```

APK: `automotive/build/outputs/apk/debug/automotive-debug.apk` (versionName 0.3.0, versionCode 3). debug 서명이며 출시 APK가 아니다. 구현·검증 결과는 [개발 보고서](v0.3.0-development-report.md)를 참조한다.


## 키 없이 HTTP 연결을 확인하는 합성 테스트

`python3 server/run_local_demo.py --fixture`는 TMAP을 전혀 호출하지 않고 481초를 반환한다. debug 앱만 `source=TEST_FIXTURE`를 허용하며 화면/로그에 `[HTTP 테스트 ETA] 합성 응답 · 실 API 아님`으로 표시한다. Release 앱은 이 출처를 거부한다. 위 위치 설정·권한·Real 전환 절차를 적용하면 A 매장 준비 6분 기준 `ETA 9분 → 약 2분 후 주문 추천`을 확인할 수 있다. 이 테스트의 성공은 실제 TMAP 인증이나 서비스 응답 검증을 의미하지 않는다.
