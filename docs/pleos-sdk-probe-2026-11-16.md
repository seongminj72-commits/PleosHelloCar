# Pleos SDK 확인 작업 (11/16)

작성 2026-11-15, 브랜치 `claude/pleos-sdk-probe`. 이 문서는 사전 조사 결과와 11/16 실행 절차, 판단 기준을 담는다. 실행 결과는 맨 아래 "결과 기록"에 채운다.

## 목적

1구간(11/16~20) 완료 기준인 "release APK에서 실제 ETA로 추천 갱신"을 위해, 하루 안에 다음 셋을 판정한다.

1. NaviHelper로 활성 경로의 남은 시간(ETA)을 받을 수 있는가
2. TTS·STT가 이 에뮬레이터에서 동작하는가 (3구간 음성 안내 범위 결정)
3. SDK를 넣고 R8을 켰을 때 연결·콜백이 깨지지 않는가

기어·속도는 이미 AAOS `CarPropertyManager`로 수신 확인됨(`docs/aaos-vehicle-validation.md`). Pleos Vehicle SDK는 기어·속도 getter가 공식 문서에 없어 이번 범위에서 제외한다.

## 사전 조사 결과 (2026-11-15, 공식 문서·공식 Maven 저장소)

### NaviHelper 3.0.5

| 항목 | 내용 | 근거 |
|---|---|---|
| 의존성 | `ai.pleos.playground:NaviHelper:3.0.5` → `navi-data:3.0.4`, `navi-aidl:3.0.1`, kotlin-stdlib 1.9.25, coroutines 1.6.3, core-ktx 1.8.0 | Nexus POM |
| 초기화 | `NaviHelper(context).initialize(onConnected = {}, onFailed = { t -> })`. onFailed는 초기 연결 재시도가 끝났을 때 | 가이드 Initialization |
| 리스너 | `addListener(NaviHelperEventListener)`는 연결 후 등록. 필요한 콜백만 override | 가이드 Event listeners |
| 정보 조회 | `get*Info()`는 값을 바로 돌려주지 않고 대응 콜백으로 결과가 온다 | 가이드 Destinations |
| ETA 후보 콜백 | `onRouteStateInfo`, `onTripInfo`, `onDrivingInfo`, `onDestinationInfo` | 가이드 Event listeners |
| 권한 | `NAVI_ROUTE`(get*Info, requestTrip), `NAVI_ROUTE_SEARCH`(requestRoute 등), `NAVI_CUSTOM_MAP` | 가이드 Declaring permissions |
| 오류 | `onError(NaviErrorCode)`: LOW_BATTERY, ROUTE_OVER_DISTANCE, TOO_SHORT_DISTANCE, SEARCH_ROUTE | 가이드 Error handling |
| **미확인** | `RouteStateInfo`·`TripInfo` 등 데이터 클래스의 필드 이름·단위. API Reference의 데이터 클래스 페이지가 본문 없이 렌더링됨 | 11/15 확인 |

→ 필드 이름을 추측해 코드에 쓰지 않는다. 프로브가 리플렉션으로 실제 필드와 값을 로그에 남긴다.

### TextToSpeech / SpeechToText 3.3.1

| 항목 | 내용 | 근거 |
|---|---|---|
| 배포 | TTS·STT 3.3.1이 공식 Maven에 게시됨(2026-10-13) | Nexus 검색 |
| 모드 | `ON_DEVICE`는 네트워크·앱 등록 불필요. `HYBRID`(기본)·`SERVER`는 Playground 프로젝트의 Client ID/Secret 필요 | 가이드 Introduction |
| 포털 상태 | 프로젝트 가이드는 여전히 TTS·STT를 "Not Available Yet"으로 안내 | 가이드 Create a Project |
| 권한 | `pleos.car.permission.TTS_SERVICE`, `STT_SERVICE` | 가이드 Declaring permissions |
| TTS | `initialize(OnInitializeListener)` → `addEventListener` → `setLocale(Locale.KOREA)` → `speak(text)`. ko-KR 지원 | 가이드 |
| STT | `initialize` → `addListener(ResultListener)` → `request()`. `onUpdated(stt, completed)`. 명령 실행 기능은 없음(Gleo Interface 별도) | 가이드 |

→ 서버 모드는 포털 상태상 쓰기 어렵다. **ON_DEVICE 모드가 에뮬레이터에서 동작하는지**가 핵심 확인 항목이다.

## 프로브 구성

- 별도 모듈 `:pleosprobe` (패키지 `com.example.pleoshellocar.probe`). **DriveFlow 앱(:automotive)의 코드·의존성·APK는 바뀌지 않는다.**
- `settings.gradle.kts`에 Pleos 저장소 추가. `includeGroup("ai.pleos.playground")`로 이 그룹만 받는다.
- 공식 문서에 시그니처가 있는 API만 직접 호출한다. 메서드 목록, 콜백 인자, TTS/STT 리스너 타입은 리플렉션으로 기록한다.
- 로그 태그 `DriveFlowProbe`. 화면에도 같은 내용이 나온다.
- 이 환경에서는 Android SDK가 없어 **컴파일 확인을 하지 못했다.** 컴파일 오류가 나면 아래 1단계의 대응을 따른다.

## 실행 절차 (11/16)

로그는 저장소 루트의 `probe-logs/`에 저장한다(`.gitignore` 처리됨). 다 끝나면 이 폴더만 전달하면 된다.

