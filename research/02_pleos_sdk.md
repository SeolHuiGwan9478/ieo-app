# Pleos Connect SDK 조사 (화물차 기사용 차량 앱 관점)

- 조사일: 2026-10-22
- 기준 문서: https://document.pleos.ai (Pleos Playground v2.6.0 개편본, SDK 릴리즈 2026-10-19)
- 요청받은 URL 중 `https://document.pleos.ai/api-reference/connect-sdk-pleos`는 존재하지 않아 사이트 루트로 리다이렉트된다. 사이트맵에도 해당 경로가 없다. API Reference는 `/docs/pleos-only/connect-sdk-pleos/<SDK>/...` 아래에 있다.
- 요청받은 URL 중 `https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk`도 사이트맵에 없다. 실제 경로는 `https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app`이다.
- **주의:** Dokka로 생성된 API Reference 상세 페이지(예: `.../Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-current-distance-driven`)는 브라우저로 열어도 제목만 나오고 시그니처, 단위, 반환 타입이 비어 있다. 그래서 API 목록은 **사이트맵의 메서드 경로명**에서 뽑았다. 단위와 타입은 "문서에서 확인 안 됨"으로 표기했다.

---

## 0. 한눈에 보는 판정표

| 앱 기능 | 필요한 데이터/동작 | 판정 | 경로 |
|---|---|---|---|
| 대기시간 자동 기록 | 속도 0 / 기어 P / 시동 상태 | **조건부 가능**. Vehicle SDK에는 속도·기어·시동 API가 없다. AAOS `CarPropertyManager`(PERF_VEHICLE_SPEED, 기어 등)를 직접 써야 한다. 위치(FusedLocation)와 지오펜스(addProximityAlert)는 SDK로 가능하다. | AAOS Car API + FusedLocation |
| ETA 알림 | 목적지까지 남은 거리/시간, ETA | **가능**. NaviHelper `getDestinationInfo()` → `DestinationInfo.remainDistance/remainTime`, `onTripInfo(TripInfo.eta)`, `onDestinationArrived` | NaviHelper |
| 음성으로 다음 배차 추천/수락 | TTS, STT, 음성 명령 | **가능 (제약 있음)**. TTS/STT는 ko-KR을 지원한다. 웨이크워드는 SDK에 없다. 시스템 음성비서(Gleo) 연동은 Gleo Interface(App Schema)로 별도 처리한다. | TTS, STT, Gleo Interface |
| 배차 수락 후 길안내 | 목적지 설정 | **가능**. `requestRoute(RouteInfo)`, `requestRouteWithWaypoints()`, 경유지 최대 2개 | NaviHelper |
| 연속운전 휴게 안내 | 주행 시간, 속도, 주행 상태 | **가능**. Odometer 도메인의 trip 주행시간 API(getTripCurrentDrivingTime 등)와 AAOS 속도/기어를 조합한다. 휴게소 정보 API는 문서에서 확인 안 됨. | Vehicle SDK + AAOS |
| 주행거리 기반 장부 | 누적 주행거리, 연료 | **가능**. Odometer `getCurrentDistanceDriven`/`registerDistanceDriven`, Fuel `getFuelPercent`/연비 API | Vehicle SDK |

---

## 1. 공통 사항

### 1.1 Gradle 저장소와 의존성

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup , https://document.pleos.ai/docs/pleos-only/getting-started , https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015

저장소(모든 SDK 공통):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
        }
    }
}
```

artifact 좌표(최신 SDK 릴리즈 노트 기준, 2026-10-19):

| SDK | 좌표 | 출처 |
|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | .../Vehicle/project-setup |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | .../NaviHelper/importing-sdk |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | .../TextToSpeech/importing-sdk |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | .../SpeechToText/importing-sdk |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | .../FusedLocation/project-setup |

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
}
```

참고: Getting started 페이지 예시에는 아직 `Vehicle:2.3.1`이 적혀 있다. 최신은 2.3.2다.
저장소 인증(계정/토큰) 필요 여부는 문서에서 확인 안 됨. Getting started에는 "의존성을 찾지 못하면 Maven 저장소 접근 권한과 SDK 버전을 확인하세요"라고만 나와 있다.

### 1.2 minSdk / targetSdk / 개발 환경

출처: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator , https://document.pleos.ai/docs/pleos-only/getting-started , https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app

