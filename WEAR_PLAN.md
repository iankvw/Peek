# Peek Wear OS 컴패니언 앱 (`:wear` 모듈) 상세 설계 문서

> **문서 목적:** 기존 안드로이드 폰 앱(`com.iank.im.peek`, `:app` 모듈)에 Wear OS 컴패니언 모듈(`:wear`)을 추가하기 위한 아키텍처, Gradle/Manifest 구성, 디자인 시스템, Room DB 부분 조회, Wearable Data Layer 통신 프로토콜, UI/UX 흐름 및 Google Play 정책 준수 사항을 정의한다.
>
> **작성 기준:** 현재 레포지토리의 `:app` 모듈 구현(`settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `ConversationDao`, `MessageDao`, `ConversationRepositoryImpl`, `SendChatMessageUseCase`, `BuildApiPayloadUseCase`, `AiGenerationService`, `SettingsRepository`, `SettingsDataStore`, `ImagePreprocessor`, `InternalImageStorage`, `Color.kt`, `strings.xml`)을 직접 분석하여 작성하였다.

---

## 목차

1. [아키텍처 개요 및 핵심 원칙](#1-아키텍처-개요-및-핵심-원칙)
2. [모듈 구조 및 Gradle / Manifest 설계](#2-모듈-구조-및-gradle--manifest-설계)
3. [디자인 시스템 및 컬러 테마 통일](#3-디자인-시스템-및-컬러-테마-통일)
4. [Room DB 부분 조회(`LIMIT N + 1` / `LIMIT M + 1`) 및 Repository 확장](#4-room-db-부분-조회limit-n--1--limit-m--1-및-repository-확장)
5. [폰-워치 통신 브릿지 및 프로토콜 명세](#5-폰-워치-통신-브릿지-및-프로토콜-명세)
6. [워치 UX 흐름 및 화면별 상세 설계](#6-워치-ux-흐름-및-화면별-상세-설계)
7. [Google Play 정책 대응 (면책 문구 · 응답 신고 · 개인정보처리방침)](#7-google-play-정책-대응-면책-문구--응답-신고--개인정보처리방침)
8. [단계별 구현 로드맵 및 체크리스트](#8-단계별-구현-로드맵-및-체크리스트)

---

## 1. 아키텍처 개요 및 핵심 원칙

### 1.1 Thin Companion 아키텍처
1. **워치의 외부 AI API 직접 통신 금지:**
   - `:wear` 모듈은 `Retrofit`, `OkHttp`, `SecureKeyStorage`(Google Tink)를 포함하지 않으며, 외부 AI 공급자(Gemini, OpenAI 호환)와 직접 통신하지 않는다.
   - 모든 API 키, API 프리셋, 모델 그룹, 시스템 프롬프트, 대화 내역 및 원본 이미지는 **폰(`:app`)을 단일 진실 공급원(Single Source of Truth)** 으로 관리한다.
2. **기존 폰 생성 파이프라인 100% 재사용:**
   - 워치에서 전송한 질문·재시도·취소 요청은 폰의 `WearBridgeListenerService`가 수신하여 기존 `SendChatMessageUseCase`, `BuildApiPayloadUseCase`, `AiGenerationService` (Foreground Service `dataSync`), `ActiveSendRegistry`, `ConversationRepositoryImpl` 파이프라인으로 처리한다.
   - 따라서 워치에서 시작한 대화와 폰에서 시작한 대화는 동일한 `peek_database`(Room DB)에 저장되며, 폰에 설정된 컨텍스트 유지 범위(`ContextRetentionMode`), 오래된 이미지 자동 텍스트 대치(`autoTextReplaceOldImages`), 백그라운드 캡션(`backgroundCaptionEnabled`), 시스템 지침(`systemPrompt`) 규칙이 동일하게 적용된다.
3. **워치 카메라 기능 완전 제외:**
   - `:wear` 모듈은 CameraX 의존성과 `CAMERA` 권한, `<uses-feature android:name="android.hardware.camera.any" />` 선언을 포함하지 않는다. 워치는 기존 대화의 사진 첨부를 저용량 썸네일로 확인하고 온디맨드 고화질 뷰어로 확대 열람하며 텍스트/음성으로 질문 및 후속 대화를 이어가는 데 집중한다.
4. **블루투스 대역폭 및 배터리 최적화:**
   - 대화 목록(`LIMIT N + 1`)과 메시지 목록(`LIMIT M + 1`)은 커서 기반 부분 조회만 수행해 필요한 데이터만 전송한다.
   - `MessageClient`/`DataClient`의 100KB 페이로드 제한을 준수하고, 이미지 바이너리(썸네일 및 확대 이미지)는 `ChannelClient` 스트리밍으로만 온디맨드 전송한다.

### 1.2 전체 시스템 상호작용 흐름

```mermaid
flowchart LR
    subgraph WATCH [":wear 모듈 (Wear OS 3.0+)"]
        W_UI["Wear Compose UI<br/>(Home / Chat / Viewer / Settings)"]
        W_DS["WearSettingsDataStore<br/>(입력방식 / 보기화질 로컬 저장)"]
        W_CACHE["WearImageCache<br/>(썸네일 · 확대 이미지 캐시)"]
        W_CLIENT["WearPhoneBridgeClient<br/>(MessageClient / DataClient / ChannelClient)"]
        W_UI --> W_DS
        W_UI --> W_CACHE
        W_UI --> W_CLIENT
    end

    subgraph D_LAYER ["Google Play Services Wearable Data Layer"]
        MC["MessageClient<br/>(경량 RPC & 실시간 상태 푸시)"]
        DC["DataClient<br/>(/peek/state/config 동기화)"]
        CC["ChannelClient<br/>(이미지 바이너리 스트리밍)"]
    end

    subgraph PHONE [":app 모듈 (Android Phone)"]
        P_SVC["WearBridgeListenerService<br/>(WearableListenerService)"]
        P_SYNC["WearStateSyncManager<br/>(Room/Settings 변경 감지 푸시)"]
        P_IMG["WearImageStreamHelper<br/>(ImagePreprocessor 리사이징)"]
        P_UC["SendChatMessageUseCase<br/>+ AiGenerationService"]
        P_DB["PeekDatabase (Room)<br/>& SettingsDataStore"]
        P_SVC --> P_UC
        P_SVC --> P_IMG
        P_SVC --> P_DB
        P_UC --> P_DB
        P_DB --> P_SYNC
    end

    W_CLIENT <--> MC
    W_CLIENT <--> DC
    W_CLIENT <--> CC
    MC <--> P_SVC
    MC <-- P_SYNC
    DC <-- P_SYNC
    CC <--> P_IMG
```

---

## 2. 모듈 구조 및 Gradle / Manifest 설계

### 2.1 `settings.gradle.kts`
`:wear` 모듈을 루트 프로젝트에 포함한다.

```kotlin
rootProject.name = "peek"
include(":app", ":wear")
```

### 2.2 `gradle/libs.versions.toml` 추가 정의
기존 `agp = "8.10.1"`, `kotlin = "2.1.10"`, `ksp = "2.1.10-1.0.31"`, `hilt = "2.56.2"`, `composeBom = "2025.08.00"`, `coil = "3.2.0"`, `datastore = "1.1.4"`, `kotlinxSerializationJson = "1.8.0"`, `coroutines = "1.10.1"` 과 호환되는 Wear OS 전용 의존성을 추가한다.

```toml
[versions]
# ... 기존 버전 유지 ...
playServicesWearable = "19.0.0"
coroutinesPlayServices = "1.10.1"
wearCompose = "1.4.1"
wearComposeMaterial3 = "1.0.0-alpha36"
horologist = "0.6.23"
wearRemoteInteractions = "1.1.0"
wearInput = "1.2.0-alpha02"
coreSplashscreen = "1.0.1"

[libraries]
# ... 기존 라이브러리 유지 ...
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutinesPlayServices" }

# Wear Compose & Horologist
androidx-wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }
androidx-wear-compose-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "wearComposeMaterial3" }
androidx-wear-compose-navigation = { group = "androidx.wear.compose", name = "compose-navigation", version.ref = "wearCompose" }
androidx-wear-compose-ui-tooling = { group = "androidx.wear.compose", name = "compose-ui-tooling", version.ref = "wearCompose" }

horologist-compose-layout = { group = "com.google.android.horologist", name = "horologist-compose-layout", version.ref = "horologist" }
horologist-compose-material = { group = "com.google.android.horologist", name = "horologist-compose-material", version.ref = "horologist" }

