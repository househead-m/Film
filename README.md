# 필름카메라 앱 v2 - LUT 실시간 필터 버전

인스타 스토리처럼 **카메라를 보는 화면 자체에 필터가 실시간으로 입혀지고**,
좌우로 스와이프하면 필터가 바뀌는 버전이에요. 찍은 후에도 다시 스와이프해서 필터를 바꿀 수 있어요.

## 이번 버전에서 바뀐 것
- 색보정 방식: 단순 ColorMatrix → **진짜 LUT(.cube 파일 기반)** 로 변경
- 카메라 프리뷰: 실시간으로 필터가 입혀진 화면을 보면서 촬영
- 좌우 스와이프로 필터 전환 (하드컷)
- 전면/후면 카메라 전환 버튼 추가

## 1단계: 새 프로젝트 만들기 (처음 하시는 거면)
지난 가이드와 동일해요.
1. Android Studio 설치
2. New Project → **Empty Views Activity** → Kotlin, 패키지명 `com.example.filmcamera`, Min SDK 24 이상

## 2단계: 파일 교체/추가하기

**교체할 파일** (내 프로젝트에 원래 있는 파일을 덮어쓰기)
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/example/filmcamera/MainActivity.kt`

**새로 추가할 파일** (`com.example.filmcamera` 폴더에 새 Kotlin 파일로)
- `LutFilter.kt`
- `LutPresets.kt`
- `LutBaker.kt`

**LUT 텍스처 이미지 추가하기 (중요!)**
`app/src/main/res/drawable-nodpi/` 라는 폴더를 새로 만들고, 그 안에 아래 2개 파일을 넣어야 해요.
- `lut_jejucine_w.png`
- `lut_jejucine_s.png`

> 폴더 만드는 법: `res` 폴더 우클릭 → New → Directory → 이름에 `drawable-nodpi` 입력 → 그 안에 두 PNG 파일을 드래그해서 넣기 (탐색기에서 직접 복사해도 됨)

## 3단계: Sync + 실행
1. 위쪽 "Sync Now" 눌러서 GPUImage 라이브러리 다운로드
2. ▶ Run 버튼으로 실제 폰에 설치해서 테스트 (에뮬레이터는 카메라 성능 확인에 부적합)

## 지금 들어있는 기능
- 카메라 화면 자체에 필터가 실시간으로 보임 (제주시네 W / 제주시네 S, 2종)
- 화면을 좌우로 스와이프하면 필터가 바로 바뀜 (하드컷)
- 셔터를 누르면 지금 보이는 필터 그대로 촬영
- 촬영 후에도 스와이프로 다른 필터로 바꿀 수 있음
- 우측 상단 버튼으로 전면/후면 카메라 전환

## 필터를 추가하고 싶을 때
1. 새 `.cube` 파일을 저한테 올려주시면, 텍스처 PNG로 변환해드릴게요
2. 변환된 PNG를 `res/drawable-nodpi/`에 추가
3. `LutPresets.kt`의 목록에 한 줄만 추가:
   ```kotlin
   LutPreset("필터이름", R.drawable.새파일이름, LUT크기),
   ```

## 알아두면 좋은 것 (성능)
- 실시간 프리뷰는 GPU(OpenGL)로 처리해서 부드럽게 돌아가야 정상이에요. 만약 화면이 계속 끊기면 그대로 알려주세요, 원인을 같이 찾아봐요.
- 촬영 후 저장 버튼을 눌렀을 때 살짝(반 박자) 멈칫하는 건 정상이에요 — 고화질 원본 사진에 필터를 입히는 계산(CPU)이 그 순간 일어나기 때문이에요.

## 막히는 부분이 생기면
에러 메시지를 그대로 복사해서 저한테 보여주세요.
