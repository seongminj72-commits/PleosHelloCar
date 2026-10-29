# PleosHelloCar

Android Automotive OS & Pleos Connect Application Development

## Overview

현대자동차그룹 차량용 앱 개발 환경에서 Pleos Connect 에뮬레이터를 활용해 Android for Cars App Library 기반 애플리케이션을 구현하고 검증하는 프로젝트입니다. 초기 기준점 `v0.1.0`은 기본 Hello World 앱의 정상 작동 상태를 보존합니다.

## Development Environment

- macOS / Apple Silicon, Android Studio
- Kotlin (Compose 플러그인 2.2.10)
- Gradle 9.6.0 / Android Gradle Plugin 9.4.1
- Gradle Kotlin DSL (루트, automotive, mobile); shared 모듈은 Groovy DSL
- Android API 34: 최소 지원 API 및 사용자 확인 에뮬레이터 환경
- 실제 프로젝트 설정: `minSdk = 34`, `compileSdk = 37`, `targetSdk = 37`
- Pleos Connect v2.1.0 에뮬레이터
- Android for Cars App Library: automotive 1.7.0, shared 및 projected 선언 버전 1.4.0
- Gradle daemon JVM toolchain: JDK 25; Java 소스/타깃 호환성: 11

## Project Structure

```text
PleosHelloCar/
├── automotive/           # Android Automotive OS 앱 및 CarAppActivity 진입점
├── mobile/               # Compose 기본 화면 및 Android Auto projected 구성
├── shared/               # 공통 CarAppService, Session, Screen 및 서비스 Manifest
├── gradle/               # 버전 카탈로그, JVM 설정, Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitignore
└── README.md
```

`MyCarAppService`가 `MyCarAppSession`을 생성하고, 세션이 `MyCarAppScreen`을 반환합니다. 화면은 `MessageTemplate`으로 `Hello world!`를 표시합니다. shared Manifest는 POI 카테고리의 서비스를 등록하며 automotive Manifest는 Automotive 하드웨어와 template host를 요구합니다. mobile의 실제 Android Auto 연결 동작은 이번 기준점에서 검증하지 않았습니다.

## Features

- Car App Library 기반 앱 구성
- CarAppService 및 Session 연결
- Hello World 화면
- Pleos Connect 에뮬레이터 실행 (사용자가 사전에 정상 실행 확인)

## Build Instructions

Android Studio에서 프로젝트를 열고 Android SDK Platform 37과 프로젝트에 필요한 SDK 도구를 설치합니다. 로컬 SDK 경로는 커밋하지 않는 `local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`으로 설정합니다. JDK 25를 준비하고 Gradle 의존성을 다운로드할 수 있는 환경에서 실행합니다.

```bash
./gradlew :automotive:assembleDebug
```

생성 APK: `automotive/build/outputs/apk/debug/automotive-debug.apk`

## Emulator Test

Pleos Connect v2.1.0 에뮬레이터를 실행한 뒤 Android SDK platform-tools의 ADB로 확인합니다.

```bash
adb devices -l
adb install -r automotive/build/outputs/apk/debug/automotive-debug.apk
```

연결 대상이 여러 개라면 `adb -s <serial> install -r ...`로 대상을 지정합니다. 설치 후 에뮬레이터 앱 런처에서 PleosHelloCar를 실행해 Hello World 화면을 확인합니다.

## Development Roadmap

다음 완료 항목은 사용자가 사전에 확인한 개발 상태를 반영합니다.

- [x] Android SDK 환경 구성
- [x] Pleos Connect 에뮬레이터 실행
- [x] Android Automotive 프로젝트 생성
- [x] APK 빌드
- [x] 에뮬레이터 앱 실행
- [ ] 차량용 앱 아이디어 선정
- [ ] 사용자 인터페이스 구현
- [ ] 핵심 기능 MVP 개발
- [ ] 테스트 및 검증
- [ ] 공모전 제출용 시연 준비

## Notes

현재는 개발 환경 및 기본 앱 실행을 검증하는 초기 단계이며, 실제 차량에서의 동작은 아직 검증하지 않았습니다. `v0.1.0`은 Git 릴리스 기준 태그이며 기존 앱의 `versionName = "1.0"`, `versionCode = 1`은 그대로 유지합니다.

현재 서비스는 개발용 `ALLOW_ALL_HOSTS_VALIDATOR`를 사용합니다. 배포 단계에서 호스트 검증 정책을 검토해야 합니다. 이번 초기 백업에서는 기존 앱 코드와 의존성 설정을 변경하지 않습니다.

로컬 SDK 설정, IDE/Gradle/Kotlin 캐시, 빌드 산출물, APK/AAB, 서명 키 및 인증정보는 저장소에서 제외합니다. Gradle Wrapper JAR는 재현 가능한 빌드에 필요하므로 포함합니다. Pleos SDK 바이너리나 에뮬레이터 시스템 이미지는 포함하지 않으며, 해당 개발 환경은 제공처의 이용 조건에 따라 별도로 준비해야 합니다.