# Wear Remote Interactions & Input
androidx-wear-remote-interactions = { group = "androidx.wear", name = "wear-remote-interactions", version.ref = "wearRemoteInteractions" }
androidx-wear-input = { group = "androidx.wear", name = "wear-input", version.ref = "wearInput" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
```

> **참고:** `androidx.wear.compose:compose-material3` 대신 안정화 버전 기반 `androidx.wear.compose:compose-material`과 병행/선택할 수도 있으나, 본 설계에서는 Material 3 Expressive 기반 최신 Wear UI(`ScreenScaffold`, `ScalingLazyColumn`, `AppScaffold`, `Button`, `FilledTonalButton`, `RadioButton` 등)와 `horologist-compose-layout` 조합을 표준으로 채택한다. (실제 구현 시 Compose BOM 버전과 충돌이 있을 경우 `compose-material` 1.4.1 및 `horologist-compose-material`로 1:1 치환 가능하도록 컴포넌트 추상화를 유지한다.)

### 2.3 `:wear/build.gradle.kts` 및 서명/배포 공유 설계
Play Services Wearable Data Layer는 **동일한 `applicationId`** 와 **동일한 서명 키(Signing Certificate)** 를 가진 폰 앱과 워치 앱 간에만 노드 통신을 허용한다.

- **`namespace`**: `"com.iank.im.peek.wear"` (R 클래스 및 패키지 충돌 방지)
- **`applicationId`**: `"com.iank.im.peek"` (`:app`과 완전히 동일)
- **서명 설정(`signingConfigs`) 공유**:
  - 루트 `build.gradle.kts` 또는 별도 `keystore.properties` / 공통 Gradle 컨벤션을 통해 `:app`과 `:wear`의 `release` 및 `debug` 서명 키를 동일하게 지정한다.
  - 개발 중에도 폰과 워치가 동일한 `debug.keystore`로 서명되어야 Data Layer 연결이 성립한다.
- **Play Store 멀티 APK/AAB `versionCode` 규칙**:
  - 동일 `applicationId`로 Play Console의 Wear OS 트랙과 폰 트랙에 동시 배포할 때 `versionCode`가 충돌하면 안 된다.
  - 권장 규칙: `:app`은 `1`, `2`, `3`… 형태(현재 `versionCode = 1`, `versionName = "1.0.0"`), `:wear`는 `100000 + appVersionCode` (예: `100001`, `versionName = "1.0.0"`).
- **SDK 및 컴파일 타겟**:
  - `compileSdk = 36`
  - `minSdk = 30` (Wear OS 3.0 이상)
  - `targetSdk = 36`
  - `JavaVersion.VERSION_17` / `JvmTarget.JVM_17` (`:app`과 동일)

```kotlin
// wear/build.gradle.kts (설계 명세)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.iank.im.peek.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iank.im.peek"
        minSdk = 30
        targetSdk = 36
        versionCode = 100001
        versionName = "1.0.0"
    }

    // signingConfigs는 :app과 동일한 release/debug 키스토어 공유
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Wear Compose & Horologist
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.compose.ui.tooling)
    implementation(libs.horologist.compose.layout)
    implementation(libs.horologist.compose.material)

    // Wearable Data Layer & Remote Interactions & Input
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.androidx.wear.input)

    // Local Settings & Serialization & DI & Image
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)
}
```

### 2.4 `:app/build.gradle.kts` 추가 의존성
기존 `:app` 의존성에 Wearable 통신 라이브러리만 추가한다.

```kotlin
implementation(libs.play.services.wearable)
implementation(libs.kotlinx.coroutines.play.services)
implementation(libs.androidx.wear.remote.interactions)
```

### 2.5 `AndroidManifest.xml` 구성

#### `:wear/src/main/AndroidManifest.xml`
- 카메라 권한(`CAMERA`) 및 `<uses-feature android:name="android.hardware.camera.any" />` **일절 제거**.
- 외부 API 직접 호출을 하지 않으므로 `INTERNET` 권한 불필요 (Wearable Data Layer를 통해서만 폰과 통신).
- `<uses-feature android:name="android.hardware.type.watch" />` 필수 선언.
- `com.google.android.wearable.standalone` = `false` (폰 컴패니언 의존 앱 명시).

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".WearApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Peek.Wear.Starting">

        <uses-library
            android:name="com.google.android.wearable"
            android:required="true" />

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:taskAffinity=""
            android:theme="@style/Theme.Peek.Wear.Starting">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 폰 -> 워치 실시간 이벤트 수신 (선택적 보조 채널; 포그라운드 외 상태 동기화) -->
        <service
            android:name=".data.bridge.WearClientListenerService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
                <data android:scheme="wear" android:host="*" android:pathPrefix="/peek" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

#### `:app/src/main/AndroidManifest.xml` 추가 등록
기존 `<application>` 내부에 Wearable Data Layer 수신 서비스를 등록한다.

```xml
<service
    android:name=".wearbridge.WearBridgeListenerService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
        <action android:name="com.google.android.gms.wearable.CHANNEL_EVENT" />
        <data android:scheme="wear" android:host="*" android:pathPrefix="/peek" />
    </intent-filter>
</service>
```

### 2.6 추가/신규 패키지 디렉터리 구조

```text
peek/
├── WEAR_PLAN.md
├── settings.gradle.kts                           # include(":app", ":wear")
├── gradle/libs.versions.toml                     # Wear Compose, Horologist, Wearable 의존성 추가
├── app/
│   └── src/main/java/com/iank/im/peek/
│       ├── data/local/db/dao/
│       │   ├── ConversationDao.kt                # getConversationsPage() 추가
│       │   └── MessageDao.kt                     # getMessagesPageWithAttachments() 추가
│       ├── data/repository/
│       │   └── ConversationRepositoryImpl.kt     # 커서 페이지네이션 메서드 추가
│       └── wearbridge/                           # [신규] 폰 측 Wear 브릿지 패키지
│           ├── WearProtocolModels.kt             # @Serializable 요청/응답/이벤트 DTO
│           ├── WearBridgeConstants.kt            # Path, 페이지 크기 상수 (N=10, M=15)
│           ├── WearBridgeListenerService.kt      # WearableListenerService (RPC/Channel 처리)
│           ├── WearStateSyncManager.kt           # 설정/프리셋/활성상태 DataClient & Event 푸시
│           ├── WearImageStreamHelper.kt          # 썸네일/온디맨드 리사이징 & ChannelClient 전송
│           └── WearReportDispatcher.kt           # 워치 신고 처리 (DB 마스킹 + 메일 연동)
└── wear/                                         # [신규] Wear OS 컴패니언 모듈
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml & values-ko/strings.xml
        └── java/com/iank/im/peek/wear/
            ├── WearApplication.kt                # @HiltAndroidApp
            ├── MainActivity.kt                   # SwipeDismissableNavHost 엔트리
            ├── di/
            │   └── WearModule.kt                 # DataClient/MessageClient/ChannelClient/DataStore/Json
            ├── domain/model/
            │   ├── WearModels.kt                 # ConversationItem, ChatMessageItem, TargetItem 등
            │   ├── WearImageQuality.kt           # ORIGINAL / HIGH_4000 / BALANCED_2000 / STANDARD_1000
            │   └── WearInputMode.kt              # TEXT_KEYBOARD(기본) / VOICE_FIRST
            ├── data/
            │   ├── bridge/
            │   │   ├── WearProtocolModels.kt     # :app과 동일 스키마 DTO
            │   │   ├── WearPhoneBridgeClient.kt  # Node 탐색, RPC 타임아웃, ChannelClient 수신
            │   │   └── WearClientListenerService.kt
            │   ├── cache/
            │   │   └── WearImageCache.kt         # 디스크 캐시 (thumb / full_{quality})
            │   └── datastore/
            │       └── WearSettingsDataStore.kt  # 보기 화질, 기본 입력 방식 저장
            └── ui/
                ├── theme/
                │   ├── Color.kt                  # OLED #000000, #1E1F20/#282A2C, #F5B800
                │   └── Theme.kt                  # PeekWearTheme
                ├── navigation/
                │   └── WearNavGraph.kt
                ├── home/
                │   ├── WearHomeViewModel.kt      # LIMIT 10+1 커서 페이지네이션 & 새 대화
                │   └── WearHomeScreen.kt         # 타이틀 -> 새 대화 -> 채팅 목록 -> 더보기 -> 설정
                ├── chat/
                │   ├── WearChatViewModel.kt      # LIMIT 15+1 역방향 페이지네이션, 실시간 갱신
                │   ├── WearChatScreen.kt         # 말풍선, 썸네일, 면책 문구, 입력 바
                │   ├── WearInputLauncher.kt      # 텍스트(RemoteInput) / 음성(RecognizerIntent) 런처
                │   └── WearReportScreen.kt       # AI 말풍선 길게 누르기 -> 신고 사유 선택
                ├── viewer/
                │   ├── WearImageViewerViewModel.kt
                │   └── WearImageViewerScreen.kt  # 온디맨드 화질 요청, 핀치/더블탭/크라운 줌
                └── settings/
                    ├── WearSettingsViewModel.kt
                    └── WearSettingsScreen.kt     # 화질, 기본 프리셋/그룹, 기본 입력, 개인정보처리방침