- **minSdk: API 28 이상 (필수)**. 차량 사이버보안 요건(signing key rotation, lineage) 때문이다.
- Gradle 8.0 이상, Java 또는 Kotlin
- 에뮬레이터: Pleos Connect Emulator v2.1.0, Android API 34 system image
- targetSdk/compileSdk: **제품 앱 기준의 공식 요구값은 문서에서 확인 안 됨**. 참고로 Sample App은 compileSdk/targetSdk 35, AGP 8.8.2, Kotlin 2.0.0, JDK 17, Gradle 8.10.2를 쓴다(Sample App 자체 minSdk는 23).
- 프로젝트 템플릿: Android Studio의 Automotive 카테고리에서 Empty Activity로 시작한다.

### 1.3 Manifest 권한 (`pleos.car.permission.*`)

출처: .../Vehicle/vehicle-permission , .../FusedLocation/declaring-permissions , .../NaviHelper/declaring-permissions , .../TextToSpeech/declaring-permissions , .../SpeechToText/declaring-permissions

| 용도 | 권한 |
|---|---|
| Odometer, DrivingMode, Brake 조회 | `pleos.car.permission.CAR_DRIVE` |
| Fuel, EvBattery 조회 | `pleos.car.permission.CAR_ENERGY` |
| CarInfo, Display | `pleos.car.permission.CAR_INFO` |
| Safety, Tire | `pleos.car.permission.CAR_SAFETY` |
| Light, TurnSignal | `pleos.car.permission.CAR_LIGHTS` |
| 제어(set) | `CONTROL_CAR_*` (예: `CONTROL_CAR_DOORS`) |
| 위치 | `pleos.car.permission.FUSED_LOCATION` (protection level: dangerous) |
| 내비 정보 조회 / requestTrip | `pleos.car.permission.NAVI_ROUTE` |
| 경로·목적지·경유지 설정 | `pleos.car.permission.NAVI_ROUTE_SEARCH` |
| 내비 화면 요청 | `pleos.car.permission.NAVI_CUSTOM_MAP` |
| TTS | `pleos.car.permission.TTS_SERVICE` |
| STT | `pleos.car.permission.STT_SERVICE` |

- Vehicle 권한은 런타임 승인 팝업 방식이 아니고 Manifest 선언으로 동작한다(Sample App 가이드).
- **Vehicle SDK 권한은 Pleos App Market과 사전 협의·승인된 것만 선언할 수 있다.** 승인되지 않은 권한을 선언하면 심사에서 탈락한다. 출처: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability#sdk-api-review
- Sample App Manifest에는 `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_MILEAGE`, `READ_CAR_DISPLAY_UNITS` 같은 권한도 있다. 이 권한들과 매핑되는 Vehicle SDK API는 문서에서 확인 안 됨. AAOS 속도/기어용 Android 표준 권한(`android.car.permission.CAR_SPEED`, `CAR_POWERTRAIN` 등)을 써야 하는지, Pleos 권한을 써야 하는지도 문서에서 확인 안 됨.
- SDK 사용 신청: Playground의 My Project에서 SDK별로 신청한다. 문서상 "Vehicle State"와 NaviHelper는 신청 없이 바로 쓸 수 있다. FusedLocation, TTS, STT, Vehicle Control은 "준비 중(Coming Soon)"으로 적혀 있다. TTS/STT 서버 모드를 쓰려면 프로젝트의 Client ID/Secret이 필요하다. 출처: https://document.pleos.ai/docs/pleos-only/create-my-project

### 1.4 앱 구조 제약 (Activity / Service / 백그라운드)

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model , .../performance-resource-mgmt , .../vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus

- 화면 모델: Driving view(고정 영역)와 앱 영역(window 최대 2개)으로 나뉜다. `screenOrientation` 미지정 시 portrait(1 window), landscape 지정 시 2 window를 쓴다. portrait와 landscape 레이아웃을 모두 설계해야 한다.
  - 에뮬레이터 확인(2026-10-22): 방향을 지정하지 않은 앱도 앱 영역 가장자리 손잡이(⋮)를 끌어 내비와 반반으로 나눌 수 있었다. 반 화면은 약 840dp × 1200dp다. 반대로 `landscape`로 지정하면 반 화면 창에서 "이 앱을 사용하려면 창 크기를 넓게 조정하세요"만 보였다.
