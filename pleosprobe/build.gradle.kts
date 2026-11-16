// Pleos SDK 확인용 별도 앱(11/16). DriveFlow 앱(:automotive)과 APK·패키지·의존성이 완전히 분리되어
// 제출 APK에는 영향을 주지 않는다. 확인이 끝나면 어댑터만 :automotive로 옮기고 이 모듈은 지워도 된다.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.pleoshellocar.probe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleoshellocar.probe"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-probe"
    }

    buildTypes {
        release {
            // R8 영향 확인용. 서명은 debug 키로 충분하다(에뮬레이터 설치 전용, 배포 금지).
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 버전은 공식 가이드 기준(2026-11-15 확인): NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation(libs.androidx.core.ktx)
}