```

---

## 3. 디자인 시스템 및 컬러 테마 통일

폰 앱(`app/src/main/java/com/iank/im/peek/ui/theme/Color.kt`)의 다크 팔레트를 Wear OS 화면에 그대로 이식하여 브랜드 일관성과 OLED 전력 절감 효과를 동시에 달성한다.

### 3.1 컬러 팔레트 토큰 매핑

| 용도 | 폰 앱 토큰 (`app/.../Color.kt`) | Hex 값 | 워치 토큰 (`wear/.../Color.kt`) | 워치 UI 적용 대상 |
| :--- | :--- | :--- | :--- | :--- |
| **OLED 순수 검정 배경** | `PeekDarkBackground` | `#000000` | `PeekWearBackground` | 전체 `AppScaffold`/`ScreenScaffold` 배경, 이미지 뷰어 배경 |
| **보조 딤 서피스** | `PeekDarkSurface` | `#131314` | `PeekWearSurface` | 비활성/보조 배경 |
| **다크 그레이 카드/서피스** | `PeekDarkSurfaceContainer` | `#1E1F20` | `PeekWearSurfaceContainer` | 채팅 목록 카드, 설정 항목 카드, 신고된 메시지 숨김 카드 |
| **강조 다크 그레이 서피스** | `PeekDarkSurfaceContainerHigh` / `PeekDarkSurfaceVariant` | `#282A2C` | `PeekWearSurfaceContainerHigh` | AI(`ASSISTANT`) 말풍선, `더보기` 버튼, `설정` 버튼, 모델 배지 |
| **꿀벌 골드 포인트** | `PeekDarkPrimary` | `#F5B800` | `PeekWearPrimary` | `새 대화` 버튼, 전송/입력 CTA, 선택된 라디오 아이콘, 로딩 인디케이터 |
| **포인트 위 텍스트/아이콘** | `PeekDarkOnPrimary` | `#1A1400` | `PeekWearOnPrimary` | `새 대화` 버튼 내부 텍스트 및 아이콘 |
| **사용자 말풍선 배경** | `PeekDarkPrimaryContainer` | `#4A3800` | `PeekWearPrimaryContainer` | `USER` 말풍선 배경 |
| **사용자 말풍선 텍스트** | `PeekDarkOnPrimaryContainer` | `#FFE082` | `PeekWearOnPrimaryContainer` | `USER` 말풍선 본문 |
| **기본 본문 텍스트** | `PeekDarkOnBackground` / `PeekDarkOnSurface` | `#E3E3E3` | `PeekWearOnBackground` | 앱 타이틀, 대화 제목, AI 응답 본문 |
| **보조/메타 텍스트** | `PeekDarkOnSurfaceVariant` | `#C4C7C5` | `PeekWearOnSurfaceVariant` | 타임스탬프, 서브타이틀, AI 면책 문구 |
| **외곽선** | `PeekDarkOutlineVariant` | `#444746` | `PeekWearOutlineVariant` | 썸네일 카드 보더, 카드 구분 테두리 |
| **오류/중지/신고** | `PeekDarkError` / `PeekDarkErrorContainer` | `#FFB4AB` / `#93000A` | `PeekWearError` / `PeekWearErrorContainer` | 오류 말풍선, 중지 버튼, 삭제/신고 강조 |

### 3.2 원형 디스플레이(Round Watch) 레이아웃 가이드라인
- **표준 스캐폴드**: Wear Compose `AppScaffold` + `ScreenScaffold`를 사용하여 상단 `TimeText`와 우측 원형 `ScrollIndicator`(`PositionIndicator`)를 자동 배치하고, 스크롤 시 `TimeText`가 자연스럽게 페이드아웃되도록 한다.
- **곡률 여백(`ScalingLazyColumn`)**: Horologist `rememberResponsiveColumnState()`(또는 `ScalingLazyColumn` 기본 `AutoCenteringParams`)를 사용해 원형 화면 상하단 엣지에서 텍스트가 잘리지 않도록 보장한다.
- **로터리(크라운/베젤) 입력 지원**: 모든 `ScalingLazyColumn` 화면과 `WearImageViewerScreen`에서 회전형 입력(`rotaryScrollable`)을 활성화한다.
- **폰트 가독성**: AI 응답은 워치 가독성을 위해 폰트의 Markdown 스타일을 경량 평문/강조(`AnnotatedString`: 볼드·인라인 코드·리스트 정리)로 변환하여 작은 원형 화면에서도 줄바꿈과 스크롤이 쾌적하게 동작하도록 한다.

---

## 4. Room DB 부분 조회(`LIMIT N + 1` / `LIMIT M + 1`) 및 Repository 확장

워치의 요청마다 전체 DB를 읽어 블루투스로 내려보내는 비효율을 방지하기 위해, 폰의 `ConversationDao`, `MessageDao`, `ConversationRepository`에 **`LIMIT size + 1`** 기반 커서 페이지네이션 메서드를 추가한다.

### 4.1 대화 목록 부분 조회 (`ConversationDao` & `ConversationRepository`)

- **정책**:
  - 기본 페이지 크기 `N = 10` (조회 시 `limitPlusOne = 11`).
  - `conversations` 테이블에는 이미 `Index(value = ["updatedAt"])`가 존재한다. 동일한 `updatedAt` 타임스탬프를 가진 행이 존재할 때 누락/중복을 방지하기 위해 `(updatedAt, id)` 복합 커서를 사용한다.
  - 폰에서 시작한 대화와 워치에서 시작한 대화 모두 동일한 `conversations` 테이블에 저장되므로 별도 필터 없이 동일하게 최신순으로 조회된다.

```kotlin
// app/src/main/java/com/iank/im/peek/data/local/db/dao/ConversationDao.kt 추가
@Query(
    """
    SELECT * FROM conversations
    WHERE (:beforeUpdatedAt IS NULL)
       OR (updatedAt < :beforeUpdatedAt)
       OR (updatedAt = :beforeUpdatedAt AND id < :beforeId)
    ORDER BY updatedAt DESC, id DESC
    LIMIT :limitPlusOne
    """
)
suspend fun getConversationsPage(
    beforeUpdatedAt: Long?,
    beforeId: String?,
    limitPlusOne: Int
): List<ConversationEntity>
```

- **Repository 구현 로직 (`ConversationRepositoryImpl`)**:
  ```kotlin
  data class PagedConversations(
      val items: List<Conversation>,
      val hasMore: Boolean,
      val nextCursorUpdatedAt: Long?,
      val nextCursorId: String?
  )

  override suspend fun getConversationsPage(
      beforeUpdatedAt: Long?,
      beforeId: String?,
      pageSize: Int
  ): PagedConversations {
      val safeSize = pageSize.coerceIn(1, 30)
      val rows = conversationDao.getConversationsPage(
          beforeUpdatedAt = beforeUpdatedAt,
          beforeId = beforeId,
          limitPlusOne = safeSize + 1
      )
      val hasMore = rows.size > safeSize
      val sliced = rows.take(safeSize).map { it.toDomain() }
      val last = sliced.lastOrNull()
      return PagedConversations(
          items = sliced,
          hasMore = hasMore,
          nextCursorUpdatedAt = if (hasMore) last?.updatedAt else null,
          nextCursorId = if (hasMore) last?.id else null
      )
  }
  ```

### 4.2 메시지 목록 부분 조회 (`MessageDao` & `ConversationRepository`)

- **정책**:
  - 기본 페이지 크기 `M = 15` (조회 시 `limitPlusOne = 16`).
  - `messages` 테이블에는 `indices = [Index(value = ["conversationId", "position"], unique = true)]`가 이미 정의되어 있다.
  - 따라서 `position DESC`로 `beforePosition` 미만의 행을 `LIMIT M + 1`개 조회하면 인덱스 역순 스캔만으로 $O(\log K)$ 성능이 보장된다.
  - 최초 진입 시 `beforePosition = null`로 가장 최근 15개 메시지를 가져오고, `rows.size > M`이면 `hasOlder = true`로 설정한다. 워치에 내려줄 때는 화면 표시 순서(오래된 것 -> 최신 것, 즉 `position ASC`)로 뒤집어(`asReversed()`) 전송한다.