- 화면에서 사라진 앱도 백그라운드에 남을 수 있다. 문서는 백그라운드에서 오디오, 통신, 다운로드처럼 지속할 작업은 "실행 조건과 중단·재개 정책을 명확히 정의"하라고 한다. 불필요한 상주, 타이머, 폴링은 최소화하라고 한다. 즉 백그라운드 작업 자체를 금지하지는 않는다.
- **백그라운드 Service나 Foreground Service 사용 허용 여부를 명시한 규정은 문서에서 확인 안 됨.** Gleo App Schema에서는 `component: "service"`(sendBroadcast로 UI 없는 백그라운드 처리)를 공식 지원한다.
- 리소스 기준: 주행 중 비내비 앱 CPU 5% 미만, 정차 중 25% 이하, RAM 400MB 이하. 앱은 10초 이내에 실행되어야 한다.
- 시동 OFF 후에는 Suspend 모드(저전력)로 들어간다. 오디오는 중단해야 하고 포커스도 반납해야 한다. 앱은 "언제든 중단·재시작될 수 있는 상태"로 간주해야 한다.
- 하드웨어 직접 접근 금지. 차량 정보는 플랫폼 API로만 접근한다. 출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture

### 1.5 에뮬레이터에서 차량 신호 시뮬레이션

출처: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator , https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator , https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide

- 이미지 설치: Android Studio > SDK Manager > SDK Update Sites에 추가한다.
  - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
  - API 34 Pleos Connect system image를 쓴다. AVD는 **Cold boot 필수**(quick boot면 VHAL을 쓸 수 없다). RAM은 4GB.
  - 외장 GPU가 필수다(최소 RTX 3060 Laptop 6GB 급).
