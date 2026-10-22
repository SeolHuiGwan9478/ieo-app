plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ieo.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ieo.app"
        // Pleos 차량 보안 요건(사이닝 키 로테이션)으로 28 이상 필수
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // 속도·기어는 Android 표준 차량 API(CarPropertyManager)로 읽음
    useLibrary("android.car")

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
}