```kotlin
// app/src/main/java/com/iank/im/peek/data/local/db/dao/MessageDao.kt 추가
@Transaction
@Query(
    """
    SELECT * FROM messages
    WHERE conversationId = :conversationId
      AND (:beforePosition IS NULL OR position < :beforePosition)
    ORDER BY position DESC
    LIMIT :limitPlusOne
    """
)
suspend fun getMessagesPageWithAttachments(
    conversationId: String,
    beforePosition: Int?,
    limitPlusOne: Int
): List<MessageWithAttachments>
```

- **Repository 구현 로직 (`ConversationRepositoryImpl`)**:
  ```kotlin
  data class PagedMessages(
      val conversation: Conversation?,
      val itemsAscending: List<ChatMessage>,
      val hasOlder: Boolean,
      val nextBeforePosition: Int?
  )

  override suspend fun getMessagesPage(
      conversationId: String,
      beforePosition: Int?,
      pageSize: Int
  ): PagedMessages {
      val safeSize = pageSize.coerceIn(1, 30)
      val conversation = conversationDao.getById(conversationId)?.toDomain()
      val rowsDesc = messageDao.getMessagesPageWithAttachments(
          conversationId = conversationId,
          beforePosition = beforePosition,
          limitPlusOne = safeSize + 1
      )
      val hasOlder = rowsDesc.size > safeSize
      val itemsAscending = rowsDesc
          .take(safeSize)
          .asReversed()
          .map { it.toDomain() }
      val oldestPosition = itemsAscending.firstOrNull()?.position
      return PagedMessages(
          conversation = conversation,
          itemsAscending = itemsAscending,
          hasOlder = hasOlder,
          nextBeforePosition = if (hasOlder) oldestPosition else null
      )
  }
  ```

---

## 5. 폰-워치 통신 브릿지 및 프로토콜 명세

### 5.1 Wearable Data Layer 채널 전략

| 계층/API | 용도 | 페이로드 특성 | 선택 이유 |
| :--- | :--- | :--- | :--- |
| **`MessageClient`** | 요청/응답형 경량 RPC, 실시간 채팅 상태 변경 이벤트 푸시 | 100KB 이하 JSON (UTF-8) | 지연 시간이 낮고 연결된 노드가 깨어 있을 때 즉시 전달됨 (`sendRequest` 지원) |
| **`DataClient`** | 전역 설정 및 프리셋/그룹 목록, 활성 전송 중인 대화 ID 동기화 | 영속 DataItem (`/peek/state/config`) | 워치가 오프라인이었다가 재연결되어도 최신 설정 상태를 자동 동기화 |
| **`ChannelClient`** | 첨부 이미지 **썸네일** 및 **온디맨드 확대 이미지** 바이너리 전송 | 가변 크기 WebP/JPEG 스트림 | `MessageClient`/`DataClient`의 100KB 제한을 회피하고 메모리 OOM 없이 스트림 전송 |

### 5.2 통신 Path 명세표

| 구분 | Path | 방향 | 요청 DTO | 응답 / 페이로드 DTO |
| :--- | :--- | :--- | :--- | :--- |
| **대화 목록 조회** | `/peek/rpc/conversations/list` | Watch -> Phone (Request) | `WearConversationListRequest` | `WearConversationListResponse` |
| **대화 삭제** | `/peek/rpc/conversations/delete` | Watch -> Phone (Request) | `WearDeleteConversationRequest` | `WearSimpleAckResponse` |
| **메시지 조회** | `/peek/rpc/messages/list` | Watch -> Phone (Request) | `WearMessageListRequest` | `WearMessageListResponse` |
| **질문 전송** | `/peek/rpc/chat/send` | Watch -> Phone (Request) | `WearSendTurnRequest` | `WearSendTurnAcceptedResponse` |
| **응답 재시도** | `/peek/rpc/chat/retry` | Watch -> Phone (Request) | `WearRetryTurnRequest` | `WearSimpleAckResponse` |
| **생성 중지** | `/peek/rpc/chat/cancel` | Watch -> Phone (Request) | `WearCancelTurnRequest` | `WearSimpleAckResponse` |
| **응답 신고** | `/peek/rpc/chat/report` | Watch -> Phone (Request) | `WearReportMessageRequest` | `WearSimpleAckResponse` |
| **기본 타겟 변경** | `/peek/rpc/settings/select_target` | Watch -> Phone (Request) | `WearSelectTargetRequest` | `WearSimpleAckResponse` |
| **이미지 요청** | `/peek/rpc/image/request` | Watch -> Phone (Request) | `WearImageRequest` | `WearImageRequestAck` |
| **실시간 채팅 갱신** | `/peek/event/chat_updated` | Phone -> Watch (Push) | - | `WearChatUpdateEvent` |
| **전역 설정 동기화** | `/peek/state/config` | Phone -> Watch (`DataClient`) | - | `WearSyncedConfigDto` |
| **이미지 스트림** | `/peek/channel/image/{requestId}` | Phone -> Watch (`ChannelClient`) | - | Raw Image Bytes (`image/webp` or `image/jpeg`) |

> **요청-응답 패턴 구현 세부:** Play Services Wearable 18.0+의 `MessageClient.sendRequest(nodeId, path, bytes)` 및 `WearableListenerService.onRequest(nodeId, path, data): Task<ByteArray>` 를 사용하면 별도의 correlationId 매칭 코루틴 없이도 직관적인 동기식 RPC(`await()`)를 구현할 수 있다. 단, `sendRequest`는 기본 타임아웃이 있으므로, 장시간 소요되는 AI 응답 생성 자체는 **`sendRequest`로 즉시 `Accepted`(초기 `USER`/`LOADING` 메시지 ID)만 반환**하고, AI 생성 완료/실패/취소 결과는 백그라운드에서 **`MessageClient.sendMessage(..., "/peek/event/chat_updated", ...)`로 푸시**한다.

### 5.3 직렬화 DTO 명세 (`WearProtocolModels.kt`)

```kotlin
@Serializable
data class WearConversationListRequest(
    val beforeUpdatedAt: Long? = null,
    val beforeId: String? = null,
    val pageSize: Int = 10
)

@Serializable
data class WearConversationSummaryDto(
    val id: String,
    val title: String,
    val activeTargetId: String?,
    val activeTargetDisplayName: String?,
    val updatedAt: Long,
    val isGenerating: Boolean
)

@Serializable
data class WearConversationListResponse(
    val items: List<WearConversationSummaryDto>,
    val hasMore: Boolean,
    val nextCursorUpdatedAt: Long? = null,
    val nextCursorId: String? = null
)

@Serializable
data class WearMessageListRequest(
    val conversationId: String,
    val beforePosition: Int? = null,
    val pageSize: Int = 15
)

@Serializable
data class WearAttachmentDto(
    val id: String,
    val sortIndex: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalMimeType: String,
    val isTextReplaced: Boolean
)

@Serializable
data class WearMessageDto(
    val id: String,
    val conversationId: String,
    val position: Int,
    val turnIndex: Int,
    val role: String,           // "USER" | "ASSISTANT"
    val textContent: String,
    val status: String,         // "LOADING" | "SUCCESS" | "ERROR" | "CANCELLED"
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val servedTargetSummary: String? = null, // 예: "그룹명 · 프리셋 (모델ID)"
    val createdAt: Long,
    val isReported: Boolean = false,
    val attachments: List<WearAttachmentDto> = emptyList()
)

@Serializable
data class WearMessageListResponse(
    val conversationId: String,
    val conversationTitle: String,
    val boundTargetId: String?,
    val boundTargetDisplayName: String?,
    val messages: List<WearMessageDto>,      // position ASC (오래된 것 -> 최신 것)
    val hasOlder: Boolean,
    val nextBeforePosition: Int? = null
)

@Serializable
data class WearSendTurnRequest(
    val conversationId: String? = null,      // null이면 새 대화 시작
    val userText: String
)

@Serializable
data class WearSendTurnAcceptedResponse(
    val accepted: Boolean,
    val conversationId: String? = null,
    val boundTargetDisplayName: String? = null,
    val userMessage: WearMessageDto? = null,
    val assistantPlaceholder: WearMessageDto? = null,
    val rejectionCode: String? = null        // "NO_PRESET" | "MISSING_MODEL" | "MISSING_API_KEY" | "EMPTY"
)

@Serializable
data class WearRetryTurnRequest(
    val conversationId: String,
    val assistantMessageId: String
)

@Serializable
data class WearCancelTurnRequest(
    val conversationId: String
)

@Serializable
data class WearDeleteConversationRequest(
    val conversationId: String
)

@Serializable
data class WearReportMessageRequest(
    val messageId: String,
    val reason: String
)

@Serializable
data class WearSelectTargetRequest(
    val targetId: String
)

@Serializable
data class WearSimpleAckResponse(
    val success: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

@Serializable
data class WearChatUpdateEvent(
    val conversationId: String,
    val conversationTitle: String,
    val updatedMessage: WearMessageDto,
    val isGenerating: Boolean
)

@Serializable
data class WearTargetDto(
    val id: String,
    val displayName: String,
    val subtitle: String,       // 단일 프리셋: "GEMINI · gemini-2.5-flash" / 그룹: "폴백 · A, B"
    val isGroup: Boolean
)

@Serializable
data class WearSyncedConfigDto(
    val targets: List<WearTargetDto>,
    val selectedTargetId: String?,
    val activeGeneratingConversationIds: Set<String>,
    val updatedAt: Long
)

@Serializable
data class WearImageRequest(
    val requestId: String,
    val attachmentId: String,
    val kind: String,           // "THUMBNAIL" | "FULL"
    val qualityPreset: String   // "ORIGINAL" | "HIGH_4000" | "BALANCED_2000" | "STANDARD_1000"
)

@Serializable
data class WearImageRequestAck(
    val accepted: Boolean,
    val requestId: String,
    val channelPath: String,    // "/peek/channel/image/{requestId}"
    val mimeType: String? = null,
    val errorReason: String? = null
)
```

