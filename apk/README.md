# 제출용 release APK 등록 절차

1. 사용자 터미널에서 저장소 루트로 이동합니다. README의 릴리스 서명 방법에 따라 네 서명 속성을 사용자 환경에 주입합니다. 서명 관련 값은 저장소에 기록하지 않습니다.
2. 사용자 터미널에서 서명된 release APK를 빌드하고 지정 경로로 복사합니다.

```bash
./gradlew :automotive:assembleRelease
mkdir -p apk
cp automotive/build/outputs/apk/release/automotive-release.apk "apk/조성민_PleosDriveFlow_release.apk"
```

3. 파일 크기를 확인합니다. 50MB를 넘으면 커밋하지 않고 GitHub Releases에 업로드하여 루트 README의 APK 위치를 해당 Release 페이지 URL로 갱신합니다.
4. 서명 검증, SHA-256 계산 및 에뮬레이터 설치·실행 검증 후 루트 README에 실제 APK의 SHA-256을 기록하고 커밋합니다. debug APK는 등록하지 않습니다.
