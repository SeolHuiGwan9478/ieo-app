# Ieo 브랜드

![미리보기](logo/preview.png)

## 로고 파일

모든 파일은 배경이 투명한 SVG이고, 글자는 path로 변환되어 있어 글꼴 설치 없이 똑같이 보임.
파일 이름의 색이 로고의 색. **black은 밝은 배경(라이트 모드)용, white는 어두운 배경(다크 모드)용**.

| 종류 | 라이트 모드 | 다크 모드 | 용도 |
|---|---|---|---|
| 아이콘 | `logo/icon/ieo_icon_black.svg` | `logo/icon/ieo_icon_white.svg` | 앱 아이콘, 프로필 이미지 |
| 심볼 | `logo/icon/ieo_symbol_black.svg` | `logo/icon/ieo_symbol_white.svg` | 배경 사각형 없이 심볼만 쓸 때 |
| 텍스트 로고 | `logo/wordmark/ieo_wordmark_black.svg` | `logo/wordmark/ieo_wordmark_white.svg` | 기본 로고 |

- 아이콘과 텍스트 로고를 붙인 조합(락업)은 쓰지 않음. 둘을 함께 놓아야 하면 따로 배치함
- 이전 시안은 `logo/drafts/`에 버전별로 보관

## 색상 팔레트

컨셉: **흑백 위에 신호등.** 브랜드, 화면, 글자, 버튼은 모두 흑백으로 하고, 운행 상황을 알릴 때만 상태색을 씀. 운전 중에 색이 보이면 곧 "무슨 일이 생겼다"는 신호가 되도록 색을 아껴 씀.

### 기본색 (흑백)

| 이름 | 값 | 쓰임 |
|---|---|---|
| Black | `#000000` | 다크 모드 배경, 라이트 모드 글자·로고 |
| Gray 950 | `#121212` | 다크 모드 화면 바탕 |
| Gray 900 | `#1E1E1E` | 다크 모드 카드 |
| Gray 800 | `#2C2C2C` | 다크 모드 보조 버튼, 구분선 |
| Gray 500 | `#6B6B6B` | 비활성 요소 |
| Gray 400 | `#A3A3A3` | 다크 모드 보조 글자 |
| Gray 100 | `#EDEDED` | 라이트 모드 카드, 구분선 |
| White | `#FFFFFF` | 라이트 모드 배경, 다크 모드 글자·주요 버튼 |

### 상태색

차량 화면은 대부분 다크 모드라서 다크 값을 먼저 정함. 라이트 값은 흰 배경에서 글자로 읽힐 만큼 진하게 잡음.

| 상태 | 다크 모드 | 라이트 모드 | 쓰임 |
|---|---|---|---|
| 운행 (Blue) | `#60A5FA` | `#1D4ED8` | 주행 중, ETA, 경로, 다음 배차 안내 |
| 대기 (Amber) | `#FBBF24` | `#B45309` | 대기 타이머, 청구 가능 대기비 |
| 완료·수익 (Green) | `#4ADE80` | `#15803D` | 하차 완료, 실수익, 지급 완료 |
| 경고 (Red) | `#F87171` | `#B91C1C` | 휴게 필요, 미수금, 지급 지연 |

### 사용 규칙

- 주요 버튼(수락 등)은 다크 모드에서 흰 바탕에 검정 글자, 라이트 모드에서 검정 바탕에 흰 글자
- 상태색은 한 화면에 두 가지 이하로 씀
- 색만으로 상태를 구분하지 않고 글자("대기 중")나 아이콘을 함께 씀 (색약 사용자 고려)

## 규칙

- 텍스트 로고는 소문자와 마침표를 쓴 **`ieo.`**. 글꼴은 **Outfit ExtraBold(800)**이고 자간을 -0.03em 좁힘
- 문장 안에서는 `Ieo`로 씀
- 아이콘 의미: 막대는 지금 하는 운행, 점 두 개는 이어지는 다음 일. 멀어질수록 점이 작아짐
- Android 앱 아이콘은 검정 배경에 흰 심볼 (`android/app/src/main/res/mipmap-anydpi/`)
- Outfit은 SIL Open Font License 1.1이라 로고 제작과 상업적 이용이 가능함

## 다시 만들기

로고를 수정하려면 `scripts/make_brand.py`의 설정값(`TEXT`, `WEIGHT`, `TRACKING`)을 고친 뒤 다시 생성함. Outfit 가변 글꼴 파일과 fonttools가 필요함.

```bash
cd design
python3 -m venv .venv && .venv/bin/pip install fonttools
curl -L -o Outfit.ttf "https://github.com/google/fonts/raw/main/ofl/outfit/Outfit%5Bwght%5D.ttf"
.venv/bin/python scripts/make_brand.py Outfit.ttf logo
.venv/bin/python scripts/preview.py logo preview.svg
```