### 5.4 폰 측 `WearBridgeListenerService` 처리 로직

#### A. 질문 전송 (`/peek/rpc/chat/send`) 및 모델 바인딩 규칙
1. **빈 텍스트 검증**: `request.userText.isBlank()`이면 `rejectionCode = "EMPTY"` 반환.
2. **타겟 결정 (대화방 설정 제한 규칙 준수)**:
   - **기존 대화방(`request.conversationId != null`)**:
     - `val existingConv = conversationRepository.getConversation(request.conversationId)`
     - 해당 대화방에 이미 바인딩된 `existingConv?.activeTargetId`를 그대로 사용하여 `sendChatMessageUseCase.prepareExecutionTarget(existingConv?.activeTargetId)` 호출.
     - (참고: `SendChatMessageUseCase.prepareExecutionTarget`는 전달된 `targetId`가 유효하지 않거나 삭제된 경우에만 `settings.selectedPresetId` 또는 첫 번째 프리셋으로 안전하게 폴백한다.)
   - **새 대화방(`request.conversationId == null`)**:
     - `sendChatMessageUseCase.prepareExecutionTarget(null)`을 호출하여 현재 폰 `SettingsDataStore`에 저장된 기본 프리셋/그룹(`settings.selectedPresetId`)을 바인딩한다.
3. **실행 검증 실패 처리**:
   - `NoPresetConfigured` -> `rejectionCode = "NO_PRESET"`
   - `MissingModelId` -> `rejectionCode = "MISSING_MODEL"`
   - `MissingApiKey` -> `rejectionCode = "MISSING_API_KEY"`
   - 워치 UI는 해당 코드를 받으면 `"폰 앱 설정에서 API 프리셋(또는 키/모델)을 먼저 확인해 주세요"` 안내를 표시한다.
4. **기존 파이프라인 실행 및 실시간 상태 Push**:
   - `val resolvedConvId = sendChatMessageUseCase.sendNewTurn(request.conversationId, request.userText, emptyList(), executionTarget)` 호출.
   - `SendChatMessageUseCase.sendNewTurn` 내부 동작:
     - `commitTurn(...)`을 통해 폰 Room DB에 `USER` 메시지 + `ASSISTANT` (`status = MessageStatus.LOADING`) 플레이스홀더 즉시 저장.
     - `launchVisionRequest(...)`가 `AiGenerationService.startForGeneration(appContext)`를 호출해 백그라운드에서도 안전하게 네트워크 요청을 완료하며, `BuildApiPayloadUseCase`가 폰에 저장된 **`settings.systemPrompt`** (비어 있으면 `R.string.default_system_prompt`), `contextRetentionMode`, `autoTextReplaceOldImages`, `keepRecentImageTurns`를 그대로 적용한다.
   - `WearBridgeListenerService`는 DB에서 방금 커밋된 최신 2개 메시지(`USER`와 `LOADING` 상태의 `ASSISTANT`)를 읽어 `WearSendTurnAcceptedResponse`로 즉시 응답한다.
   - 동시에 `WearStateSyncManager`가 `ActiveSendRegistry.activeConversationIds` 및 `conversationRepository.observeMessages(resolvedConvId)`를 관찰(또는 응답 완료 콜백을 구독)하여, `assistantPlaceholder.id`의 상태가 `LOADING`에서 `SUCCESS` / `ERROR` / `CANCELLED`로 바뀌는 순간 `/peek/event/chat_updated` 메시지와 `/peek/state/config` DataItem을 모든 연결된 워치 노드에 푸시한다.

> **스트리밍 확장 참고:** 현재 `VisionAiRepository.generateVisionResponse(...)`는 완료된 `VisionResponseResult`(`Success` 또는 `Failure`)를 일괄 반환한다. 향후 `VisionAiRepository`에 SSE 토큰 스트리밍이 추가되더라도, `WearStateSyncManager`에서 300ms 디바운스로 `/peek/event/chat_updated`(단 `status = "LOADING"`, `textContent = partialText`)를 푸시하면 프로토콜 변경 없이 워치 실시간 타이핑 스트리밍을 그대로 지원할 수 있다.

#### B. 온디맨드 이미지 전송 (`ChannelClient` + `ImagePreprocessor`)
1. 워치로부터 `/peek/rpc/image/request` 수신 시 즉시 `WearImageRequestAck(accepted = true, channelPath = "/peek/channel/image/${req.requestId}")`를 반환하고, 비동기 코루틴(`Dispatchers.IO`)으로 파일 준비 및 스트리밍을 시작한다.
2. **썸네일 요청 (`kind == "THUMBNAIL"`)**:
   - `conversationRepository.getAttachment(req.attachmentId)` 조회.
   - `InternalImageStorage.resolveIfExists(attachment.thumbnailRelPath)` 파일 확인 (폰에서 이미 400px WebP로 생성되어 있음, 통상 10~30KB).
   - 만약 썸네일 파일이 유실되었으면 `ImagePreprocessor`로 `originalRelPath`에서 400px WebP를 재생성한다.
3. **확대 이미지 요청 (`kind == "FULL"`)**:
   - 워치 설정 화질 매핑:
     - `ORIGINAL` -> `maxSide = 16383` (`ImageResolutionPreset.ORIGINAL.maxSidePx`) 또는 `originalRelPath`가 `image/jpeg`/`image/webp`/`image/png`이고 EXIF 회전이 정방향(`ORIENTATION_NORMAL`)인 경우 원본 그대로 스트리밍 가능. 단, 안전하고 일관된 EXIF 방향 보정 및 용량 최적화를 위해 `ImagePreprocessor` 기반 리사이징 캐시 사용을 권장한다.
     - `HIGH_4000` -> `4000px`
     - `BALANCED_2000` -> `2000px`
     - `STANDARD_1000` -> `1000px`
   - **전송 캐시 재사용 (`context.cacheDir/wear_image_cache/{attachmentId}_{quality}.webp`)**:
     - 기존 폰의 `payloadRelPath`가 존재하고 `payloadMaxSide == targetMaxSide`라면 추가 리사이징 없이 바로 `payloadRelPath`를 스트리밍한다.
     - 그 외에는 `ImagePreprocessor`의 소프트웨어 디코더(`decodeScaledBitmapSafely` + WebP quality 85)로 `wear_image_cache/`에 1회 생성 후 스트리밍한다 (폰의 `message_attachments.payloadRelPath` DB 컬럼은 오염시키지 않음 — 폰의 AI 전송 해상도 설정과 워치 보기 화질을 독립 유지하기 위함).