- **Virtual Console**: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/` 접속
  - Motion: **Gear Selection(P/R/N/D)**, Brake Pedal(0~100%), **Odometer(0~1,000,000km)**, Steering Angle
  - ADAS: Cruise state, Cruise Target Speed
  - Energy: Remaining Range, Battery SOC 등
  - Safety: Telltales(주차 브레이크, 안전벨트 등)
  - Vehicle: Notification
  - **차량 속도 카드는 사용 가이드 카드 목록에 없다.** 셋업 가이드 소개문에만 "차량 속도, 도어, 조명 등 차량 상태 변경"이 언급된다. **연료량(Fuel %) 카드도 목록에 없다.** 시동/IGN 시뮬레이션도 문서에서 확인 안 됨.
- 주행 규제(UX restriction) 강제 ON/OFF:
  ```
  adb shell cmd hmg_car_service --service drivingstate driving_regulation on
  adb shell cmd hmg_car_service --service drivingstate driving_regulation off
  ```
- 에뮬레이터 위치(GPS) 시뮬레이션 방법: 문서에서 확인 안 됨(Android 에뮬레이터 Extended Controls 사용 여부도 언급 없음).
- 마이크: STT 테스트를 위해 Extended Controls > Microphone에서 호스트 오디오 입력을 켠다.

### 1.6 운전 중 UI 제약 (Driver Distraction)

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline , .../driving-restiction-policy , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide , https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category , https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements

- **Distraction Optimization(DO)을 선언하지 않은 앱은 주행(Drive) 중 사용 불가**이고 주차 중에만 사용할 수 있다.
- DO를 선언한 앱도 AAOS `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()`로 받은 ActiveRestrictions에 맞춰 기능을 직접 제한해야 한다.
- 지역별 주행 중 제한: 아래 "그외 지역"이 한국에 적용되는지는 문서에서 명시적으로 확인 안 됨. 지역 코드 값에 `KOR`이 있다.
  - 북미: 키보드, 다이얼패드 ⛔ / 검색은 음성 한정 / 설정·로그인 ⛔ / 동영상 ⛔ / 대용량 텍스트 ⛔
  - 그외 지역: 키보드, 다이얼패드, 검색 ✅ / 미디어 로그인·설정 ⛔ / 차량설정 ⛔ / 동영상 ✅로 표기됨 / 대용량 텍스트(LIMIT_CONTENT, LIMIT_STRING_LENGTH) ⛔
- UX 원칙: 큰 터치 타깃, 높은 대비, 텍스트 최소화, 음성·프리셋 우선, 자동 애니메이션 최소화
- 알림: 심사 가이드상 "운전과 관련되지 않은 알림은 사용할 수 없다", "미디어 앱은 헤드업 알림을 게시해서는 안 된다". 배차 알림이 "운전 관련"으로 인정되는지는 문서에서 확인 안 됨.
- 카테고리: 화물 배차 앱은 Business 또는 Mobility Services에 해당할 것으로 보인다. 주행 중 제한적 허용 예시가 있는 카테고리는 Music/Audio, Maps/Navigation, Mobility Services뿐이다(Business 예외는 문서에 없음).
- 내비 정책: 단일 경로 원칙에 따라 Navigation 앱 하나만 Active Route를 가진다. 우리 앱이 직접 TBT 음성 안내를 하면 안 되고 NaviHelper로 OEM 내비에 위임해야 한다. 출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide

### 1.7 차량 모델별 호환성

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability

- Vehicle SDK: 모델에 따라 다르다. `checkXxxCapabilities`로 확인한다.
- NaviHelper: 내비게이션이 탑재된 차량에서만 쓸 수 있다. 내비 탑재 여부를 확인하는 방법은 비공개다(partnership@pleos.ai 문의, FAQ).
- TTS/STT: 모든 모델에서 호환된다.
- FusedLocation: 모델에 따라 다르다. `isSupported()`로 확인한다.
- **화물차(상용차) 모델 지원 여부는 문서에서 확인 안 됨.** 플랫폼은 Connect-S / Connect-L / Connect-W로만 구분된다.

---

## 2. Vehicle SDK (v2.3.2)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro , .../Vehicle/obtain-domain-object , .../Vehicle/callback , .../Vehicle/get-property , .../Vehicle/check-capability , .../Vehicle/vehicle-error-code

### 2.1 구조
- 진입점 `Vehicle(context)` → `initialize()` → `getOdometer()`, `getFuel()` 등으로 도메인 객체를 얻고 → 마지막에 `release()`
- 도메인마다 4가지 API 타입이 있다: Capability(`checkXxxCapabilities`), Get(`getCurrentXxx`, 비동기 onComplete/onFailed), Set(`setXxx`), Callback(`registerXxx`/`unregisterXxx`, 리스너 `onXxxUpdated`/`onFailed`)
- IPC 기반이라 모든 결과가 비동기로 온다. 콜백이 끝나기 전에 `release()`를 호출하면 안 된다.
- 도메인 목록: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper

### 2.2 요청 신호별 확인 결과

| 신호 | Vehicle SDK | 비고 / 출처 |
|---|---|---|
| **속도** | **없음** | v2.3.2에서 `VehicleSpeedListener`(onSpeedUpdated)가 **삭제**됐다(sdk-261015 릴리즈 노트 Shutdown API). FAQ는 클러스터와 같은 속도를 원하면 AAOS `CarPropertyManager`로 `PERF_VEHICLE_SPEED`를 읽으라고 안내한다. https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq |
| **기어(P/D/R)** | **없음** | SDK API 정책 문서상 "속도, 기어 상태, 외기 온도, 연료 레벨 등 표준 데이터는 AAOS Car API"를 쓴다. Virtual Console에서 기어 P/R/N/D 시뮬레이션은 가능하다. https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api |
| **시동/전원 상태** | **없음** | IGN 상태 API는 문서에서 확인 안 됨. Safety 도메인에 `hasPowerDownWarningIndicator`/`registerPowerDownWarningIndicator`가 있지만 의미와 시그니처는 문서에서 확인 안 됨. AAOS `IGNITION_STATE` 사용도 Pleos 문서에는 언급이 없다. |
| **누적 주행거리** | **있음** (Odometer) | `getCurrentDistanceDriven`, `registerDistanceDriven`, `checkCurrentDistanceDrivenCapabilities`. 권한은 `CAR_DRIVE`. 단위는 문서에서 확인 안 됨(Odometer 도메인에 "주행 거리 단위 조회"가 있다고 소개됨). |
| 트립 거리/시간 | 있음 (Odometer) | `get/registerTripCurrentDrivingDistance`, `TripCurrentDrivingTime`, `TripCumulativeDrivingDistance/Time`, `TripDrivingDistance/Time`(check만 확인), `TripSinceRefillDrivingDistance/Time`, `TripInstantaneousDrivingDistance`. 연속운전 시간 계산에 쓸 수 있다. |
| **연료량** | **있음** (Fuel) | `getFuelPercent`, `registerFuelPercent`, `checkFuelPercentCapabilities`. 권한은 `CAR_ENERGY`. |
| **연비** | **있음** (Fuel) | `get/registerInstantaneousFuelEconomy`, `CurrentFuelEconomy`, `CumulativeFuelEconomy`, `SinceChargeFuelEconomy`. 단위는 문서에서 확인 안 됨. |
| 주행가능거리 | 일부 | Odometer에 `setRangeRemainingDistance`(set)만 있다. 조회 API는 문서 목록에 없다. `RangeRemainingListener`는 "문서 숨김" 처리됐다. |
| 기타 | 있음 | Brake: `getCurrentBrakePedalStatus`/`register...`. DrivingMode: 드라이브 모드, 크루즈 상태/목표속도. Safety: 텔테일(`getTelltalesInfo`, 주차 브레이크 경고등 포함), 엔진오일, 안전벨트, 에어백. TurnSignal 상태. Tire: `getLowTirePressure`. CarInfo: `getVINInfo`. SteeringWheel 각도. Toll(하이패스 안내 음량). Seat 탑승(occupancy). |

### 2.3 초기화·구독 예시 (문서 원문, Kotlin)

```kotlin
// Vehicle 인스턴스 생성 후 초기화
val vehicle = Vehicle(context)
vehicle.initialize()
// getXxx()로 도메인 객체 획득
val door = vehicle.getDoor()
// ...
// 사용 종료 후 리소스 해제
vehicle.release()
```

```kotlin
// Callback (문서 예시는 Door 도메인. Odometer/Fuel도 같은 register/unregister 패턴)
val callbackListener = object : DoorPositionListener {
  override fun onPositionUpdated(pos: Int?) { /* do something */ }
  override fun onFailed(e: Exception) { /* do something */ }
}
val door = vehicle.getDoor()
door.registerDoorPosition(doorPositionListener = callbackListener)
door.unregisterDoorPosition(doorPositionListener = callbackListener)
```

```kotlin
// Capability 확인
door.checkDoorPositionCapabilities(
  onComplete = { config, areaMap ->
    when (config.accessType) {
      AccessType.READ, AccessType.READ_WRITE -> { /* 사용 가능 */ }
      else -> { /* NONE or WRITE only — disable UI */ }
    }
  },
  onFailed = { e: Exception -> if (e is VehicleException) { /* e.errorCode */ } }
)
```

- Odometer/Fuel 리스너의 정확한 이름과 콜백 시그니처는 Dokka 상세 페이지가 비어 있어 문서에서 확인 안 됨. 사이트맵 기준 리스너 패키지는 `ai.pleos.playground.vehicle.listener`다.
- 에러코드에 `ERROR_DRIVING_RESTRICTED`(주행 중 기능 제한)와 `ERROR_SECURITY_PERMISSION` 등이 있다.

---

## 3. FusedLocation SDK (v1.1.3)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro , .../location-data-access , .../initialization-and-release , .../declaring-permissions

- API: `isSupported()`, `initialize()`, `getLastKnownLocation()`, `registerFusedLocationCallback(listener)`, `unregisterFusedLocationCallback(listener)`, `release()`, `showLocationAccessBlockedPopup()`, **`addProximityAlert()` / `removeProximityAlert()`**(진입/이탈 지오펜스: 위도, 경도, radiusMeters, ENTER/EXIT)
- `LocationData`: latitude, longitude, altitude, heading, confidence, timestampMillis. **속도(speed) 필드는 없다.**
- 업데이트 주기/간격 설정 API: 문서에서 확인 안 됨
- 권한: `pleos.car.permission.FUSED_LOCATION` (dangerous)
- **백그라운드 가능 여부: 문서에서 확인 안 됨.** 문서는 Android `ACCESS_BACKGROUND_LOCATION` 등 포그라운드·백그라운드 위치 정책을 "이 SDK 문서에서 별도로 보장하지 않는다"고 명시한다. 예시 코드는 `Application` 범위에서 인스턴스를 관리한다.
- 지오펜스(addProximityAlert)는 상하차지 도착/출발 감지로 대기시간을 기록하는 데 쓸 만하다.

```kotlin
class MyApplication : Application() {
    lateinit var fusedLocation: FusedLocation
        private set
    override fun onCreate() {
        super.onCreate()
        fusedLocation = FusedLocation(this)
        if (fusedLocation.isSupported()) {
            fusedLocation.initialize()
        } else {
            // 위치 관련 UI와 기능을 비활성화합니다.
        }
    }
}