```bash
cd ~/AndroidStudioProjects/PleosHelloCar
git fetch origin && git checkout claude/pleos-sdk-probe
mkdir -p probe-logs
```

**1. 빌드** (Android Studio에서 Gradle Sync 후)
```bash
./gradlew :pleosprobe:assembleDebug 2>&1 | tee probe-logs/01-build-debug.txt
```
- Sync에서 Pleos 저장소 접근이 실패하면 그 오류를 그대로 저장하고 중단한다.
- 컴파일 오류가 나면 오류 전문을 저장한다. 가능성이 있는 곳은 `RouteInfo` 생성자(named args)와 TTS·STT import 경로다. `RouteInfo` 블록은 지워도 나머지 확인은 가능하다.

**2. 연결 확인 (목적지 없음)**
```bash
adb install -r pleosprobe/build/outputs/apk/debug/pleosprobe-debug.apk
adb logcat -c
adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --es stt off
sleep 40; adb logcat -d -s DriveFlowProbe > probe-logs/02-navi-noroute.txt
```

**3. 경로 설정 후 ETA 수신**
- 방법 A: Navigation 앱에서 직접 목적지 설정 후 안내 시작 → 프로브 다시 실행
- 방법 B: 프로브가 직접 요청
```bash
adb shell am force-stop com.example.pleoshellocar.probe; adb logcat -c
adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --ez route true --es stt off
```
- 2분 동안 10초마다 정보를 조회한다. 그동안 Virtual Console에서 기어 D·속도를 바꿔 본다.
```bash
sleep 130; adb logcat -d -s DriveFlowProbe > probe-logs/03-navi-route.txt
```

**4. TTS (ON_DEVICE)** — 2·3단계에서 이미 실행됨. 소리가 났는지 메모. 안 났으면 HYBRID도 한 번:
```bash
adb shell am force-stop com.example.pleoshellocar.probe; adb logcat -c
adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --ez navi false --es ttsMode HYBRID --es stt off
sleep 15; adb logcat -d -s DriveFlowProbe > probe-logs/04-tts-hybrid.txt
```

**5. STT (ON_DEVICE)** — 에뮬레이터 Extended controls > Microphone에서 호스트 마이크 입력을 켠다.
```bash
adb shell am force-stop com.example.pleoshellocar.probe; adb logcat -c
adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --ez navi false --ez tts false --es stt listen
# 화면 지시대로 말하기. 권한 요청이 뜨면 허용 후 같은 명령 재실행
sleep 20; adb logcat -d -s DriveFlowProbe > probe-logs/05-stt.txt
```

**6. R8 (release)**
```bash
./gradlew :pleosprobe:assembleRelease 2>&1 | tee probe-logs/06-build-release.txt
adb uninstall com.example.pleoshellocar.probe
adb install pleosprobe/build/outputs/apk/release/pleosprobe-release.apk
adb logcat -c
adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --ez route true --es stt off
sleep 60; adb logcat -d -s DriveFlowProbe > probe-logs/06-release.txt
# SDK에 포함된 consumer R8 규칙 확인
find ~/.gradle/caches -name "NaviHelper-3.0.5.aar" -exec unzip -p {} proguard.txt \; > probe-logs/06-navihelper-consumer-rules.txt
```

**7. 기존 앱 회귀 확인**
```bash
./gradlew test :automotive:assembleRelease 2>&1 | tail -30 > probe-logs/07-regression.txt
```

## 판단 기준 (11/16 저녁에 결정)

| 항목 | GO | NO-GO일 때 |
|---|---|---|
| NaviHelper ETA | 10초 안에 onConnected, 경로 설정 후 남은 시간 또는 도착 시각 필드가 있는 콜백 수신 | TMAP 경로 유지. PT 자료에 확인한 범위(연결, 이벤트)까지 명시 |
| TTS | ON_DEVICE에서 onDone과 실제 소리 | 3구간은 화면 안내 문구까지만, TTS는 인터페이스만 |
| STT | ON_DEVICE에서 `onUpdated(…, completed=true)`로 한국어 문장 | 텍스트 명령 해석기로 시연 |
| R8 | release 로그가 debug와 같은 흐름 | `:automotive` 연동 시 `-keep class ai.pleos.playground.** { *; }` 추가 후 재확인 |

NaviHelper가 GO면 11/17부터 `:automotive`에 `NaviHelperEtaProvider : NavigationEtaProvider`를 추가한다. Mock으로 몰래 대체하지 않는다는 기존 원칙(`TmapNavigationEtaProvider`)을 그대로 따른다.

## 결과 기록

| 단계 | 결과 | 로그 |
|---|---|---|
| 1 빌드 | | |
| 2 연결 | | |
| 3 ETA | | |
| 4 TTS | | |
| 5 STT | | |
| 6 R8 | | |
| 7 회귀 | | |

## 출처
- NaviHelper 가이드: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro (Initialization, Event listeners, Destinations, Declaring permissions, Error handling 하위 페이지)
- TextToSpeech 가이드: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- SpeechToText 가이드: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro
- 프로젝트·SDK 상태: https://document.pleos.ai/en/docs/pleos-only/create-my-project
- Maven 저장소: https://nexus-playground.pleos.ai/repository/maven-releases/ (NaviHelper-3.0.5.pom, REST 검색)