4. **ChannelClient 스트리밍**:
   - `val channel = Wearable.getChannelClient(context).openChannel(sourceNodeId, channelPath).await()`
   - `val outputStream = Wearable.getChannelClient(context).getOutputStream(channel).await()`
   - 16KB 버퍼로 `FileInputStream` -> `outputStream` 복사 후 `flush()` 및 `channelClient.close(channel).await()`.
   - 워치 측 `WearClientListenerService` 또는 활성 `WearPhoneBridgeClient`의 `ChannelCallback.onChannelOpened`에서 `channelClient.getInputStream(channel)`으로 수신하여 임시 파일에 기록한 뒤 atomic rename으로 `WearImageCache`에 저장한다.

---

## 6. 워치 UX 흐름 및 화면별 상세 설계

### 6.1 네비게이션 구조 (`WearNavGraph.kt`)
Wear OS 표준 제스처인 오른쪽 스와이프 뒤로가기(Swipe-to-Dismiss)를 지원하는 `SwipeDismissableNavHost`를 사용한다.

| Route | 화면 | 진입 조건 |
| :--- | :--- | :--- |
| `home` | `WearHomeScreen` (메인 화면) | 앱 시작 화면 |
| `chat?conversationId={id}` | `WearChatScreen` (대화 화면) | 채팅 목록 아이템 탭(`id` 전달) 또는 `새 대화` 탭(`id = null`) |
| `image_viewer/{attachmentId}` | `WearImageViewerScreen` | 말풍선 내 이미지 썸네일 탭 |
| `report_message/{messageId}` | `WearReportScreen` | AI 말풍선 길게 누르기 -> `응답 신고` 선택 |
| `settings` | `WearSettingsScreen` | 메인 화면 하단 `설정` 버튼 탭 |
| `settings/quality` | 사진 보기 화질 선택 화면 | 설정 -> `사진 보기 화질` 탭 |
| `settings/target` | 기본 API 프리셋/그룹 선택 화면 | 설정 -> `기본 API 프리셋/그룹` 탭 |
| `settings/input` | 기본 입력 방식 선택 화면 | 설정 -> `기본 입력 방식` 탭 |

### 6.2 메인 화면 (`WearHomeScreen`)
요구사항에 정의된 **정확한 위아래 배치 순서**를 `ScalingLazyColumn`으로 구현한다.

1. **맨 위 앱 타이틀**:
   - `ListHeader` — `"Peek"` (`PeekWearOnBackground`, 굵은 타이틀. 필요 시 상단 작은 꿀벌 골드 점/아이콘 장식).
2. **`새 대화` 버튼**:
   - Full-width `Button` (또는 `FilledTonalButton` with `containerColor = PeekWearPrimary`, `contentColor = PeekWearOnPrimary`).
   - 아이콘: `Icons.Rounded.Add` / 라벨: `"새 대화"` (`R.string.drawer_new_conversation`).
   - 탭 시 `chat?conversationId=`(신규 대화) 화면으로 이동한다. (폰에 설정된 기본 타겟이 미설정이면 스낵바/토스트로 안내)
3. **채팅 목록**:
   - 폰으로부터 `/peek/rpc/conversations/list` (`beforeUpdatedAt = null`, `pageSize = 10`, 즉 폰 DB `LIMIT 11`)로 최초 10개만 수신해 표시한다.
   - 대화 아이템 카드 (`containerColor = PeekWearSurfaceContainer` `#1E1F20`):
     - 제목: `title` (최대 2줄, `TextOverflow.Ellipsis`).
     - 부제목: 마지막 업데이트 상대 시각(예: `"방금 전"`, `"3분 전"`, `"어제"`) + 생성 중(`isGenerating == true`)일 경우 꿀벌 골드(`#F5B800`) 작은 스피너/인디케이터 표시.
     - 탭: `chat?conversationId={item.id}`로 이동.
     - 길게 누르기: 삭제 확인 다이얼로그(`AlertDialog`) 노출 -> 확인 시 `/peek/rpc/conversations/delete` 호출 및 로컬 리스트에서 제거.
   - 대화가 0개인 경우: 안내 문구(`"아직 대화가 없습니다"`) 카드 표시.
   - 폰과 연결되지 않은 경우: `"휴대폰 연결 확인 중…"` 상태 카드 및 `다시 시도` 버튼 표시.
4. **`더보기` 버튼 (조건부 노출)**:
   - 서버 응답의 **`hasMore == true`일 때만** 채팅 목록 바로 아래에 렌더링한다.
   - 배경 `#282A2C` (`PeekWearSurfaceContainerHigh`), 텍스트 `"더보기"`.
   - 탭 시 현재 마지막 커서(`nextCursorUpdatedAt`, `nextCursorId`)로 `/peek/rpc/conversations/list` (`pageSize = 10`)를 추가 요청하고, 응답받은 `items`를 기존 목록 뒤에 이어 붙인다(`distinctBy { it.id }`). 로딩 중에는 버튼 내 스피너 표시.
5. **`설정` 버튼**:
   - `더보기` 버튼(노출된 경우) 바로 아래, 또는 채팅 목록 최하단에 배치.
   - 배경 `#282A2C` (`PeekWearSurfaceContainerHigh`), 아이콘 `Icons.Rounded.Settings`, 라벨 `"설정"`.
   - 탭 시 `WearSettingsScreen`으로 이동.

### 6.3 대화 화면 (`WearChatScreen`)

#### A. 상단 바 & 모델 바인딩 규칙 (대화방 설정 제한)
- 워치 대화 화면 내부에는 **API 프리셋/그룹 변경 드롭다운, 사진 교체 메뉴, 텍스트 요약 토글, 해상도 변경 메뉴를 일절 제공하지 않는다.**
- 상단 헤더에는 읽기 전용으로 현재 대화방에 바인딩된 모델/그룹 이름 배지(예: `Gemini Flash` 또는 `폴백 그룹`)만 간결하게 표시한다:
  - **기존 대화방 진입 시**: 폰 DB `conversations.activeTargetId`에 이미 바인딩된 타겟 이름을 표시하고, 후속 질문 시에도 해당 타겟으로만 요청한다.
  - **새 대화 시작 시**: 폰 `DataClient`(`/peek/state/config`)의 현재 `selectedTargetId` 이름을 표시하며, 전송 시 폰의 전역 기본 타겟 및 폰의 `systemPrompt`가 자동 적용된다.

#### B. 메시지 부분 조회 (`LIMIT M + 1`, `M = 15`) & 상단 스크롤 로드
- 대화 진입 시 `/peek/rpc/messages/list` (`beforePosition = null`, `pageSize = 15`, 폰 DB `LIMIT 16`)를 호출해 최근 15개 메시지와 `hasOlder`를 받아온다.
- 최초 로드 완료 직후 `ScalingLazyListState.scrollToItem(...)`으로 가장 아래(최신 메시지)에 포커스한다.
- 사용자가 화면을 위로 스크롤해 최상단 아이템(`index == 0`)에 도달하고 `hasOlder == true`이며 중복 요청 중이 아닐 때:
  - 자동으로(또는 최상단 `"이전 메시지 불러오기"` 인디케이터를 거치며) `beforePosition = messages.first().position`, `pageSize = 15`로 이전 15개 메시지를 요청한다.
  - 이전 메시지를 기존 리스트 앞쪽에 prepend할 때 현재 보고 있던 첫 번째 메시지의 key/index를 기준으로 `scrollToItem` 오프셋을 보정하여 스크롤이 위로 튀지 않도록 유지한다.

#### C. 말풍선 UI 및 상태 처리
- **사용자(`USER`) 말풍선**:
  - 우측 정렬 카드, 배경 `#4A3800` (`PeekWearPrimaryContainer`), 글자색 `#FFE082`, 둥근 모서리(`16.dp`, 우상단 `4.dp`).
  - 첨부 이미지가 있는 경우 말풍선 위 또는 내부 상단에 정사각 썸네일 그리드(최대 2열, `72.dp` × `72.dp`, `RoundedCornerShape(10.dp)`) 표시.
    - 썸네일은 `WearImageCache`에 없으면 자동으로 `/peek/rpc/image/request(kind = "THUMBNAIL")`을 호출해 `ChannelClient`로 받아 캐싱 및 표시.
    - `attachment.isTextReplaced == true`인 경우 폰과 동일하게 `"텍스트 요약"` 작은 배지 표시.