val listener = object : FusedLocationListener {
    override fun onChanged(location: LocationData?) {
        location ?: return
        // Use the location data.
    }
    override fun onError(exception: Exception) { /* Handle the error. */ }
}
fusedLocation.registerFusedLocationCallback(listener)
```

---

## 4. NaviHelper SDK (v3.0.5)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro , .../destinations , .../routes , .../waypoint-settings , .../route-with-waypoints , .../event-listeners , .../initialization-and-release , .../error-handling

- 동작 방식: 요청은 Pleos Connect의 Navigation 앱으로 전달되고, 결과는 `NaviHelperEventListener` 콜백으로 비동기로 받는다. 정보 조회 API는 값을 직접 반환하지 않는다.
- **목적지 설정(길안내 요청): 가능.** `requestRoute(RouteInfo)`, `requestRouteWithWaypoints(RouteWithWaypointsRequest)`, `cancelRoute()`, `requestReRoute()`, `changeRouteOption(RouteOption)`(RECOMMENDED / SHORT_DISTANCE / SHORT_TIME / FREE / HIGHWAY / COMFORTABLE / SCHOOL_ZONE)
  - 경유지는 최대 2개(`WaypointIndex.FIRST/SECOND`): `addWaypoint`, `removeWaypoint`, `getWaypointInfo`
  - **화물차 전용 경로 옵션(높이, 중량 제한)은 RouteOption에 없다.**
- **목적지, 남은 거리, ETA 조회: 가능.**
  - `getDestinationInfo()` → `onDestinationInfo(DestinationInfo)`: name, address, lat/lon, **remainDistance, remainTime**, tollFare
  - `onTripInfo(TripInfo)`: distance, **eta**, energyConsumption, estimatedSocAtDest, summary. 관련 요청은 `requestTrip(TripRequest)`이고 권한은 NAVI_ROUTE다.
  - `WaypointInfo.remainDistance/remainTime`, `onRouteStarted(RouteStartInfo: distance, duration, tollFare)`
  - `getRouteStateInfo()` → isRouting, naviMode(ROUTE_GUIDING/SAFE_GUIDING/SIMULATION_GUIDING/NOT_WORKING), routeOption, trafficStatus
  - `getCurrentLocationInfo()` → lat/lon/heading/address
  - `getTBTInfo()`, `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onNavigationStatus(NavigationStatus: FREE_DRIVE/ROUTE_DRIVE/INACTIVE/INITIALIZING)`, `onDrivingInfo`
  - 도착 이벤트: `onDestinationArrived(DestinationArrivedInfo)`, `onWayPointArrived`
- 현재 경로 전체(폴리라인) 조회: 문서에서 확인 안 됨. `TripSummary.routePoints`(RoutePointInfo)는 있다.
- 단위: 거리는 미터, 시간은 초 (에뮬레이터 확인: 75km → 75000, 1시간 42분 → 6120)
- 에뮬레이터에서 확인한 동작 (2026-10-22, 내비 서비스 3.0.4)
  - `poiId = ""`, 좌표만 넣은 `requestRoute`도 경로 후보 화면이 뜨고, 잠시 뒤 1번 경로로 안내가 시작된다
  - 경로가 정해지기 전 `onDestinationInfo`는 모두 0으로 온다. 0은 "아직 모름"으로 다뤄야 한다
  - 이벤트 순서: `onRouteStarted`(전체 거리·시간) → `onRouteEnded`(경로 탐색 끝, 안내 종료가 아님) → `onNavigationStatus(ROUTE_DRIVE)`
  - 내비에서 [안내 종료]를 누르면 `onRouteCancelled` → `onNavigationStatus(FREE_DRIVE)`
  - 앱이 `FLAG_ACTIVITY_LAUNCH_ADJACENT`로 내비를 띄워도 옆 창으로 나뉘지 않고 앱 영역 전체를 덮는다. 화면 나누기는 사용자가 손잡이를 끌어서 한다
- 기타: `requestNavigationScreen(...)`(검색, 경로 개요, 주변 충전소 등 내비 화면 열기), 북마크, 최근 목적지 조회
- 권한: 조회는 NAVI_ROUTE, 경로 설정은 NAVI_ROUTE_SEARCH, 화면 요청은 NAVI_CUSTOM_MAP

```kotlin
val naviHelper = NaviHelper(applicationContext)
naviHelper.initialize(
    onConnected = { /* 리스너를 등록하고 API를 호출합니다. */ },
    onFailed = { throwable -> /* 초기 연결 재시도가 모두 실패 */ }
)
naviHelper.addListener(naviEventListener)

