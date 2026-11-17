# 예약 전달 서명 release 검증

기준 브랜치 `codex/scheduled-dispatch`, 코드 기준 커밋 `817f753`. Pleos Connect v2.1.0 에뮬레이터에서 서명된 `automotive-release.apk`를 2026-11-17 설치해 진행했다.

## 검증 결과

| 시나리오 | 결과 | 근거 |
|---|---|---|
| 서명 release 설치 및 첫 화면 | 성공. 앱 실행과 예약 화면 이동 확인 | 에뮬레이터, 앱 로그 |
| 내비 전면 + ETA 급감 | 30분 → 5분 시연 ETA 변경 뒤 약 25초에 예약 전달 발생. 결과에 지연 안내 포함 | `probe-logs/scheduled-final/drive.log`, [내비 전면 1](validation-scheduled-dispatch/02-navigation-dispatch-1.png), [내비 전면 2](validation-scheduled-dispatch/03-navigation-dispatch-2.png) |
| 주문 상태 | Coffee A 주문이 전달됨으로 표시되고 “약 1분 늦게 준비될 수 있습니다” 표시 | [주문 상태와 음성 안내 오류](validation-scheduled-dispatch/04-order-delivered-with-tts-error.png) |
| TTS 음성 안내 | 실패. 초기화/리스너 등록 및 한국어 로캘 설정 로그 뒤 20초 제한에서 `ON_DEVICE TTS failed`; 실제 음성 출력은 관측되지 않음 | `probe-logs/scheduled-final/drive.log` |
| D·속도 > 0 검증 | 미실행. Virtual Console 상태에서 `bridge_ready=false`, `emulator_active=false`, DDS 입력 준비 상태 전체 false였고 gear/controls 요청은 HTTP 503. 실제 D 및 속도 > 0을 만들지 못함 | Virtual Console `/vehicle/state` 응답, 요청 결과 |
| ETA 증가, 통신 단절/복구, 마감 실패, 주행 중 취소 | 미실행. 필수 D·속도 조건이 구성되지 않아 중단 | 해당 시나리오 수행 로그 없음 |
| 크래시 확인 | 수집한 테스트 구간에서 예약 흐름의 크래시는 관측되지 않음. 주행 반복 시험은 미실행 | `probe-logs/scheduled-final/drive.log` |

이 회차는 Virtual Console의 P·0 상태에서 진행됐다. 따라서 백그라운드 전달 및 주문 상태는 관찰했지만 주행 중 자동 전달 완료 기준을 충족하지 않는다.

## TTS 후속 수정

기존 release 검증에서 ON_DEVICE 엔진 초기화 성공 뒤 `onReady`가 오지 않아 음성 요청이 시작되지 않고 실패하는 것을 관찰했다. 검증 프로브에서는 초기화 뒤 지연된 `speak` 호출 후 `onReady`가 관찰됐다. 이를 근거로 `DispatchVoice`가 초기화와 로캘 설정 뒤 3초 대기 후 음성 요청을 한 번 보내도록 보완했다. 이후 `./gradlew test`와 `:automotive:assembleRelease`는 성공했다.

이 보완 빌드는 **서명되지 않았고**, 에뮬레이터에서 아직 검증하지 않았다. 새 서명 APK에서 음성 시작/완료 콜백과 실제 청취를 확인해야 한다. D·속도 > 0 Virtual Console 입력이 복구되면 미실행 시나리오도 다시 시험한다.

## 재현에 사용한 확인 정보

- 에뮬레이터: Pleos Connect v2.1.0, API 34
- 서명 release 테스트에서 주문 전달 로그: `데모 전달 완료. 약 1분 늦게 준비될 수 있습니다`
- TTS 로그: 초기화 및 이벤트 리스너 설정 이후, 20초 제한에서 `ON_DEVICE TTS failed`
- Virtual Console: `gear.bridge_ready=false`, `controls.emulator_active=false`, DDS 입력 `all=false`; gear/controls PUT 응답 HTTP 503
- 미검증: 실제 차량, 다른 호스트, 실제 매장/POS, 실제 결제, release 실제 TMAP ETA, D·속도 > 0에서의 시나리오
