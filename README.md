# Ieo

화물차 기사를 위한 차량용 운행 비서. 현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작.

## 구조

| 경로 | 내용 |
|---|---|
| `android/` | 차량 앱 (Kotlin, Pleos Connect SDK) |
| `server/` | 백엔드 (Java 21, Spring Boot 4) |
| `docs/` | 기획서, 도메인별 요구사항 (`docs/requirements/`) |
| `research/` | 시장조사, SDK 조사 |
| `design/` | 로고, 브랜드 가이드 |

## 서버 실행

```bash
cd server
./gradlew bootRun
```

- 기본 프로필은 `local`이고 H2 파일 DB(`server/data/`)를 씀
- 헬스체크: http://localhost:8080/actuator/health
- H2 콘솔: http://localhost:8080/h2-console

## 앱 실행

1. Android Studio에서 `android/` 폴더를 연다.
2. Pleos Connect Emulator(API 34, Automotive)를 설치하고 AVD를 만든다. 자세한 방법은 [Pleos 공식 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 참고한다.
3. `app`을 실행한다.

차량 신호(기어, 주행거리 등)는 Virtual Console로 바꿀 수 있다.

```bash
adb forward tcp:8088 tcp:8088
```

포트를 연결한 뒤 브라우저에서 http://127.0.0.1:8088/ 을 연다.

SDK 조사 내용은 [research/02_pleos_sdk.md](research/02_pleos_sdk.md)에 있다.