naviHelper.requestRoute(
    RouteInfo(
        latitude = 37.48544722,
        longitude = 127.03636666,
        displayLongitude = null,
        displayLatitude = null,
        poiName = "42dot",
        poiId = "36khp37hh8gqcm5nqhe5",
        poiSubId = "0",
        address = "서울시 강남구 남부순환로 2621"
    )
)
naviHelper.getDestinationInfo()
override fun onDestinationInfo(destinationInfo: DestinationInfo) {
    Log.d(logTag, "onDestinationInfo $destinationInfo")
}
```

- 주의: `poiId`가 필수인지, 좌표만으로 요청할 수 있는지는 문서에서 확인 안 됨. 예시는 모두 poiId를 포함한다.
- v3.0.5 Breaking: 좌표 인자 순서가 (lat, lon)으로 바뀌었고, `totalDistance/totalTime`이 `remainDistance/remainTime`으로 바뀌었다.

---

## 5. TextToSpeech / SpeechToText SDK (v3.3.1)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro , .../TextToSpeech/start-and-stop , .../TextToSpeech/set-locale , .../TextToSpeech/app-registration , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro , .../SpeechToText/start-and-stop , .../SpeechToText/result-listeners , .../SpeechToText/set-locale , .../SpeechToText/sending-audio-data

### 공통
- 모드: `HYBRID`(기본값, 서버 우선이고 네트워크 실패 시 온디바이스로 자동 전환), `SERVER`, `ON_DEVICE`(앱 등록 불필요, 오프라인)
- 서버 모드는 `registerApp(clientID, clientSecret, OnServerConnectionListener)`가 필요하다. Playground My Project에서 발급받는다.
- **한국어 지원: 예.** 지원 locale은 ko-KR, en-US, fr-CA, es-US, es-MX
- 권한: `TTS_SERVICE` / `STT_SERVICE`

### TTS
```kotlin
val textToSpeech = TextToSpeech(applicationContext, Mode.HYBRID)
textToSpeech.initialize(object : OnInitializeListener {
    override fun onSuccess() { }
    override fun onFailure(errorType: ErrorType) { }
})
textToSpeech.addEventListener(eventListener) // onReady/onStart/onDone/onStop/onError/onUpdatedRms
textToSpeech.setLocale(Locale.KOREA)
textToSpeech.speak("안녕하세요. 무엇을 도와드릴까요?")
textToSpeech.speak("안녕하세요. ", isFinal = false) // 스트리밍 분할 전송
textToSpeech.stop()
```
- 그 외 API(사이트맵 기준): `setAudioAttributes`, `setAudioZoneId`, `setSpeakerType`. 화자 종류는 문서에서 확인 안 됨.
- 음성 안내 재생 전 150~200ms 선행 묵음을 권장한다. 출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide

### STT
```kotlin
val speechToText = SpeechToText(applicationContext, Mode.HYBRID)
speechToText.initialize()
speechToText.addListener(resultListener)
speechToText.setLocale(Locale.KOREA)
speechToText.request()   // 차량 마이크 활성화 후 인식 시작
speechToText.stop()

