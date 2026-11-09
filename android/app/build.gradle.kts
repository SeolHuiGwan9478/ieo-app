import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

        // Pleos 콘솔에서 받은 인증 정보. git에 올라가지 않는 local.properties에서 읽는다 (없으면 빈 값: 앱은 돌지만 서버 음성 합성이 안 된다)
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProperties.getProperty("pleos.clientId", "")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProperties.getProperty("pleos.clientSecret", "")}\"")
    }

    // 속도·기어는 Android 표준 차량 API(CarPropertyManager)로 읽음
    useLibrary("android.car")

    buildTypes {
        debug {
            // 샘플 화면 전환 버튼. 평소에는 숨기고 필요할 때만 -PieoShowDemoSwitcher=true 로 빌드한다
            buildConfigField("boolean", "SHOW_DEMO_SWITCHER", providers.gradleProperty("ieoShowDemoSwitcher").getOrElse("false"))
            // 에뮬레이터에서 본 내 컴퓨터(localhost)의 서버
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            buildConfigField("boolean", "SHOW_DEMO_SWITCHER", "false")
            isMinifyEnabled = false
            // 배포 서버 주소는 빌드할 때 넘긴다: ./gradlew assembleRelease -PieoApiBaseUrl=https://.../
            val apiBaseUrl = providers.gradleProperty("ieoApiBaseUrl").getOrElse("https://api.ieo.invalid/")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 디버그 빌드에서만 데모 상태 전환을 보여 주려고 BuildConfig.DEBUG를 쓴다
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // TODO: 출시 전에는 쓰는 아이콘만 벡터로 옮기고 이 의존성을 뺀다 (용량이 큼)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    // QR 로그인용 QR 코드 생성 (Google 서비스 불필요)
    implementation(libs.zxing.core)

    // 서버 연결
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    // 화면 상태(ViewModel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