- **어시스턴트(`ASSISTANT`) 말풍선**:
  - 좌측 정렬 카드, 배경 `#282A2C` (`PeekWearSurfaceContainerHigh`), 글자색 `#E3E3E3`.
  - `status == "LOADING"`: 꿀벌 골드 스피너 + `"분석 중…"` + `"중지"` 버튼(`/peek/rpc/chat/cancel`).
  - `status == "ERROR"`: 오류 테두리(`PeekWearError`) + 에러 안내 텍스트(`ErrorUiMapper`의 `errorCode` 매핑과 동일 리소스 사용) + `"재시도"` 버튼(`/peek/rpc/chat/retry`).
  - `status == "CANCELLED"`: `"요청이 중지되었습니다."` + `"재시도"` 버튼.
  - `isReported == true`: `"신고된 응답입니다. 탭하여 보기"` 카드로 마스킹 (`Icons.Rounded.VisibilityOff`), 탭 시 해당 세션 동안 임시 열람.
  - **AI 말풍선 길게 누르기(`combinedClickable.onLongClick`)**: 컨텍스트 메뉴(1. 응답 모델 정보 `servedTargetSummary` 읽기 전용 확인, 2. `응답 신고`)를 표시한다.

#### D. AI 면책 안내 문구 및 하단 질문 입력 바
- 메시지 리스트의 마지막 메시지 아래, 입력 버튼 바로 위에 **AI 면책 안내 문구**(`"연결된 AI 제공자 및 모델에 따라 부정확한 응답이 생성될 수 있습니다."`)를 작고 흐린 텍스트(`PeekWearOnSurfaceVariant.copy(alpha = 0.65f)`)로 상시 배치한다.
- **하단 입력 컨트롤 (텍스트 기본 + 음성 보조 전환)**:
  - 주 입력 버튼 (넓은 골드 `Button`, `#F5B800`): 워치 설정의 `WearInputMode`에 따라 동작한다.
    - `TEXT_KEYBOARD` (**기본값**): 키보드 아이콘 + `"메시지 입력"` -> 탭 시 텍스트 입력 인텐트(`RemoteInputIntentHelper`) 실행.
    - `VOICE_FIRST`: 마이크 아이콘 + `"말하기"` -> 탭 시 음성 인식(`RecognizerIntent`) 실행.
  - 옆에 작은 원형 보조 버튼(`FilledTonalIconButton`, `#282A2C`)을 배치하여, 기본이 키보드일 때는 마이크 아이콘으로, 기본이 음성일 때는 키보드 아이콘으로 즉시 1회성 전환 입력이 가능하도록 한다.
  - 전송 중(`isGenerating == true`)일 때는 주 입력 버튼 자리에 빨간/어두운 `"응답 중지"` 버튼을 표시해 즉시 중단할 수 있게 한다.

### 6.4 입력 방식 상세 구현 (`WearInputLauncher.kt`)

```kotlin
// 1. 기본값: 텍스트(키보드) 입력 (Wear OS RemoteInput)
fun createTextRemoteInputIntent(hint: String): Intent {
    val remoteInputs = listOf(
        RemoteInput.Builder(KEY_PROMPT_TEXT)
            .setLabel(hint)
            .build()
    )
    return RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
    }
}
// 결과 수신:
// RemoteInput.getResultsFromIntent(resultIntent)?.getCharSequence(KEY_PROMPT_TEXT)?.toString()

// 2. 음성 입력 (RecognizerIntent)
fun createVoiceRecognizerIntent(promptLabel: String): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, promptLabel)
    }
}
// 결과 수신:
// resultIntent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
```

- **UX 안전장치**: 음성 인식/키보드 입력 결과 문자열이 공백이 아니면 확인 후 바로 `/peek/rpc/chat/send`로 전송(오인식 방지를 위해 음성 인식 직후 1.5초 취소 토스트 또는 즉시 전송 선택 가능; 기본적으로 `RemoteInput`과 `RecognizerIntent` 모두 시스템 UI 내 확인 단계를 포함하므로 즉시 전송해도 자연스럽다).

### 6.5 온디맨드 전체 화면 이미지 뷰어 (`WearImageViewerScreen`)
- 말풍선의 썸네일을 탭하면 `WearImageViewerScreen(attachmentId)`로 진입한다.
- 진입 즉시 `WearSettingsDataStore`에서 현재 선택된 **`WearImageQuality`** 를 읽어 캐시 키 `{attachmentId}_{quality.name}`을 확인한다.
  - 캐시에 없으면 폰으로 `/peek/rpc/image/request(kind = "FULL", qualityPreset = quality.name)`을 전송하고, `ChannelClient` 수신 완료까지 중앙에 `CircularProgressIndicator` + 썸네일 블러/프리뷰를 표시한다.
- 수신된 고화질 이미지 표시 시 제스처 지원:
  - **핀치 줌 & 드래그 패닝**: `detectTransformGestures` (1.0x ~ 5.0x 배율 제한, 화면 경계 클램핑).
  - **더블 탭 줌**: 1.0x <-> 2.5x 토글.
  - **회전형 크라운/베젤 줌**: `Modifier.onRotaryScrollEvent`를 통해 워치 용두 회전으로 부드럽게 확대/축소.
  - 우측 상단(또는 하단 중앙) 작은 닫기(`X`) 버튼 제공 (`SwipeDismissableNavHost` 스와이프 제스처와 패닝 충돌 방지를 위해 확대 상태(`scale > 1.05f`)일 때는 스와이프 뒤로가기보다 팬을 우선하거나 닫기 버튼으로 종료).

### 6.6 워치 설정 화면 (`WearSettingsScreen`)
요구사항에 따라 **3가지 핵심 옵션 + 1가지 정책 항목**으로만 간결하게 구성하며, 불필요한 폰 전용 고급 설정(Base URL 편집, API 키 입력, 컨텍스트 턴 수, 캡션 모델, 카메라 렌즈 등)은 노출하지 않는다.

| 순서 | 항목명 | 저장/동기화 주체 | 선택 옵션 및 세부 동작 |
| :---: | :--- | :--- | :--- |
| **1** | **사진 보기 화질** | 워치 로컬 (`WearSettingsDataStore`) | 폰 -> 워치 이미지 확대 전송 시 사용할 리사이징 해상도 선택:<br>• `최고 화질 (원본)` (`ORIGINAL`, maxSide 16383px)<br>• `높음 (4000px)` (`HIGH_4000`)<br>• `균형 (2000px)` (`BALANCED_2000`, **기본값**)<br>• `표준/빠름 (1000px)` (`STANDARD_1000`) |
| **2** | **기본 API 프리셋/그룹** | 폰과 **양방향 동기화** (`DataClient` + `MessageClient`) | 폰에 등록된 `SelectableTarget`(프리셋 및 모델 그룹) 목록을 표시. 그룹에는 `[그룹]` 배지 표기.<br>선택 시 `/peek/rpc/settings/select_target` 전송 -> 폰의 `settingsRepository.selectPreset(targetId)` 갱신 -> 폰 `/peek/state/config` DataItem 업데이트로 양방향 동기화. |
| **3** | **기본 입력 방식** | 워치 로컬 (`WearSettingsDataStore`) | 대화 화면 메인 입력 버튼의 기본 동작 선택:<br>• `텍스트 키보드` (`TEXT_KEYBOARD`, **기본값**)<br>• `음성 입력` (`VOICE_FIRST`) |
| **4** | **개인정보처리방침** | 폰 브라우저 위임 (`RemoteActivityHelper`) | 탭 시 `RemoteActivityHelper(context).startRemoteActivity(...)`로 페어링된 폰 브라우저에서 `https://iank.im/peek/privacy` 열기 실행 + 워치 화면 확인 피드백 표시. |

- 설정 목록 최하단에는 폰 설정 화면과 동일하게 독립 클라이언트 상표권 고지(`settings_trademark_notice`)를 작은 캡션으로 표기한다.

---

## 7. Google Play 정책 대응 (면책 문구 · 응답 신고 · 개인정보처리방침)

Google Play의 생성형 AI(Generative AI) 앱 정책 및 Wear OS 컴패니언 앱 심사 기준을 모두 충족하도록 설계한다.

### 7.1 AI 면책 안내 문구
- `WearChatScreen`의 마지막 메시지 하단에 `:app`의 `R.string.chat_ai_disclaimer`와 동일한 문구를 표시한다:
  - 한국어: `"연결된 AI 제공자 및 모델에 따라 부정확한 응답이 생성될 수 있습니다."`
  - 영어: `"Responses from connected AI providers may be inaccurate."`