private val resultListener = object : ResultListener {
    override fun onReady() = Unit
    override fun onStartedRecognition() = Unit
    override fun onUpdated(stt: String, completed: Boolean) { /* 중간/최종 결과 */ }
    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
    override fun onUpdatedRms(rms: Double) = Unit
    override fun onEndedRecognition() = Unit
    override fun onError(errorType: ErrorType) { }
}
```
- 중간 결과(partial)는 지원한다(`onUpdated(stt, completed=false)`). EPD(끝점 검출)도 지원한다.
- **연속 인식(continuous) 모드: 문서에서 확인 안 됨.** `request()` 한 번이 인식 세션 하나다. 필요하면 앱이 다시 `request()`를 호출해야 할 것으로 보인다(추정).
- **웨이크워드(호출어): STT SDK에 없다(문서에서 확인 안 됨).** STT는 텍스트 변환만 하고 명령 실행은 하지 않는다. 문서는 음성 명령으로 앱 기능을 실행하려면 **Gleo Interface**를 쓰라고 안내한다.
- `sendAudio(ByteArray)` / `completeAudioSend()`: 앱이 가진 PCM(16kHz/16bit/mono 예시)을 인식한다. **ON_DEVICE 모드에서만** 쓸 수 있다.
- 에뮬레이터 마이크 설정이 필요하다(1.5 참고).

### Gleo Interface (시스템 음성비서 연동, 웨이크워드 대체 경로)
출처: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts , .../app-capability , .../deliver-schema , .../action_callback , .../examples
- 앱 `assets/schema.json`에 function(최대 3개)을 정의하고, `invokeWord`(예: "리마인더")로 앱을 식별한다. Gleo LLM이 function call을 만들어 앱을 Intent로 호출한다.
- `component: "activity"`(startActivity) 또는 `"service"`(sendBroadcast, UI 없는 백그라운드 처리)
- 앱은 Broadcast Callback(status, mode: voice/silent/beep, data)으로 결과를 돌려준다.
- 스키마는 Pleos 검토와 승인을 거쳐야 활성화된다.
- 예: "다음 배차 수락해줘" → `ACCEPT_DISPATCH` function. Gleo 자체 호출어(wake word)가 무엇인지는 문서에서 확인 안 됨.
- LLM SDK는 제공이 종료됐다(2026-10 릴리즈 노트).

---

## 6. 그 외 앱에 쓸 만한 것 (목록만)

- **AAOS Car API (`CarPropertyManager`)**: 속도(PERF_VEHICLE_SPEED), 기어 등 표준 차량 속성. https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api
- **AAOS `CarUxRestrictionsManager`**: 주행 규제 상태 확인. 대기/정차 판단 보조로도 쓸 수 있다. https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- **Gleo Interface**: 음성 명령으로 앱 function을 호출한다(5절).
- **Vehicle SDK Safety 텔테일, Seat occupancy**: 주차 브레이크 경고등과 운전석 탑승 여부. 대기 상태 판단 보조용.
- **알림**: 별도 Notification SDK는 없다. Android 시스템 알림을 쓰고, 디자인 가이드에 System/Vehicle Notification 컴포넌트가 있다. https://document.pleos.ai/docs/connect/guide/docs-design/components/notification/system-notification
- **미디어**: Music/Audio 앱은 MediaBrowserService와 MediaSession이 필수다. "미디어 센터"(MBS 템플릿)를 권장한다. https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide
- **오디오 포커스 정책** (TTS 재생 시 준수): https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development
- **CCP 로터리 컨트롤러 대응**: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/ccp-rotary-controller-development
- 참고: 별도 제품 라인으로 Pleos Platform SDK(`/api-reference/pleos-platform-sdk`), Fleet API, Vehicle Data API(`/api-reference/vehicle-data-api`), SDV OS API가 있다. Pleos Connect 차량 앱용 SDK와는 별개이고, 이번 조사에서는 다루지 않았다.

---

## 7. 문서에서 확인되지 않은 항목 (후속 확인 필요)

1. Vehicle SDK의 속도, 기어, 시동(IGN) API. 이 신호들은 AAOS CarPropertyManager를 쓰라고 안내되지만, Pleos에서 필요한 권한과 승인 절차는 미기재
2. Odometer/Fuel 값의 단위, 리스너 시그니처, 업데이트 주기(Dokka 상세가 빈 페이지)
3. FusedLocation 백그라운드 수신 보장 여부, 업데이트 주기 설정
4. 백그라운드/Foreground Service 허용 정책
5. 제품 앱의 targetSdk 요구값
6. STT 연속 인식, 웨이크워드. Gleo 호출어
7. Virtual Console의 차량 속도, 연료량, 시동, GPS 시뮬레이션 방법
8. 화물차(상용차) 모델 지원, 화물차 경로 옵션
9. 한국 지역 주행 중 제한표("그외 지역"이 KOR에 적용되는지)
10. Nexus 저장소 접근에 인증이 필요한지