### 7.2 AI 응답 신고 (`응답 신고`) 양방향 동기화 + 폰 메일 신고 연동
1. **워치 UI 트리거**:
   - `WearChatScreen`에서 `ASSISTANT` 말풍선을 길게 누르면 컨텍스트 메뉴가 열리고 `응답 신고` 버튼을 선택할 수 있다.
   - `WearReportScreen`에서 `:app`의 `ReportResponseDialog`와 동일한 4가지 사유 중 하나를 선택한다:
     - `유해하거나 위험한 콘텐츠` (`report_reason_harmful`)
     - `혐오 또는 차별적 표현` (`report_reason_hate`)
     - `부정확한 정보` (`report_reason_inaccurate`)
     - `기타` (`report_reason_other`)
2. **폰 DB 동기화 및 메일 신고 연동 (`WearReportDispatcher`)**:
   - 워치가 `/peek/rpc/chat/report(messageId, reason)`를 전송하면 폰의 `WearBridgeListenerService`가 이를 수신한다.
   - **즉시 DB 마스킹**: `conversationRepository.markMessageReported(messageId, isReported = true)`를 호출해 폰 Room DB를 갱신하고, 워치로 `/peek/event/chat_updated`를 푸시하여 폰과 워치 모두에서 해당 AI 응답이 즉시 `"신고된 응답입니다. 탭하여 보기"` 상태로 전환된다.
   - **폰 메일 신고 실행**:
     - 폰의 `MainViewModel.reportAssistantMessage`와 동일한 양식(`developer_report_email = support@iank.im`, 제목 `[Peek Report] AI 응답 신고`, 본문 `사유 / 모델 정보 / 메시지 ID / 응답 원문`)으로 `ACTION_SENDTO` (`mailto:`) 인텐트를 생성한다.
     - **백그라운드 제약 대응 (Android 10+ BAL 제한)**: 폰 앱이 백그라운드일 때는 서비스에서 `startActivity()`를 직접 호출할 수 없으므로, (a) `RemoteActivityHelper`로 폰 측 `ACTION_SENDTO` 오픈을 시도하고 동시에 (b) 폰에 신고 메일 발송용 알림(`PendingIntent.getActivity(...)` with `ACTION_SENDTO` / `ACTION_CHOOSER`)을 발행하여 사용자가 폰을 열었을 때 탭 한 번으로 이메일 앱에서 신고를 마무리할 수 있도록 보장한다. (포그라운드일 때는 즉시 메일 앱 호출)

### 7.3 개인정보처리방침 (`RemoteActivityHelper`)
- 워치 자체에는 웹 브라우저가 없는 경우가 대부분이므로, `androidx.wear.remote.interactions.RemoteActivityHelper`를 사용한다.

```kotlin
// wear/ui/settings/WearSettingsScreen.kt 또는 ViewModel 동작 명세
val remoteIntent = Intent(Intent.ACTION_VIEW)
    .addCategory(Intent.CATEGORY_BROWSABLE)
    .setData(Uri.parse("https://iank.im/peek/privacy"))

val helper = RemoteActivityHelper(context, Executors.newSingleThreadExecutor())
// ListenableFuture -> kotlinx-coroutines-guava 또는 CallbackToFutureAdapter로 대기
// 성공 시 워치 화면에 ConfirmationOverlay("폰에서 열었습니다") 표시
// 실패(노드 미연결 등) 시 안내 문구 및 URL 텍스트 안내
```

---

## 8. 단계별 구현 로드맵 및 체크리스트

### Phase 1: 프로젝트 및 모듈 뼈대 구축
- [x] `settings.gradle.kts`에 `:wear` 모듈 추가 (`include(":app", ":wear")`)
- [x] `gradle/libs.versions.toml`에 Wear Compose, Horologist, Play Services Wearable, `wear-remote-interactions`, `wear-input`, `kotlinx-coroutines-play-services` 정의
- [x] `wear/build.gradle.kts` 작성 (`applicationId = "com.iank.im.peek"`, `namespace = "com.iank.im.peek.wear"`, `minSdk = 30`, `targetSdk = 36`, `:app`과 동일 서명 설정 공유, `versionCode = 100001`)
- [x] `app/build.gradle.kts`에 `play-services-wearable`, `kotlinx-coroutines-play-services` 추가
- [x] `wear/src/main/AndroidManifest.xml` 작성 (카메라 피처/권한 완전 제외, `standalone = false`)

### Phase 2: 폰 측 DB 페이지네이션 & Wearable 브릿지 구현 (`:app`)
- [x] `ConversationDao.kt`: `getConversationsPage(beforeUpdatedAt, beforeId, limitPlusOne)` 추가
- [x] `MessageDao.kt`: `getMessagesPageWithAttachments(conversationId, beforePosition, limitPlusOne)` 추가
- [x] `ConversationRepository.kt` & `ConversationRepositoryImpl.kt`: `PagedConversations` (`N + 1`) / `PagedMessages` (`M + 1`) 구현
- [x] `com.iank.im.peek.wearbridge.*` 구현:
  - [x] `WearProtocolModels.kt` & `WearBridgeConstants.kt`
  - [x] `WearStateSyncManager.kt` (`SettingsRepository` + `ActiveSendRegistry` 변경 감지 -> `DataClient` `/peek/state/config` 동기화 + 완료/오류 `/peek/event/chat_updated` 푸시)
  - [x] `WearImageStreamHelper.kt` (`ImagePreprocessor` 재사용 온디맨드 리사이징 + `ChannelClient` 스트리밍)
  - [x] `WearReportDispatcher.kt` (`markMessageReported(true)` + 메일 신고 인텐트/알림 연동)
  - [x] `WearBridgeListenerService.kt` 작성 및 `app/src/main/AndroidManifest.xml` 등록

### Phase 3: 워치 테마 · 데이터 · 통신 레이어 구현 (`:wear`)
- [ ] `ui/theme/Color.kt` & `Theme.kt`: `#000000`, `#1E1F20`, `#282A2C`, `#F5B800` 컬러 매핑
- [ ] `WearSettingsDataStore.kt`: `WearImageQuality` (기본값 `BALANCED_2000`), `WearInputMode` (기본값 `TEXT_KEYBOARD`) 영구 저장
- [ ] `WearPhoneBridgeClient.kt` & `WearImageCache.kt`: `MessageClient` RPC/이벤트 수신, `DataClient` 설정 구독, `ChannelClient` 이미지 수신 캐싱

### Phase 4: 워치 화면 구현 (`:wear`)
- [ ] `WearHomeScreen`: 타이틀(`Peek`) -> `새 대화` -> 채팅 목록(10+1) -> `더보기` (조건부) -> `설정`
- [ ] `WearChatScreen`: 바인딩 모델 표시(읽기 전용), 15+1 메시지 역방향 페이지네이션, 썸네일 자동 로딩, 마지막 하단 AI 면책 문구, 텍스트(기본)/음성(전환) 입력
- [ ] `WearImageViewerScreen`: 설정 화질 기반 온디맨드 요청 및 핀치/더블탭/크라운 줌
- [ ] `WearReportScreen`: AI 말풍선 길게 누르기 -> 사유 선택 -> 폰 동기화 및 메일 연동
- [ ] `WearSettingsScreen`: 사진 보기 화질, 기본 API 프리셋/그룹(양방향 동기화), 기본 입력 방식, 개인정보처리방침(`RemoteActivityHelper`)

### Phase 5: 검증 계획 (실제 Android SDK 환경에서 수행)
- [ ] Gradle 빌드 검증 (`./gradlew :app:assembleDebug :wear:assembleDebug`) — **주의: 현재 ARM64 컨테이너(SDK/JDK 미설치)에서는 실행하지 않고, Android SDK가 구성된 빌드 머신에서 수행한다.**
- [ ] Room DAO 단위 테스트: `ConversationDao.getConversationsPage` (`10 + 1`) 및 `MessageDao.getMessagesPageWithAttachments` (`15 + 1`)의 `hasMore`/`hasOlder` 경계값 검증 (정확히 10개/11개, 15개/16개일 때).
- [ ] Wear OS 에뮬레이터 + 폰 에뮬레이터 페어링 E2E 검증:
  - 폰/워치 양쪽에서 시작한 대화의 목록 동기화 및 `더보기` 페이지네이션 동작
  - 워치 채팅방 진입 시 기존 `activeTargetId` 유지 및 새 대화 시 기본 타겟 + `systemPrompt` 적용 확인
  - 기본 입력(텍스트) <-> 설정 변경 후 음성 입력 전환 확인
  - 썸네일 표시 후 터치 시 설정 화질별 온디맨드 `ChannelClient` 전송 확인
  - AI 응답 신고 시 폰 Room DB `isReported = true` 반영 및 메일 연동 확인
  - 설정 -> `개인정보처리방침` 탭 시 폰 브라우저 실행 확인
