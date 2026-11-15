# Peek (`com.iank.im.peek`) 프로젝트 설계 문서 (PLAN.md)

> **Rev.2.1 (2026-11-15)** — Rev.2 설계 리뷰 완료 및 다국어(i18n: `en-US` 기본값 + `ko` 한국어) 아키텍처 추가, 세부 엣지 케이스(`BackHandler` LIFO 우선순위, `GatedLifecycleOwner` 초기 상태, API 29 WebP 분기, 공식 도메인 HTTPS 강제) 보완. 변경 요약은 문서 끝 [7. 변경 이력](#7-변경-이력)을 참고하세요.

## 1. 프로젝트 개요

* **앱 이름**: Peek
* **패키지명**: `com.iank.im.peek`
* **플랫폼 / 언어 / UI**: Android Native / Kotlin / Jetpack Compose (Material 3)
* **지원 언어 (i18n / l10n)**: **영어 (`en-US`, 기본값)** 및 **한국어 (`ko`)** 완벽 지원
  * 앱 내 모든 UI 문구(버튼, 안내, 다이얼로그, 설정, 오류 메시지, 스낵바/토스트, 접근성 `contentDescription`)를 리소스화하여 하드코딩 문자열을 배제.
  * 시스템 언어 자동 추종(기본) + 앱 설정 내 언어 수동 선택(`System Default` / `English (US)` / `한국어`) 및 Android 13+(API 33+) 시스템 '앱별 언어(Per-app language)' 설정 연동.
* **SDK 기준**: `minSdk 29` (권장 — `ImageDecoder`·HEIC 디코딩·Scoped Storage 전제), `targetSdk 36`
  * `targetSdk 35+`: Edge-to-Edge 강제 → 모든 레이어가 `WindowInsets`를 직접 처리해야 함.
  * `targetSdk 36`: Predictive Back 기본 활성 → `onBackPressed()`/`KEYCODE_BACK` 미호출. **뒤로 가기는 반드시 `OnBackPressedDispatcher`(`BackHandler`/`PredictiveBackHandler`)로만 처리**.
* **핵심 컨셉**: 앱 실행 즉시 카메라 뷰파인더가 활성화되어 사물·문서를 비추고 바로 질문하며 멀티턴 대화를 이어가는 **구글 렌즈(Google Lens) 스타일 개인용 비전 AI 클라이언트**
* **핵심 기술 목표**:
  1. **즉각적인 카메라 진입 & 전력·발열 최적화**: Single Activity 구조에서 대화 모드 진입 시 카메라 세션을 **즉시 비활성화**하고, 탐색 모드 복귀 시 재활성화. 바인딩 on/off는 `unbindAll()` 직접 호출이 아니라 **Lifecycle Gate**(4.1)로 직렬화하여 레이스 컨디션을 원천 차단.
  2. **원본 보존 + 3종 이미지 파이프라인**: 촬영/선택한 사진은 앱 내부 저장소(`context.filesDir`)에 **원본 / 전송용(WebP 85%) / 썸네일** 3종으로 분리 저장하고, Room DB에는 **상대 경로**와 메타데이터만 기록. 전송 전 사진은 `staging/`에서 관리하고, 모든 파일 삭제는 **삭제 큐 + 고아 파일 GC**로 이중 보장.
  3. **토큰 비용 및 HTTP 413 페이로드 한도 방어**: 해상도 프리셋(원본/4000px/2000px/1000px), Gemini File API(48시간 캐시, 서버 `expirationTime` 기준) 및 Base64 전환, **전송 전 페이로드 크기 사전 계산(Preflight)과 단계적 강등**, 슬라이딩 윈도우 컨텍스트, **이전 턴 사진 자동 텍스트 대치(`autoTextReplaceOldImages`)** 지원.
  4. **워치 컴패니언 확장성**: 추후 Wear OS 워치 컴패니언 앱(`:wear`) 연동을 고려해 도메인/데이터 모델과 비즈니스 로직을 깔끔하게 분리.
  5. **글로벌 다국어 대응 (`en-US` 기본 / `ko` 지원)**: DB에는 언어 중립적인 상태/오류 코드(`errorCode`)만 저장하고, 렌더링 시점에 현재 로케일에 맞는 문구로 매핑하여 실행 중 언어를 전환해도 과거 대화·오류 안내가 즉시 해당 언어로 표시되도록 설계.

---

## 2. 전체 폴더 및 파일 구조

추후 Wear OS 워치 앱 모듈(`:wear`) 추가를 고려하여, 현재는 `:app` 모듈 내부에서 레이어별 패키지를 엄격히 분리하되 공통 프로토콜/모델을 분리하기 쉬운 **Clean Architecture + MVVM (Unidirectional Data Flow)** 구조로 구성합니다. (Rev.2 신규/변경 파일은 `★` 표시)

```text
peek/
├── build.gradle.kts                     # 루트 Gradle 설정 (AGP, Kotlin, KSP, Hilt, Compose 플러그인)
├── settings.gradle.kts                  # 모듈 설정 (:app, 추후 :wear 확장 가능)
├── gradle.properties                    # Gradle JVM 인자, AndroidX, Non-transitive R 설정
├── gradle/
│   └── libs.versions.toml               # Version Catalog (라이브러리 버전 일원화 관리)
└── app/
    ├── build.gradle.kts                 # 앱 모듈 의존성 및 빌드 설정 (namespace = "com.iank.im.peek")
    ├── schemas/                         # ★ Room exportSchema 출력 (마이그레이션 검증용, VCS 커밋)
    ├── proguard-rules.pro               # R8/ProGuard 난독화 및 직렬화 보존 규칙
    └── src/main/
        ├── AndroidManifest.xml          # 카메라 권한, FileProvider, MainActivity(portrait, adjustResize),
        │                                #   enableOnBackInvokedCallback, localeConfig, networkSecurityConfig, dataExtractionRules
        ├── res/
        │   ├── values/                  # ★ 기본 로케일 (en-US, English)
        │   │   ├── strings.xml          # 모든 UI 문구·오류·접근성 설명의 영어(기본값) 리소스
        │   │   └── themes.xml
        │   ├── values-ko/               # ★ 한국어 로케일 (ko)
        │   │   └── strings.xml          # 모든 UI 문구·오류·접근성 설명의 한국어 번역 리소스
        │   └── xml/
        │       ├── locales_config.xml           # ★ 지원 로케일 선언 (en-US, ko) — Android 13+ 앱별 언어 설정 연동
        │       ├── file_paths.xml               # FileProvider: cache/share/ 하위만 노출 (images/ 직접 노출 금지)
        │       ├── network_security_config.xml  # ★ 공식 API(Gemini/OpenAI)는 HTTPS 강제, 커스텀 URL용 cleartext/사용자 CA 정책
        │       ├── data_extraction_rules.xml    # ★ (API 31+) 백업 제외: DB, images/, 암호화 키 저장소
        │       └── backup_rules.xml             # ★ (API 30 이하) 동일 정책
        └── java/com/iank/im/peek/
            ├── PeekApplication.kt       # @HiltAndroidApp, 첫 프레임 이후 StartupMaintenance 실행
            ├── MainActivity.kt          # 단일 액티비티 (AppCompatActivity 또는 로케일 ContextWrapper + enableEdgeToEdge + MainScreen 호스트)
            │
            ├── di/                      # Hilt 의존성 주입 모듈
            │   ├── AppModule.kt         # CoroutineDispatcher, ContentResolver, FileManager 제공
            │   ├── CoroutineScopeModule.kt  # ★ @ApplicationScope CoroutineScope (전송/업로드/정리 작업용)
            │   ├── DatabaseModule.kt    # Room Database 및 DAO 제공
            │   ├── DataStoreModule.kt   # Preferences DataStore & 암호화 API Key 저장소 제공
            │   └── NetworkModule.kt     # OkHttpClient(타임아웃·로그 마스킹), Retrofit/Ktor, JSON 직렬화기 제공
            │
            ├── domain/                  # 비즈니스 모델 및 핵심 인터페이스 (순수 Kotlin)
            │   ├── model/
            │   │   ├── AppLanguage.kt           # ★ SYSTEM, EN_US("en-US"), KO("ko")
            │   │   ├── ApiProvider.kt           # GEMINI, OPENAI
            │   │   ├── ApiPreset.kt             # 별칭, 공급자, Custom Base URL, 모델명, 이미지 포맷, 요청 크기 상한
            │   │   ├── ApiError.kt              # ★ Auth/NotFound/PayloadTooLarge/RateLimited/Server/Blocked/FileUnavailable/Network/Timeout/Parse/InterruptedByExit
            │   │   ├── ImageSendMethod.kt       # FILE_API, BASE64
            │   │   ├── ImagePayloadFormat.kt    # ★ WEBP, JPEG (OpenAI 호환 백엔드 호환성)
            │   │   ├── ImageResolutionPreset.kt # DEFAULT(상속), ORIGINAL, HIGH_4000, STANDARD_2000, SAVER_1000
            │   │   ├── ContextRetentionMode.kt  # ALL, SLIDING_WINDOW(n)  (sealed → DataStore에 직렬화 저장)
            │   │   ├── CameraLensOption.kt      # BACK, FRONT
            │   │   ├── CaptureResolutionOption.kt # MAX, BALANCED, STANDARD
            │   │   ├── AppSettings.kt           # 전송/컨텍스트/카메라/시스템 프롬프트/언어(AppLanguage) 통합 설정 모델
            │   │   ├── Conversation.kt          # 대화방 도메인 모델 (방 단위 해상도 오버라이드 포함)
            │   │   ├── ChatMessage.kt           # 메시지 도메인 모델 (USER/ASSISTANT, 상태, 에러 여부)
            │   │   ├── MessageAttachment.kt     # 첨부 사진 (원본/전송용/썸네일 상대경로, 해상도, 텍스트 대치, 캡션)
            │   │   └── PendingAttachment.kt     # 전송 전 입력창 상단에 대기 중인 사진 모델 (staging 경로)
            │   ├── repository/
            │   │   ├── ConversationRepository.kt   # 모든 삭제 경로는 여기를 경유 (삭제 큐 기록 보장)
            │   │   ├── SettingsRepository.kt
            │   │   └── VisionAiRepository.kt
            │   └── usecase/
            │       ├── StageAttachmentUseCase.kt          # ★ 촬영/갤러리 → staging 복사 + 썸네일 생성
            │       ├── CommitAttachmentsUseCase.kt        # ★ staging → original/thumb 이동(rename) + DB 기록
            │       ├── EnsurePayloadUseCase.kt            # ★ 실효 해상도 기준 payload 지연(lazy) 생성/재생성
            │       ├── ReprocessAttachmentResolutionUseCase.kt # 개별/대화 전체 해상도 변경 (메타 갱신 + 원격 캐시 무효화)
            │       ├── BuildApiPayloadUseCase.kt          # 슬라이딩 윈도우 & 이전 사진 텍스트 대치 & 크기 Preflight
            │       ├── SendChatMessageUseCase.kt          # 커밋 -> payload 보장 -> (필요시 File API 업로드) -> 채팅 요청
            │       ├── EditMessageUseCase.kt              # ★ 사용자 메시지 수정 시 이후 턴 절단(+파일 삭제 큐) 후 재전송
            │       ├── GenerateImageCaptionUseCase.kt     # (선택/opt-in) 경량 모델 1줄 캡션 생성 및 캐싱
            │       └── DeleteConversationUseCase.kt       # DB 트랜잭션(+삭제 큐) → 파일 삭제 → 원격 파일 삭제 요청
            │
            ├── data/                    # 로컬 DB, 파일 시스템, 설정, 원격 API 구현체
            │   ├── local/
            │   │   ├── db/
            │   │   │   ├── PeekDatabase.kt
            │   │   │   ├── converter/
            │   │   │   │   └── RoomConverters.kt
            │   │   │   ├── dao/
            │   │   │   │   ├── ConversationDao.kt
            │   │   │   │   ├── MessageDao.kt
            │   │   │   │   ├── ApiPresetDao.kt
            │   │   │   │   ├── RemoteFileCacheDao.kt      # ★
            │   │   │   │   └── FileDeletionQueueDao.kt    # ★
            │   │   │   └── entity/
            │   │   │       ├── ConversationEntity.kt
            │   │   │       ├── MessageEntity.kt
            │   │   │       ├── MessageAttachmentEntity.kt
            │   │   │       ├── ApiPresetEntity.kt
            │   │   │       ├── RemoteFileCacheEntity.kt   # ★ Gemini File URI 캐시 (API 키·payload 리비전 단위)
            │   │   │       ├── PendingFileDeletionEntity.kt   # ★ 로컬 파일 삭제 큐
            │   │   │       └── PendingRemoteDeletionEntity.kt # ★ Gemini 원격 파일 삭제 큐 (best-effort)
            │   │   ├── datastore/
            │   │   │   ├── SettingsDataStore.kt         # 전역 설정 + selectedPresetId (DataStore Preferences)
            │   │   │   └── SecureKeyStorage.kt          # API Key 암호화 보관 (Tink AEAD + Android Keystore + DataStore)
            │   │   ├── image/
            │   │   │   ├── InternalImageStorage.kt      # files/images/{staging,original,payload,thumb} 관리, 상대경로↔File 변환, 원자적 쓰기
            │   │   │   └── ImagePreprocessor.kt         # 목표 크기 직접 디코딩, EXIF 회전 보정, WebP/JPEG 인코딩 (동시성 1 제한)
            │   │   └── maintenance/
            │   │       ├── StartupMaintenance.kt        # ★ LOADING 정리, 삭제 큐 처리, staging 정리, 만료 캐시 정리
            │   │       └── OrphanFileSweeper.kt         # ★ DB 미참조 파일 GC (유예시간 적용)
            │   ├── remote/
            │   │   ├── common/
            │   │   │   ├── StreamingJsonBody.kt         # ★ Base64를 메모리에 올리지 않고 Sink로 스트리밍하는 RequestBody
            │   │   │   ├── PayloadSizeEstimator.kt      # ★ 요청 바디 크기 사전 계산 (413 Preflight)
            │   │   │   └── ApiErrorMapper.kt            # ★ HTTP/본문/예외 → ApiError 매핑 (비 JSON 오류 본문 포함)
            │   │   ├── gemini/
            │   │   │   ├── GeminiApiService.kt          # generateContent, files.get, files.delete
            │   │   │   ├── GeminiFileUploader.kt        # ★ Resumable Upload (start → upload,finalize) + 상태 폴링 + single-flight
            │   │   │   └── dto/
            │   │   │       ├── GeminiRequestDto.kt
            │   │   │       ├── GeminiResponseDto.kt
            │   │   │       └── GeminiFileUploadDto.kt
            │   │   └── openai/
            │   │       ├── OpenAiApiService.kt          # @Url 기반 동적 엔드포인트 ({normalizedBaseUrl}/chat/completions)
            │   │       ├── BaseUrlNormalizer.kt         # ★ 사용자 입력 Base URL 정규화 + 미리보기
            │   │       └── dto/
            │   │           ├── OpenAiRequestDto.kt
            │   │           └── OpenAiResponseDto.kt
            │   └── repository/
            │       ├── ConversationRepositoryImpl.kt
            │       ├── SettingsRepositoryImpl.kt
            │       └── VisionAiRepositoryImpl.kt
            │
            └── ui/                      # Jetpack Compose UI 레이어
                ├── common/
                │   ├── UiText.kt                        # ★ StringResource(@StringRes id, args) / DynamicString 래퍼 (하드코딩 문자열 배제)
                │   ├── ErrorUiMapper.kt                 # ★ ApiError / errorCode → 현재 로케일(en-US / ko) @StringRes 변환
                │   └── AppLocaleManager.kt              # ★ AppCompatDelegate.setApplicationLocales 기반 런타임 언어 전환 (SYSTEM / en-US / ko)
                ├── theme/
                │   ├── Color.kt
                │   ├── Type.kt
                │   └── Theme.kt
                ├── main/
                │   ├── MainViewModel.kt                 # 화면 모드, activeConversationId, 오버레이, 입력창, 프리셋 전환 관리 (SavedStateHandle 사용)
                │   ├── MainUiState.kt                   # UI 상태, 파생 상태(cameraShouldRun), 단발성 이벤트(UiEvent) 정의
                │   ├── OverlayState.kt                  # ★ 단일 오버레이 상태 (None/Settings/PresetMenu/MessageMenu/Dialog/ImageViewer)
                │   └── MainScreen.kt                    # ModalNavigationDrawer + CameraLayer + ChatPanel + SettingsSheet + BackHandler 우선순위
                ├── camera/
                │   ├── CameraPreviewLayer.kt            # PreviewView(COMPATIBLE) 호스트, 권한 미허용 시 플레이스홀더
                │   ├── CameraSessionController.kt       # ★ GatedLifecycleOwner + use case 구성/재구성 + CameraState 오류 관찰
                │   ├── TopControlBar.kt                 # 좌측 햄버거 메뉴 + API 프리셋 별칭 전환 드롭다운 + 우측 설정 아이콘
                │   └── BottomCaptureAndInputBar.kt      # 대기 중 썸네일 스트립 + 갤러리 버튼 / 촬영 버튼 + 하단 채팅 입력창 & 전송 버튼 (imePadding)
                ├── drawer/
                │   ├── NavigationDrawerContent.kt       # 상단 검색바 + 새 대화 버튼 + 대화 목록(길게 눌러 삭제) + 하단 설정 버튼
                │   └── DeleteConversationDialog.kt      # 대화 삭제 확인 다이얼로그
                ├── chat/
                │   ├── ChatPanel.kt                     # ★(구 ChatBottomSheet) 같은 윈도우의 전체 화면 대화 레이어 + 자체 입력바
                │   ├── MessageBubble.kt                 # 질문/응답 말풍선, 로딩 인디케이터, 오류 메시지 + 재시도/중지 버튼
                │   ├── MessageContextMenu.kt            # 텍스트 길게 누르기 메뉴(복사, 텍스트 선택, 수정) / 이미지 길게 누르기 메뉴(복사, 교체, 텍스트 대치, 해상도 변경)
                │   ├── EditMessageDialog.kt             # 대화 내용 수정 다이얼로그 (이후 턴 삭제 경고 포함)
                │   ├── ResolutionChangeDialog.kt        # 전송 해상도 선택 + "현재 대화 전체에 적용할까요?" 확인 다이얼로그
                │   └── FullScreenImageViewerDialog.kt   # 썸네일 탭 시 로컬 원본 서브샘플링 핀치 줌 뷰어
                └── settings/
                    ├── SettingsBottomSheet.kt           # 설정 바텀 시트 컨테이너 (skipPartiallyExpanded, imePadding)
                    ├── ApiPresetSection.kt              # 별칭, 공급자, API Key(마스킹/토글), Custom Base URL(+해석 결과 미리보기, 연결 테스트), 모델, 이미지 포맷
                    ├── PayloadContextSection.kt         # 컨텍스트 범위, 전송 방식, 해상도, 자동 텍스트 대치, 최근 이미지 유지 턴 수
                    ├── CameraConfigSection.kt           # 기본 카메라(후면/전면), 촬영 해상도, 앱 표시 언어(System / English (US) / 한국어)
                    └── SystemPromptSection.kt           # 기본 시스템 지침 멀티라인 TextArea
```

---

## 3. 사용 라이브러리 및 선정 이유

안드로이드 스튜디오 최신 안정 버전에서 바로 동기화·빌드될 수 있도록 검증된 공식 Jetpack 및 표준 라이브러리로 구성합니다.

> [!NOTE]
> AGP 9.x부터는 built-in Kotlin 지원이 기본이므로, Version Catalog 확정 시 **AGP ↔ Kotlin ↔ KSP ↔ Hilt ↔ Room** 호환 버전 조합을 한 번에 검증합니다. Compose Material 3는 **1.3.0 이상**(ModalNavigationDrawer 내장 Predictive Back, `ModalBottomSheet`의 `contentWindowInsets`)을 전제로 합니다.

| 분류 | 라이브러리 | 역할 및 선정 이유 |
| :--- | :--- | :--- |
| **코어 & 비동기** | `Kotlin Coroutines & Flow` | 이미지 전처리(`Dispatchers.Default.limitedParallelism(1)`), DB 스트림, API 비동기 통신 처리 |
| **UI 프레임워크** | `Jetpack Compose (BOM)` + `Material 3 (1.3+)` + `activity-compose (1.9+)` | `ModalNavigationDrawer`, `ModalBottomSheet`(설정 전용), `BackHandler`/`PredictiveBackHandler`, Edge-to-Edge |
| **다국어(i18n)** | `AndroidX AppCompat (1.7+)` (`AppCompatDelegate.setApplicationLocales`) | `minSdk 29`~`targetSdk 36` 전 구간에서 영어(`en-US`, 기본값)·한국어(`ko`) 런타임 전환 및 Android 13+ 시스템 앱별 언어 설정 자동 동기화 |
| **아키텍처 / DI** | `Hilt (Dagger)` + `Lifecycle ViewModel Compose` + `SavedStateHandle` | 카메라·채팅·설정 간 상태 공유, 프로세스 종료 후 대기 첨부/화면 모드 복원 |
| **카메라** | `AndroidX CameraX 1.4+` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) | 뷰파인더 프리뷰, `ImageCapture` 파일 직접 저장, `ProcessCameraProvider.awaitInstance()`, 16KB 페이지 정렬 네이티브 라이브러리. (대안: `camera-compose`의 `CameraXViewfinder`) |
| **로컬 DB** | `AndroidX Room` (`room-runtime`, `room-ktx`, `ksp`) | 대화방, 메시지, 첨부 메타데이터, API 프리셋, 원격 파일 캐시, 파일 삭제 큐. `exportSchema = true` |
| **설정 & 보안 저장소** | `AndroidX DataStore (Preferences)` + `Google Tink (tink-android)` | 실시간 설정 Flow 공유 및 Keystore 기반 AEAD로 API Key 암호화. ※ `security-crypto`(EncryptedSharedPreferences)는 **deprecated** 및 기기별 Keyset 손상 이슈로 사용하지 않음 |
| **네트워크 & 직렬화** | `OkHttp 4` + `Retrofit 2` (또는 `Ktor`) + `Kotlinx Serialization` | Gemini REST(Resumable Upload + `generateContent`) 및 OpenAI 호환 동적 Base URL(`@Url`) 호출, 스트리밍 RequestBody |
| **이미지 로딩 / 뷰어** | `Coil 3 (coil-compose)` + `Telephoto (zoomable-image-coil3)` + `AndroidX ExifInterface` | 썸네일 비동기 렌더링, **원본 서브샘플링 줌**(대형 원본 전체 디코딩 OOM 방지), EXIF 회전 보정 |
| **마크다운 렌더링** | `Compose Markdown` (또는 경량 마크다운 파서) | AI 응답 내 코드 블록, 표, 리스트, 볼드체 등 가독성 높은 렌더링 |

---

## 4. 핵심 로직 상세 설계

### 4.1. Single Activity & 카메라 세션 제어 (`CameraSessionController`)

> [!IMPORTANT]
> Rev.1의 "`LaunchedEffect`에서 `unbindAll()` / `bindToLifecycle()` 직접 토글" 방식은 다음 위험이 있어 폐기합니다.
> ① 촬영(`takePicture`) 진행 중 전송 시 `unbindAll()`로 `ERROR_CAMERA_CLOSED` 발생·임시 파일 손상, ② `CameraPreviewLayer`가 컴포지션에서 빠져도 `LaunchedEffect`는 정리 코드를 실행하지 않아 Activity 수명 동안 카메라가 계속 바인딩됨, ③ `ProcessCameraProvider` 획득(suspend) 도중 상태가 빠르게 뒤집히면 해제 후 재바인딩되는 순서 역전, ④ 전면 카메라 미탑재 기기/에뮬레이터에서 `bindToLifecycle()`이 `IllegalArgumentException`으로 크래시.

1. **카메라 실행 여부는 파생 상태(Derived State) 하나로만 결정**:
   * `isCameraActive`를 별도 `var`로 두지 않고 `MainUiState`에서 계산합니다.
     ```kotlin
     val cameraShouldRun: Boolean get() =
         hasCameraPermission &&
         (screenMode == ScreenMode.CAMERA_EXPLORE || captureInFlight > 0) && // 촬영 중이면 대화 모드 진입 후에도 콜백 완료까지 유지
         overlay !is OverlayState.ImageViewer
     ```
   * 드로어/설정 시트가 열려 있어도 탐색 모드라면 프리뷰는 유지합니다(시각적 연속성). 갤러리 피커 등 외부 Activity로 이동하면 Activity가 `STOPPED`가 되어 아래 Gate에 의해 자동으로 꺼집니다.

2. **Lifecycle Gate 방식 (권장)**:
   * use case(`Preview`, `ImageCapture`)는 **Activity가 아니라 커스텀 `GatedLifecycleOwner`(LifecycleRegistry)** 에 1회 바인딩합니다.
   * `GatedLifecycleOwner`는 생성 직후 초기 상태를 `Lifecycle.State.CREATED`로 설정합니다(`INITIALIZED` 상태에서 바로 `DESTROYED`로 전이하면 `LifecycleRegistry`가 `IllegalStateException`을 던지는 문제 방지).
   * Gate 상태 = `min(Activity 현재 상태, if (cameraShouldRun) RESUMED else CREATED)`
   * CameraX는 바인딩된 LifecycleOwner가 `STARTED` 미만이면 카메라를 닫고, 다시 `STARTED` 이상이 되면 엽니다. 따라서 **on/off는 동기적인 Lifecycle 상태 변경 한 줄**이며, 빠른 토글에도 비동기 순서 역전이 발생하지 않습니다. 백그라운드 진입도 자동 처리됩니다.
   * `CameraSessionController`는 `CameraPreviewLayer`에서 `remember`로 생성하고 **`DisposableEffect`의 `onDispose`에서 Gate를 `DESTROYED`로 내려 CameraX가 바인딩을 해제**하게 합니다(재사용 불가 → 컴포지션마다 새 Owner 생성).
   * CameraX 객체(`PreviewView`, `ProcessCameraProvider`, use case)는 **ViewModel에 두지 않습니다**(구성 변경 시 View/Context 누수). ViewModel은 `cameraShouldRun`, 렌즈, 촬영 해상도라는 "원하는 상태"만 노출합니다.

3. **use case 재구성(렌즈/촬영 해상도 변경) 규칙**:
   * 모든 CameraX 호출은 메인 스레드에서 수행합니다. Provider는 `ProcessCameraProvider.awaitInstance(context)`로 1회 획득합니다.
   * `LaunchedEffect(provider, lens, captureResolution)` 내부에서: `provider.unbind(oldPreview, oldCapture)`(자신의 use case만 해제, `unbindAll()` 지양) → 새 use case 생성 → `provider.hasCamera(selector)` 확인(없으면 다른 렌즈로 폴백 + 안내) → `runCatching { provider.bindToLifecycle(gatedOwner, selector, preview, imageCapture) }`.
   * `captureInFlight > 0`일 때는 렌즈/해상도 변경 UI를 비활성화합니다.
   * `camera.cameraInfo.cameraState`를 관찰해 `ERROR_CAMERA_IN_USE`, `ERROR_MAX_CAMERAS_IN_USE`, `ERROR_CAMERA_DISABLED`(기업 정책) 등은 프리뷰 위 오류 오버레이 + [다시 시도]로 표시합니다.

4. **촬영 ↔ 전송 레이스 방어**:
   * 촬영은 반드시 `ImageCapture.OutputFileOptions`로 **`images/staging/` 파일에 직접 저장**합니다. 고해상도에서 `OnImageCapturedCallback`(메모리 `ImageProxy`)은 사용하지 않습니다.
   * `takePicture` 호출 시 ViewModel의 `captureInFlight`를 증가, 콜백(성공/실패)에서 감소합니다. 결과는 컴포저블 상태가 아닌 **ViewModel로 전달**(컴포저블이 이미 dispose되어도 결과 유실 없음).
   * `captureInFlight > 0`이면 전송 버튼은 "처리 중" 상태로 비활성화합니다. Gate가 닫히는 중 실패(`ERROR_CAMERA_CLOSED`)는 취소로 간주하고 부분 파일을 삭제합니다.

5. **권한 / 회전 / IME**:
   * `CAMERA` 런타임 권한 허용 전에는 바인딩하지 않습니다. 거부 시 **갤러리 전용 모드**(플레이스홀더 + 설정 이동 버튼)로 동작합니다.
   * Activity는 `portrait` 고정 + `OrientationEventListener`로 `imageCapture.targetRotation`을 갱신해 가로 촬영본의 EXIF 방향을 정확히 기록합니다.
   * **탐색 모드에도 채팅 입력창이 있으므로 카메라가 켜진 상태에서 IME가 올라옵니다.** 프리뷰 찌그러짐은 바인딩 해제가 아니라 레이아웃으로 해결합니다: `enableEdgeToEdge()` + `windowSoftInputMode="adjustResize"`, `PreviewView`는 `fillMaxSize()`로 inset을 무시하고, `imePadding()`은 `BottomCaptureAndInputBar`에만 적용합니다. `scaleType = FILL_CENTER`.
   * `PreviewView.implementationMode = COMPATIBLE`(TextureView)을 사용해 드로어 스크림·전환 애니메이션 시 SurfaceView z-order 깜빡임/검은 화면을 방지합니다.

6. **프로세스 종료 대비**: `screenMode`, `activeConversationId`, 대기 첨부(staging 상대경로 목록)는 `SavedStateHandle`에 저장합니다.

---

### 4.2. 3종 이미지 저장 및 해상도 전처리 파이프라인 (`ImagePreprocessor` & `InternalImageStorage`)

1. **디렉터리 구조 및 경로 규칙**:
   * `files/images/staging/{uuid}.{ext}`, `{uuid}_thumb.webp`: **전송 전 대기 첨부**. 촬영/갤러리 선택 즉시 생성, 전송 시 커밋(이동), `X` 삭제 시 즉시 삭제.
   * `files/images/original/{uuid}.{ext}`: 리사이징 없는 원본 바이트 그대로(jpg/heic/png/webp 등 **원래 확장자 유지**, `originalMimeType` 기록).
   * `files/images/payload/{uuid}_{maxSide}_r{revision}.{webp|jpg}`: API 전송용. 프리셋에 맞춰 축소 + **WebP 85%**(프리셋이 JPEG 지정 시 JPEG 90%).
   * `files/images/thumb/{uuid}.webp`: 썸네일 (긴 변 최대 400px, WebP 80%).
   * **DB에는 `images/` 기준 상대 경로만 저장**하고 `InternalImageStorage.resolve(relPath)`로 런타임에 `File`을 만듭니다(`filesDir` 절대경로는 기기 이전·멀티유저·백업 복원 시 바뀔 수 있음).
   * `images/`, Room DB, 키 저장소는 **Auto Backup에서 제외**합니다(25MB 백업 한도 초과 + DB만 복원되어 경로가 깨지는 불일치 방지).
   * 모든 쓰기는 **원자적 쓰기**: 같은 디렉터리의 `*.tmp`에 기록 → `fd.sync()` → `renameTo()`. 전송 중 읽기와 재생성 쓰기가 겹쳐도 반쯤 쓰인 파일을 읽지 않습니다.

2. **입력 확보 (Staging)**:
   * 카메라: `OutputFileOptions`로 staging에 직접 저장(MediaStore 경유 없음).
   * 갤러리: `PickVisualMedia`/`PickMultipleVisualMedia`가 반환한 URI는 **임시 권한**이므로 선택 즉시 `contentResolver.openInputStream()`으로 staging에 복사합니다(전송 시점·프로세스 재시작 후에는 접근 불가할 수 있음). 크기 상한(예: 64MB)과 MIME 화이트리스트를 검사합니다.
   * 썸네일은 staging 단계에서 즉시 생성해 입력창 썸네일 스트립에 사용합니다.

3. **디코딩·리사이징 OOM 방어**:
   * 50MP급 원본을 `ARGB_8888`로 통째 디코딩하면 약 200MB로 대부분의 앱 힙 한도를 넘습니다. Rev.1의 "`inSampleSize` 디코딩 → `createScaledBitmap` → 회전 `Matrix`"는 **비트맵 3장이 동시에 존재**할 수 있어 위험합니다.
   * **API 28+ `ImageDecoder` 우선**: `setTargetSize(w, h)`로 목표 크기에 직접 디코딩, `allocator = ALLOCATOR_SOFTWARE`(compress 가능해야 하므로 HARDWARE 금지). `ImageDecoder`는 EXIF 회전을 자동 적용하므로 **추가 회전을 하지 않습니다**(이중 회전 방지 — 기기 테스트 필수).
   * `BitmapFactory` 폴백 시: 목표 이상을 유지하는 최대 2의 거듭제곱 `inSampleSize`로 디코딩 → **스케일+회전을 하나의 `Matrix`로 `Bitmap.createBitmap()` 1회 적용** → 원본 비트맵 즉시 `recycle()`.
   * 디코딩 전 `width × height × 4`를 가용 힙(`Runtime.maxMemory() - 사용량`)과 비교해 부족하면 `inSampleSize`를 키웁니다. 대형 할당의 `OutOfMemoryError`는 1회에 한해 샘플 크기 2배로 재시도 후 첨부 단위 오류로 처리합니다.
   * 이미지 처리 전체를 `Dispatchers.Default.limitedParallelism(1)`(또는 `Semaphore(1)`)로 **직렬화**합니다. 여러 장 동시 처리는 메모리 사용량을 곱으로 늘립니다.
   * 썸네일과 payload를 같은 원본에서 만들 때 디코딩을 재사용합니다.

4. **인코딩 규칙**:
   * **API 레벨별 WebP 포맷 분기**: `minSdk 29` 기준이므로 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R`(API 30+)에서는 `Bitmap.CompressFormat.WEBP_LOSSY`를, API 29(Android 10)에서는 `@Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP`(quality < 100일 때 lossy 동작)를 사용합니다(`NoSuchFieldError` 방지).
   * WebP는 한 변 최대 **16383px** 제한이 있으므로 `ORIGINAL` 프리셋도 이 값으로 클램프합니다(파노라마 사진 인코딩 실패 방지).
   * payload는 항상 **재인코딩본**이며 EXIF(GPS 등)를 포함하지 않습니다. 원본 파일을 그대로 전송하지 않습니다(위치 정보 유출 방지).
   * `ORIGINAL` 프리셋은 원본 해상도 재인코딩이므로 시간이 오래 걸리고 저장 공간을 두 배로 쓸 수 있음을 설정 UI에 안내합니다.

5. **실효 해상도와 지연(Lazy) 재생성**:
   * 우선순위: **첨부 개별 지정(`requestedPreset ≠ DEFAULT`) > 대화방 오버라이드 > 전역 설정**. 실제 생성된 파일의 해상도는 `payloadMaxSide`로 별도 기록합니다(전역 설정이 바뀌면 DEFAULT 첨부의 payload가 낡게 되는 문제 해결).
   * payload는 **실제로 이미지로 전송될 첨부에 한해, 전송 직전** `EnsurePayloadUseCase`가 생성/재생성합니다. 텍스트 대치될 이전 턴 사진은 재생성하지 않습니다. 첨부별 `Mutex`로 동시 재생성을 막습니다.
   * 교체 순서: 새 payload 원자적 쓰기 → **단일 트랜잭션**(payload 경로·`payloadMaxSide` 갱신, `payloadRevision + 1`, 해당 첨부의 `remote_file_cache` 삭제, 구 경로를 `pending_file_deletions`에 기록) → 구 파일 삭제 → 큐에서 제거.

6. **개별 사진 vs 대화 전체 해상도 변경 로직**:
   * 채팅창에서 사진을 길게 눌러 **[전송 해상도 변경]** 선택 시 옵션 선택 후 **"변경한 해상도를 현재 대화 전체에 적용할까요?"** 확인 창을 표시합니다.
   * **[현재 대화 전체 적용 (예)]**: 하나의 트랜잭션에서 `ConversationEntity.overrideResolutionPreset` 갱신 + 해당 대화 모든 첨부의 `requestedPreset`을 `DEFAULT`로 초기화 + 원격 파일 캐시 무효화. **파일 재생성은 즉시 일괄 수행하지 않고** 다음 전송 시 필요한 첨부만 지연 생성합니다(대화 내 수십 장 일괄 재인코딩으로 인한 발열·OOM·대기 방지).
   * **[이 사진만 적용 (아니오)]**: 해당 첨부의 `requestedPreset`만 변경 + 해당 첨부 원격 캐시 무효화.
   * 해상도 변경/재생성 진행 중에는 해당 대화의 전송 버튼을 잠시 비활성화하거나 전송이 첨부 `Mutex`를 기다리도록 합니다.

---

### 4.3. 로컬 DB 스키마 설계 (Room)

바이너리 데이터는 DB에 저장하지 않습니다. 외래키는 `CASCADE`로 설정하되, **CASCADE는 파일을 지우지 못하므로** 행을 삭제하는 모든 경로(대화 삭제, 메시지 수정에 따른 이후 턴 절단, 첨부 교체, payload 재생성)는 `ConversationRepository`를 경유하여 다음 순서를 따릅니다.

1. **하나의 `@Transaction`** 안에서: 영향받는 파일 상대경로 조회 → `pending_file_deletions`에 INSERT → (Gemini 원격 파일이 있으면 `pending_remote_deletions`에 INSERT) → 행 DELETE(CASCADE).
2. 커밋 후 IO 스레드에서 실제 파일 삭제 → 성공한 경로를 큐에서 제거.
3. 크래시 등으로 남은 큐는 다음 앱 시작 시 `StartupMaintenance`가 처리합니다.

> [!NOTE]
> Rev.1의 "파일 먼저 삭제 → DB 삭제" 순서는 DB 삭제 실패 시 **깨진 썸네일을 가진 행**을, 반대 순서의 단순 구현은 크래시 시 **고아 파일**을 남깁니다. 삭제 큐는 두 경우 모두를 복구 가능하게 만들고, `OrphanFileSweeper`는 최후의 안전망입니다.

```kotlin
// 1. API 프리셋 테이블
//  - isSelected 컬럼 제거: 여러 행이 동시에 true가 되는 불일치 방지 → 선택 ID는 DataStore(selectedPresetId)에 단일 값으로 저장
@Entity(tableName = "api_presets")
data class ApiPresetEntity(
    @PrimaryKey val id: String,              // UUID
    val alias: String,                       // 상단 바에 표시될 별칭
    val provider: ApiProvider,               // GEMINI, OPENAI
    val customBaseUrlEnabled: Boolean,       // OpenAI 선택 시 커스텀 URL 사용 여부
    val customBaseUrl: String?,              // 저장 시 BaseUrlNormalizer로 정규화된 값 (LiteLLM / 로컬 프록시)
    val modelId: String,                     // 예: gemini-2.5-flash, gpt-4o 등
    val imagePayloadFormat: ImagePayloadFormat, // WEBP(기본) / JPEG (webp 미지원 백엔드 대응)
    val maxRequestBytes: Long?,              // 요청 바디 상한 (null = 공급자 기본값, 프록시는 사용자 지정)
    val createdAt: Long
)
// * API Key는 preset.id를 키로 하여 SecureKeyStorage(Tink AEAD + DataStore)에 분리 저장.
// * 프리셋 삭제 시 키도 함께 삭제. 선택된 프리셋 삭제/최초 실행 시 프리셋 없음 → 온보딩(프리셋 생성) 유도.

// 2. 대화방 테이블
@Entity(tableName = "conversations", indices = [Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey val id: String,              // UUID
    val title: String,                       // 첫 질문 요약 또는 자동 제목
    val overrideResolutionPreset: ImageResolutionPreset?, // 대화방 전체 해상도 (null이면 전역 설정 사용)
    val createdAt: Long,
    val updatedAt: Long                      // 드로어 정렬 기준
)

// 3. 메시지 테이블
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["conversationId", "position"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey val id: String,              // UUID
    val conversationId: String,
    val position: Int,                       // 대화 내 단조 증가 순번 (정렬·절단 기준, 중복 불가)
    val turnIndex: Int,                      // 턴 순서 (USER/ASSISTANT 쌍이 동일 값)
    val role: MessageRole,                   // USER, ASSISTANT
    val textContent: String,                 // 질문 또는 답변 텍스트
    val status: MessageStatus,               // LOADING, SUCCESS, ERROR, CANCELLED
    val errorCode: String?,                  // ApiError 종류 (재시도 가능 여부 판단)
    val errorMessage: String?,               // 오류 상세 메시지
    val presetIdUsed: String?,               // 응답 생성에 사용된 프리셋 (공급자 전환 추적)
    val modelIdUsed: String?,
    val providerMetadataJson: String?,       // 공급자 원문 메타 (예: Gemini thoughtSignature) — 동일 공급자일 때만 재전송
    val createdAt: Long
)

// 4. 메시지 첨부 사진 테이블
@Entity(
    tableName = "message_attachments",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("messageId")]
)
data class MessageAttachmentEntity(
    @PrimaryKey val id: String,              // UUID
    val messageId: String,
    val sortIndex: Int,                      // 메시지 내 사진 순서
    val originalRelPath: String,             // original/{uuid}.{ext}  (images/ 기준 상대경로)
    val originalMimeType: String,            // image/jpeg, image/heic ...
    val originalWidth: Int,                  // 회전 보정 후 크기
    val originalHeight: Int,
    val thumbnailRelPath: String,            // thumb/{uuid}.webp
    val payloadRelPath: String?,             // payload/... — null이면 아직 미생성(지연 생성)
    val payloadMaxSide: Int?,                // 실제 생성된 payload의 긴 변 (실효 해상도와 비교)
    val payloadRevision: Int,                // 재생성마다 +1 → 원격 캐시 무효화 키
    val requestedPreset: ImageResolutionPreset, // DEFAULT(상속), ORIGINAL, HIGH_4000, STANDARD_2000, SAVER_1000
    val isTextReplaced: Boolean,             // 수동 텍스트 대치 강제 여부
    val imageCaption: String?                // 텍스트 대치용 요약/캡션 캐시
)

// 5. Gemini 원격 파일 캐시 (첨부 행에서 분리)
//  - Files API 파일은 "업로드한 API 키의 프로젝트"에 귀속 → 프리셋(키) 전환 시 다른 키로는 접근 불가.
//  - 키·payload 리비전 단위로 캐시하여 프리셋을 오가도 서로 덮어쓰지 않음.
@Entity(
    tableName = "remote_file_cache",
    primaryKeys = ["attachmentId", "credentialFingerprint"],
    foreignKeys = [ForeignKey(
        entity = MessageAttachmentEntity::class,
        parentColumns = ["id"],
        childColumns = ["attachmentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RemoteFileCacheEntity(
    val attachmentId: String,
    val credentialFingerprint: String,       // HMAC-SHA256(appSalt, apiKey) 앞 16자 — 키 원문 미저장
    val payloadRevision: Int,                // 첨부의 payloadRevision과 다르면 무효
    val fileName: String,                    // "files/abc123" (상태 조회·삭제용)
    val fileUri: String,                     // generateContent의 file_data.file_uri
    val mimeType: String,
    val state: String,                       // PROCESSING, ACTIVE, FAILED
    val expiresAt: Long,                     // 서버 응답 expirationTime (로컬 시각 보정 후 저장)
    val createdAt: Long
)

// 6. 파일 삭제 큐
@Entity(tableName = "pending_file_deletions")
data class PendingFileDeletionEntity(
    @PrimaryKey val relPath: String,
    val enqueuedAt: Long
)

@Entity(tableName = "pending_remote_deletions", primaryKeys = ["fileName", "credentialFingerprint"])
data class PendingRemoteDeletionEntity(
    val fileName: String,                    // files/abc123
    val credentialFingerprint: String,       // 해당 키를 가진 프리셋이 없으면 포기 (48시간 후 자동 만료)
    val enqueuedAt: Long
)
```

**스키마 운영 규칙**:
* `exportSchema = true`, 버전마다 `Migration`/`AutoMigration` 작성 및 `MigrationTestHelper`로 검증. **릴리스에서 `fallbackToDestructiveMigration()` 금지** — DB만 초기화되고 `images/` 파일이 전부 고아가 됩니다(부득이하게 사용할 경우 `images/` 전체 삭제를 함께 수행).
* Enum은 이름(String)으로 저장하고 이름 변경 금지(변경 시 마이그레이션). `ContextRetentionMode` 같은 sealed 타입은 DataStore에 직렬화하여 저장.
* 드로어 검색: 제목은 `LIKE`, 메시지 본문 검색이 필요하면 `@Fts4(contentEntity = MessageEntity::class)` 보조 테이블 사용.
* 스트리밍 도입 시 응답 텍스트를 토큰마다 DB에 쓰지 않고 메모리에서 누적 후 일정 간격(예: 300ms)으로만 갱신(Room invalidation 폭주 방지).

---

### 4.4. API 연동 및 컨텍스트·페이로드 최적화 (`BuildApiPayloadUseCase`)

#### 4.4.1. 공통 네트워크 정책

* **타임아웃**: connect 15s / write 120s(업로드) / read 180s(비스트리밍 추론 모델은 첫 바이트까지 수십 초 이상 소요). OkHttp 기본 10s는 사용하지 않습니다. 이후 스트리밍(Gemini `streamGenerateContent?alt=sse`, OpenAI `stream: true`) 도입 시 read 타임아웃은 "청크 간 간격" 기준으로 재조정.
* **로그**: 릴리스 `Level.NONE`. 디버그도 `HEADERS`까지만, `redactHeader("Authorization")`, `redactHeader("x-goog-api-key")` 적용. **`BODY` 레벨 금지**(수 MB Base64가 로그·메모리로 복제되어 OOM/ANR 유발).
* **Gemini 인증**은 쿼리 파라미터 `?key=` 대신 `x-goog-api-key` 헤더 사용(URL 로그·프록시 로그 유출 방지).
* **직렬화**: `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`.
* **오류 매핑(`ApiErrorMapper`)**: 401/403 → `Auth`, 404 → `NotFound`(모델명 오류 안내), 413 → `PayloadTooLarge`, 429 → `RateLimited`(`Retry-After` 존중), 5xx → `Server`, 안전 차단 → `Blocked`, 파일 참조 실패 → `FileUnavailable`, `IOException` → `Network`, 타임아웃 → `Timeout`, **JSON이 아닌 오류 본문(프록시의 HTML 502 등)** → `Parse`(원문 앞부분 표시).
* **자동 재시도**: `RateLimited`/503/`Network`만 지수 백오프 최대 2회. 그 외 4xx는 즉시 오류 표시 + [재시도] 버튼.
* **취소**: 전송 Job은 `@ApplicationScope`에서 실행(대화 전환·화면 이탈로 취소되지 않음)하고, 사용자가 [중지]를 누르면 명시 취소 → 메시지 `CANCELLED`.

#### 4.4.2. Gemini File API (Resumable Upload & 48시간 만료)

1. **업로드 프로토콜**:
   * Step 1 (start): `POST https://generativelanguage.googleapis.com/upload/v1beta/files` + 헤더 `X-Goog-Upload-Protocol: resumable`, `X-Goog-Upload-Command: start`, `X-Goog-Upload-Header-Content-Length`, `X-Goog-Upload-Header-Content-Type` + JSON `{"file": {"display_name": "<attachmentId>"}}` → 응답 헤더 `x-goog-upload-url`.
   * Step 2 (upload): 해당 URL로 `X-Goog-Upload-Offset: 0`, `X-Goog-Upload-Command: upload, finalize` + 파일 바이트(스트리밍 `RequestBody`).
   * 이미지는 수 MB 이하이므로 중단 시 **처음부터 재시작**을 기본으로 합니다(필요 시 `X-Goog-Upload-Command: query`로 수신 바이트를 확인해 이어 올리기).
   * 응답 `file.state`가 `PROCESSING`이면 `GET v1beta/{name}`을 짧은 백오프로 폴링(최대 ~10초), `FAILED`이면 해당 이미지는 Base64로 폴백.
2. **캐시 유효성 판정** (`remote_file_cache`):
   * `(attachmentId, 현재 키 fingerprint)` 행이 있고, `payloadRevision`이 일치하며, `state == ACTIVE`이고, `expiresAt - now > 2시간`일 때만 재사용합니다.
   * 만료 시각은 로컬 `uploadedAt + 47h`가 아니라 **서버 응답의 `expirationTime`** 을 사용합니다. 기기 시계 오차를 줄이기 위해 응답 `Date` 헤더로 `serverOffset`을 계산해 보정하고, 여유 마진(2시간)은 긴 요청·추론 시간까지 포괄합니다.
   * **프리셋(API 키) 전환 시 다른 fingerprint이므로 자동으로 캐시 미스 → 재업로드**됩니다(다른 프로젝트 키로 기존 URI를 참조하면 403/404).
3. **동시성 (single-flight)**: `(attachmentId, fingerprint)` 단위 `Mutex`로 재시도 탭·백그라운드 캡션·재전송이 같은 사진을 중복 업로드하지 않게 합니다.
4. **사후 실패 복구**: 48시간 이전에도 파일은 삭제될 수 있습니다(다른 클라이언트의 삭제, 프로젝트 20GB 한도 등). `generateContent`가 파일 참조 관련 400/403/404를 반환하면 → 해당 요청에 쓰인 캐시 행 삭제 → 재업로드 → **1회만** 재시도 → 그래도 실패하면 해당 이미지를 Base64 인라인으로 폴백(크기 Preflight 통과 시).
5. **정리**: payload 재생성·대화 삭제 시 이전 파일은 `pending_remote_deletions`에 넣어 `DELETE v1beta/{name}`을 best-effort로 호출합니다(개인정보 보호 + 프로젝트 저장 한도). 앱이 업로드 직후 종료되어 DB에 기록되지 못한 원격 파일은 48시간 후 자동 만료되므로 허용합니다.
6. **요청 형식**: `{"file_data": {"mime_type": "image/webp", "file_uri": "<uri>"}}`.

#### 4.4.3. Base64 전송 & 413 방어 (Preflight)

1. **스트리밍 바디 (`StreamingJsonBody`)**: Base64 문자열을 메모리에 만들지 않습니다. `RequestBody.writeTo(sink)`에서 JSON 앞부분을 쓰고, payload 파일을 `java.util.Base64.getEncoder().wrap(닫히지 않는 sink 래퍼)`로 스트리밍 인코딩한 뒤 JSON 뒷부분을 씁니다. Base64 길이는 `4 × ceil(n / 3)`로 정확히 계산 가능하므로 `contentLength()`를 정확히 반환해 chunked 전송을 거부하는 프록시와의 호환성을 확보합니다. (Rev.1처럼 `ByteArray → Base64 String → JSON String → OkHttp 버퍼`로 가면 이미지당 원본의 4~5배 메모리가 동시에 필요)
2. **Preflight 크기 계산 (`PayloadSizeEstimator`)**: 전송 직전 예상 바디 크기 = Σ(Base64 길이) + 텍스트·JSON 오버헤드. 상한은 프리셋의 `maxRequestBytes` 또는 공급자 기본값(Gemini 인라인 요청 한도·OpenAI 요청 한도는 공식 문서 값을 상수로 두고 여유 마진 적용). **커스텀 프록시는 nginx 기본 `client_max_body_size 1m`처럼 훨씬 작을 수 있으므로 사용자 지정값을 둡니다.**
3. **초과 시 단계적 강등**(사용자에게 토스트로 고지): ① Gemini면 해당 이미지들을 File API로 전환 → ② 오래된 이미지부터 텍스트 대치 → ③ 해상도 한 단계 하향(2000 → 1000) → ④ 그래도 초과하면 전송 차단 + 원인 안내.
4. 실제 413 응답을 받으면 같은 강등 경로를 **1회** 자동 적용 후 재시도, 이후엔 오류 표시.

#### 4.4.4. OpenAI API (및 LiteLLM 등 호환 프록시)

1. 공급자가 `OPENAI`이면 설정 UI 및 전송 로직에서 **자동으로 `BASE64` 방식으로 고정**됩니다(`File API` 비활성화).
2. **Base URL 정규화 (`BaseUrlNormalizer`)** — 저장 시점에 수행하고 결과를 UI에 "요청 주소: `https://host/v1/chat/completions`"로 미리보기:
   * 공백 제거, 스킴 누락 시 오류(자동으로 `https://` 붙이지 않음 — 로컬 `http://` 서버 혼동 방지).
   * 끝의 `/` 제거, 사용자가 `/chat/completions`까지 붙여 넣었으면 제거.
   * `/v1`을 **자동으로 붙이지 않음**(OpenRouter `…/api/v1`, 기타 프록시 경로 다양). 기본값(커스텀 미사용)은 `https://api.openai.com/v1`.
   * 최종 엔드포인트 = `"$normalizedBase/chat/completions"`. (Rev.1의 파일 트리 주석 `/v1/chat/completions`와 본문 `{customBaseUrl}/chat/completions` 표기 불일치를 이 규칙으로 통일)
   * [연결 테스트] 버튼: `GET $normalizedBase/models` → 실패 시 최소 텍스트 요청으로 재확인, 결과를 `ApiError`로 표시.
3. **Retrofit 사용 시 주의**: `@POST("/chat/completions")`처럼 **선행 `/`가 붙은 상대 경로는 Base URL의 `/v1` 경로를 버립니다.** 동적 URL은 `@POST suspend fun chat(@Url url: String, @Header("Authorization") auth: String?, @Body body: RequestBody)`로 전체 URL을 넘깁니다.
4. **로컬/사설 서버**:
   * `http://` 평문 통신은 기본 차단되므로 `network_security_config.xml`에서 `<base-config cleartextTrafficPermitted="true">`로 커스텀 서버를 허용하되, **공식 엔드포인트(`generativelanguage.googleapis.com`, `api.openai.com`)는 `<domain-config cleartextTrafficPermitted="false">`로 HTTPS를 강제**합니다. 또한 `http://` + API Key 조합 저장 시 UI에서 경고합니다.
   * 자체 서명 인증서 프록시를 위해 사용자 설치 CA 신뢰(`<certificates src="user"/>`)를 opt-in으로 제공.
   * 안내 문구: 기기에서 `localhost`는 PC가 아닌 기기 자신, 에뮬레이터에서 호스트 PC는 `10.0.2.2`.
   * 커스텀 URL 사용 시 API Key는 **선택 입력**(비어 있으면 `Authorization` 헤더 생략).
5. **이미지 포함 형식**: `{"type": "image_url", "image_url": {"url": "data:image/webp;base64,{base64Data}", "detail": "auto"}}`. 프리셋의 `imagePayloadFormat`이 JPEG이면 `data:image/jpeg`. 일부 호환 백엔드는 WebP를 지원하지 않으므로 이 옵션을 둡니다. `detail`(low/high/auto)은 비용 제어용 설정으로 노출합니다.
6. **응답 관용 파싱**: `choices` 빈 배열, `message.content`가 `null` 또는 파트 배열인 경우, 비표준 `reasoning_content` 필드(표시 안 함), `finish_reason: length / content_filter`를 모두 처리합니다.
7. **시스템 프롬프트 매핑**: Gemini는 최상위 `systemInstruction`, OpenAI는 `role: "system"`(일부 추론 모델·프록시는 `developer`를 요구할 수 있어 프리셋 고급 옵션으로 선택 가능).

#### 4.4.5. 컨텍스트 조립 & 이전 턴 사진 텍스트 대치

* **Step 0 (히스토리 정제)**:
  * `LOADING`/`ERROR`/`CANCELLED` 상태의 ASSISTANT 메시지와, 성공 응답이 없는 과거 USER 메시지는 제외합니다(현재 전송/재시도 중인 USER 메시지만 예외). → Gemini `user`/`model` 역할 교대 보장 및 실패 턴 중복 전송 방지.
  * **재시도**는 USER 메시지를 새로 만들지 않고 기존 메시지를 재사용, ASSISTANT 메시지만 `LOADING`으로 되돌립니다.
* **Step 1 (컨텍스트 범위 필터링)**:
  * `ContextRetentionMode.ALL`: 대화방의 전체 메시지 히스토리.
  * `ContextRetentionMode.SLIDING_WINDOW(n)`: 최근 `N`개 턴(기본 10턴 = User/Assistant 쌍 기준)만 슬라이싱하며, 윈도우의 첫 메시지는 반드시 USER가 되도록 맞춥니다.
* **Step 2 (사진 포함 vs 텍스트 대치 판별)**:
  * Rev.1의 `isLatestTurn` 기준은 **"이 부분 더 자세히 알려줘" 같은 텍스트 전용 후속 질문에서 직전 사진이 텍스트로 대치되어 모델이 사진을 볼 수 없는** 치명적 문제가 있습니다. 렌즈형 앱의 가장 흔한 후속 질문 패턴이므로 기준을 다음으로 변경합니다.
  * `isInRecentImageTurns`: **이미지가 첨부된 가장 최근 K개 턴**(설정 `keepRecentImageTurns`, 기본 1)에 속하는가?
  * 실제 이미지 페이로드(File URI 또는 Base64) 첨부 조건: `!attachment.isTextReplaced && (isInRecentImageTurns || !autoTextReplaceOldImages)`.
  * 그 외 사진은 이미지 바이너리를 제외하고 현재 앱 로케일(`en-US` 기본 / `ko`)에 맞춘 리소스 템플릿으로 치환합니다:
    * `imageCaption`이 캐싱되어 있는 경우: `"[Previous turn attached image summary: ${attachment.imageCaption}]"` (`ko`: `"[이전 턴 첨부 사진 요약: ${attachment.imageCaption}]"`)
    * 캡션이 없는 경우(추가 API 비용 없음): `turnQuestionSummary` = 해당 턴 USER 질문 원문 앞 120자 + ASSISTANT 응답 앞 200자로 정의하고 `"[Previous turn attached image (question context: ${turnQuestionSummary})]"` (`ko`: `"[이전 턴 첨부 사진 (질문 맥락: ${turnQuestionSummary})]"`)로 치환합니다.
  * *(선택적 백그라운드 캡셔닝, opt-in)*: 별도 비용이 발생하므로 기본 OFF, 캡션 전용 모델을 설정에서 지정. 메인 업로드와 같은 single-flight·원격 캐시를 공유하고, 실패해도 대화 흐름에 영향을 주지 않습니다.
* **Step 3**: 4.4.3의 Preflight 크기 검사 → 필요 시 단계적 강등.
* **공급자 전환**: 히스토리는 공급자 중립 도메인 모델로 조립합니다. `providerMetadataJson`(예: Gemini 3 계열 `thoughtSignature`)은 같은 공급자일 때만 재전송하고, 공급자가 바뀌면 버립니다.

#### 4.4.6. API Key 보관

* `SecureKeyStorage`: Android Keystore 마스터 키로 감싼 **Tink AEAD**로 암호화 후 DataStore에 저장.
* Keystore 손상/복원 실패(`GeneralSecurityException`) 시 크래시 대신 저장소를 초기화하고 다국어 리소스(`R.string.error_keystore_corrupted_reenter_key`)로 안내.
* 키 저장소는 백업 제외(다른 기기로 복원 시 복호화 불가 데이터가 되므로).

---

### 4.5. 화면 모드·대화 정책 및 주요 인터랙션 흐름

1. **상태 모델**:
   * `screenMode`: `CAMERA_EXPLORE` / `CHAT_ACTIVE` (카메라 실행 여부는 4.1의 파생 상태)
   * `activeConversationId: String?`: **화면 모드와 독립**. 탐색 모드에서도 유지될 수 있습니다.
   * 정책:
     * 대화 패널에서 뒤로 가기 → 탐색 모드로 돌아가되 `activeConversationId` **유지**. 상단에 `"Continuing in ‘{title}’"` / `"‘{대화 제목}’에 이어서"` 칩을 표시하고, 칩의 `X`로 분리하면 다음 전송은 새 대화.
     * 대화 패널의 [카메라] 버튼 → 같은 대화를 유지한 채 탐색 모드로 전환(추가 촬영 후 전송 시 같은 대화에 이어서 추가).
     * 드로어의 `+ 새 대화` → `activeConversationId = null` + 탐색 모드.
     * 드로어에서 기존 대화 선택 → 해당 ID + `CHAT_ACTIVE`.

2. **메인 카메라 화면 (`MainScreen` - 탐색 모드)**
   * 앱 실행 즉시 전체 화면 CameraX 프리뷰 동작(권한 미허용 시 갤러리 전용 모드).
   * **좌측 상단 햄버거 버튼**: 탐색 드로어(`NavigationDrawerContent`) 오픈. (드로어 스와이프 열기는 비활성 — 4.6 참고)
     * 상단 검색 바(실시간 키워드 필터링) → `+ 새 대화` 버튼 → 대화 히스토리 목록(길게 누르면 삭제 확인 다이얼로그) → 하단 `설정` 버튼(드로어 닫힘 완료 후 설정 시트 오픈).
   * **햄버거 버튼 오른쪽 API 프리셋 칩**: 현재 활성 프리셋 별칭 표시. 탭하면 저장된 프리셋 드롭다운 메뉴가 열려 즉시 API/모델 전환 가능.
   * **우측 상단 톱니바퀴 버튼**: 언제든 `SettingsBottomSheet` 호출.
   * **하단 영역**:
     * 사진 촬영 또는 갤러리 선택 시 입력창 바로 위에 **첨부 사진 썸네일 리스트**가 생성됨(staging 저장, 여러 장 추가 및 개별 `X` 삭제 시 staging 파일 즉시 삭제).
     * 하단 왼쪽: **갤러리 버튼** (`PickVisualMedia`) / 하단 중앙: **촬영 버튼** / 하단: **채팅 입력창 + 오른쪽 전송 버튼**.

3. **전송 흐름 (`SendChatMessageUseCase`)** — 전송 버튼 터치 시:
   1. **검증**: 활성 프리셋·키 존재, `captureInFlight == 0`, 해상도 재생성 진행 중 아님. 실패 시 전송하지 않고 안내.
   2. IME 숨김 + 포커스 해제 → `screenMode = CHAT_ACTIVE` (카메라는 Gate에 의해 자동 비활성화, 촬영 콜백 대기 중이면 완료 후 비활성화).
   3. **커밋(빠름)**: staging의 원본·썸네일을 `original/`, `thumb/`으로 `renameTo` 이동(같은 파일시스템 → 원자적·즉시) → 단일 트랜잭션으로 대화(필요 시 생성), USER 메시지, 첨부 행(`payloadRelPath = null`), ASSISTANT `LOADING` 메시지 INSERT. 이 시점부터 채팅 UI에 질문 말풍선 + 로딩 상태 표시.
   4. **payload 보장**: 4.4.5 Step 2 기준으로 실제 이미지로 보낼 첨부만 `EnsurePayloadUseCase`로 생성(WebP 85% 등).
   5. **(Gemini + FILE_API) 업로드** → 요청 조립(Preflight 포함) → API 호출.
   6. 성공 시 마크다운 응답 표시(`SUCCESS`) / 실패 시 **오류 메시지 + [재시도] 버튼**(`ERROR`, `errorCode` 기반 다국어 안내).
   * 3~6단계는 `@ApplicationScope`에서 실행되며, 프로세스가 중간에 종료되면 다음 시작 시 `StartupMaintenance`가 `LOADING` → `status = ERROR`, `errorCode = "INTERRUPTED_BY_EXIT"`(언어 중립 코드)로 정리해 현재 UI 언어(`en-US` / `ko`)로 안내하고 재시도 가능하게 합니다.

4. **채팅 내 인터랙션 (`ChatPanel` - 대화 모드)**:
   * **사진 썸네일 짧게 탭**: 로컬 원본(`originalRelPath`)을 **서브샘플링 줌 뷰어**(Telephoto)로 오픈 — 대형 원본을 통째 디코딩하지 않음.
   * **대화(텍스트) 길게 누르기**: `복사` / `텍스트 선택` / `수정` 메뉴 표시.
     * 말풍선 전체에 `SelectionContainer`를 씌우면 길게 누르기가 텍스트 선택으로 소비되어 컨텍스트 메뉴와 충돌하므로, 선택은 `텍스트 선택` 메뉴로 진입하는 별도 모드에서 제공.
     * **USER 메시지 수정**: "이후 대화가 삭제됩니다" 경고 → `EditMessageUseCase`가 해당 메시지 이후 `position`의 메시지·첨부를 삭제 큐 경유로 삭제 → 수정 텍스트로 재전송.
     * **ASSISTANT 메시지 수정**: 로컬 텍스트만 수정(재전송 없음), 이후 컨텍스트에 수정본이 사용됨.
   * **사진 길게 누르기**: `복사`(payload 또는 원본을 `cache/share/`에 복사 후 FileProvider URI로 클립보드) / `교체`(새 사진 커밋 + 구 파일 삭제 큐) / `텍스트 대치 토글` / `전송 해상도 변경`(`기본값` / `원본` / `고화질 4000px` / `표준 2000px` / `절약 1000px`) → 변경 선택 시 **"현재 대화 전체에 적용할까요?"** 팝업 제공(거부 시 해당 사진만 적용).
   * 대화 패널 하단의 자체 입력바(텍스트 + 갤러리 + [카메라] 버튼)로 추가 질문이나 추가 사진을 첨부하며 멀티턴 대화 지속.

---

### 4.6. UI 레이어 구조 · IME · 뒤로 가기 규칙

1. **레이어 구조 (기본은 같은 윈도우)**:
   ```text
   ModalNavigationDrawer (gesturesEnabled = drawerState.isOpen)
    └─ Box(fillMaxSize)
        ├─ CameraPreviewLayer            // fillMaxSize, inset 미적용
        ├─ ExploreOverlay                // TopControlBar(statusBars padding) + BottomCaptureAndInputBar(navigationBars + imePadding)
        └─ AnimatedVisibility(screenMode == CHAT_ACTIVE)
             └─ ChatPanel                // 불투명 전체 화면 Surface, 자체 입력바(imePadding)
   SettingsBottomSheet  = ModalBottomSheet   // 별도 윈도우
   각종 Dialog / DropdownMenu / ContextMenu  // 별도 윈도우(Popup)
   ```
   * **대화 패널은 `ModalBottomSheet`를 사용하지 않습니다.** 이유:
     * `ModalBottomSheet`는 **별도 윈도우**로 그려져 ① 드로어가 시트 아래에 깔리고, ② 메인 윈도우의 `BackHandler`가 뒤로 가기를 받지 못하며, ③ 메인 윈도우의 Snackbar가 시트에 가려집니다.
     * 메시지 `LazyColumn`을 맨 위까지 스크롤한 뒤 계속 끌면 nested scroll이 시트로 전달되어 **대화 패널이 실수로 닫힙니다**.
     * 시트 윈도우 안의 텍스트 입력 + IME inset 처리는 M3 버전별 동작 차이가 큽니다.
     * 대화 모드에서는 카메라가 꺼져 있으므로 뒤가 비치는 시트 형태의 이점이 없습니다.
   * 시트 형태의 시각 효과가 꼭 필요하면 같은 윈도우의 `BottomSheetScaffold`를 사용하고 `sheetSwipeEnabled`를 목록 최상단이 아닐 때만 허용합니다.

2. **오버레이 단일화 (`OverlayState`)**: 모달 오버레이는 동시에 하나만 존재합니다(`None / Settings / PresetMenu / MessageMenu(id) / Dialog(...) / ImageViewer(id)`). 드로어는 `DrawerState`가 소유하되 열기/닫기 요청은 ViewModel의 단일 함수를 거칩니다.
   * 전환은 **"닫기 애니메이션 완료 후 열기"**: 예) 드로어의 `설정` → `drawerState.close()` 완료 후 `overlay = Settings`.
   * 화면 모드 전환(전송 등) 시 열려 있는 오버레이는 먼저 닫습니다.
   * 오버레이·드로어 상태는 ViewModel 또는 `rememberSaveable`로 보존(구성 변경 대비).

3. **ModalBottomSheet(설정) 사용 규칙**:
   * 표시 여부는 `if (overlay == Settings) ModalBottomSheet(onDismissRequest = { overlay = None }) {...}`로 조건부 컴포지션.
   * 코드로 닫을 때: `scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) overlay = None }` — 상태를 먼저 `false`로 바꾸면 애니메이션 없이 사라지거나 잔상이 남습니다. 진행 중인 `show()/hide()`를 중첩 호출하지 않습니다.
   * API Key·Base URL·멀티라인 시스템 프롬프트 입력이 있으므로 `skipPartiallyExpanded = true`, 내용은 `verticalScroll` + `imePadding()`, 포커스된 필드는 `BringIntoViewRequester`로 노출.

4. **IME 규칙**:
   * 오버레이를 열거나 화면 모드를 바꾸기 전에 항상 `focusManager.clearFocus()` + `keyboardController?.hide()`(입력 포커스가 메인 윈도우에 남아 IME가 시트·다이얼로그 위로 떠 있는 현상 방지).
   * 드로어가 닫힐 때 검색 필드 포커스 해제.
   * `ChatPanel` 메시지 목록은 `LazyColumn(reverseLayout = true)`로 IME 상승 시에도 최신 메시지가 보이게 하고, 이중 inset 적용을 막기 위해 상위에서 처리한 inset은 `consumeWindowInsets`로 소비합니다.
   * 대화 모드에서 후속 질문 전송 후에는 포커스를 유지(연속 입력 UX), 탐색 → 대화 전환 시에는 IME를 숨김.

5. **뒤로 가기 우선순위** (위에서부터 처리, 처리되면 중단):

   | 순위 | 상태 | 처리 주체 | 동작 |
   | :--- | :--- | :--- | :--- |
   | 1 | IME 표시 중 | 시스템 | 키보드 닫기 |
   | 2 | Dialog / DropdownMenu / ContextMenu / 이미지 뷰어 | 각 윈도우(`dismissOnBackPress`) | 닫기 |
   | 3 | 설정 시트 | 시트 윈도우 → `onDismissRequest` | 숨김 애니메이션 후 `overlay = None` |
   | 4 | 드로어 열림 | M3 1.3+ 내장 Predictive Back | 닫기 (버전 미충족 시 `BackHandler(enabled = drawerState.isOpen)`) |
   | 5 | `CHAT_ACTIVE` | `BackHandler(enabled = screenMode == CHAT_ACTIVE && !drawerState.isOpen && overlay == OverlayState.None)` | 탐색 모드로 (대화 유지, 4.5) |
   | 6 | 탐색 모드 + 대기 첨부 있음 | `BackHandler(enabled = screenMode == ScreenMode.CAMERA_EXPLORE && pending.isNotEmpty() && !drawerState.isOpen && overlay == OverlayState.None)` | 첨부 비우기 + [실행 취소] 스낵바 |
   | 7 | 그 외 | 시스템 | 기본 동작(홈으로 Predictive Back 애니메이션) |

   * **Compose `BackHandler` LIFO 주의**: `OnBackPressedDispatcher`는 **나중에(안쪽 컴포저블에서) 등록된 핸들러를 먼저 실행**합니다. `ModalNavigationDrawer`의 내부 핸들러는 자식 컨텐츠(`ChatPanel` 등)보다 먼저 등록되므로, 자식 `BackHandler`의 `enabled` 조건에 반드시 `!drawerState.isOpen && overlay == OverlayState.None`을 포함해야 드로어가 열려 있을 때 드로어가 먼저 닫힙니다.
   * **모든 `BackHandler`는 `enabled` 조건부**로만 등록합니다. 항상 활성인 핸들러는 Android 15+의 "홈으로 돌아가기" 예측 애니메이션을 막습니다.
   * Manifest에 `android:enableOnBackInvokedCallback="true"`를 명시합니다(API 33~35 대응, 36은 기본 활성).

6. **제스처 충돌 방지**: `ModalNavigationDrawer(gesturesEnabled = drawerState.isOpen)` — 열기는 햄버거 버튼으로만, 스와이프는 닫기에만 허용합니다. 왼쪽 가장자리 스와이프가 **시스템 뒤로 가기 제스처**, 프리뷰 핀치 줌/탭 포커스, 썸네일 `LazyRow` 가로 스크롤과 충돌하는 것을 막습니다.

---

### 4.7. 앱 시작 시 정합성 복구 (`StartupMaintenance`)

`PeekApplication`에서 **첫 프레임 이후** `@ApplicationScope` + `Dispatchers.IO`로 실행합니다(카메라 즉시 진입을 방해하지 않음).

1. `status == LOADING`인 메시지 → `status = ERROR`, `errorCode = "INTERRUPTED_BY_EXIT"` (현재 실행 중인 전송 Job이 없을 때만. 고정 문자열 대신 코드를 기록하여 다국어 렌더링 보장).
2. `pending_file_deletions` 처리(파일 삭제 → 행 제거).
3. `staging/` 정리: `SavedStateHandle`에서 복원된 대기 첨부에 포함되지 않고 24시간 이상 지난 파일 삭제.
4. `remote_file_cache` 만료 행 삭제, 네트워크 가능 시 `pending_remote_deletions` best-effort 처리.
5. **고아 파일 GC (`OrphanFileSweeper`, 최대 24시간에 1회)**: `original/`, `payload/`, `thumb/`을 스캔해 DB에서 참조되지 않고 **수정 시각이 1시간 이상 지난** 파일 삭제(커밋 진행 중 파일 보호용 유예).
6. (선택) 설정 화면에 이미지 저장 용량 표시 및 "오래된 대화 원본 정리" 기능 제공.

---

### 4.8. 다국어(i18n / l10n) 아키텍처 (`en-US` 기본값 + `ko` 한국어)

앱 UI에 노출되는 **모든 문구**는 영어(`en-US`, 기본값)와 한국어(`ko`) 2개 언어를 완벽 지원합니다.

1. **리소스 파일 분리 및 기본 로케일 규칙**:
   * `app/src/main/res/values/strings.xml`: **기본 로케일(`en-US`)**. 기기 언어가 한국어가 아닌 모든 환경에서의 폴백(Fallback) 및 기본 언어.
   * `app/src/main/res/values-ko/strings.xml`: **한국어(`ko`)** 번역 리소스. `values/strings.xml`과 **1:1로 동일한 키 집합**을 유지하며 누락된 키가 없도록 관리합니다.
   * `app/src/main/res/xml/locales_config.xml`:
     ```xml
     <?xml version="1.0" encoding="utf-8"?>
     <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
         <locale android:name="en-US"/>
         <locale android:name="ko"/>
     </locale-config>
     ```
     `AndroidManifest.xml`의 `<application android:localeConfig="@xml/locales_config" ...>`에 연결하여 Android 13+(API 33+) 시스템 설정의 **앱별 언어(Per-app language)** 메뉴와 자동 연동합니다.

2. **앱 내 언어 전환 (`AppLanguage` & `AppLocaleManager`)**:
   * 설정 시트(`SettingsBottomSheet`)에서 **앱 표시 언어(App Language)** 선택 옵션을 제공합니다:
     * `System Default` (`LocaleListCompat.getEmptyLocaleList()`)
     * `English (US)` (`LocaleListCompat.forLanguageTags("en-US")`)
     * `한국어` (`LocaleListCompat.forLanguageTags("ko")`)
   * `MainActivity`는 `AppCompatActivity`를 상속(테마는 `Theme.Material3.DayNight.NoActionBar` 계열 사용)하고 `AppCompatDelegate.setApplicationLocales(localeList)`로 API 29~36 전 버전에서 동일하게 즉시 언어를 전환합니다.

3. **하드코딩 문자열 배제 및 `UiText` / `ErrorUiMapper` 패턴**:
   * `@Composable` 내부에서는 반드시 `stringResource(R.string.xxx)` 또는 `pluralStringResource`만 사용합니다(버튼, 힌트, 다이얼로그 제목/본문, 드롭다운 항목, 접근성 `contentDescription` 포함).
   * `ViewModel` / `UseCase`에서 발생하는 단발성 안내(Snackbar, Toast, Preflight 강등 알림, 카메라 렌즈 폴백 안내 등)는 고정 `String`이 아닌 `UiText`로 전달합니다:
     ```kotlin
     sealed interface UiText {
         data class StringRes(@androidx.annotation.StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText
         data class Dynamic(val value: String) : UiText // 서버 원문 에러 등 동적 문자열에만 한정 사용
     }
     ```
   * **DB 저장 데이터의 언어 중립성**:
     * `MessageEntity.errorCode`(`AUTH`, `NOT_FOUND`, `PAYLOAD_TOO_LARGE`, `RATE_LIMITED`, `SERVER`, `BLOCKED`, `FILE_UNAVAILABLE`, `NETWORK`, `TIMEOUT`, `PARSE`, `INTERRUPTED_BY_EXIT`)를 기준으로 UI의 `ErrorUiMapper`가 현재 로케일에 맞는 `@StringRes` 문구를 렌더링합니다. `MessageEntity.errorMessage`에는 서버가 반환한 원문 디버그 상세(있을 경우)만 보조로 저장합니다.
     * 사진만 첨부하고 텍스트 없이 질문했을 때 생성되는 기본 대화방 제목도 빈 제목 플래그 또는 생성 시점 로케일 리소스(`R.string.default_photo_conversation_title`: `"Photo inquiry"` / `"사진 질문"`)를 사용합니다.
     * 기본 시스템 프롬프트(System Prompt)는 사용자가 직접 수정하지 않은 초기 상태일 때 현재 로케일에 맞는 기본 안내문(`R.string.default_system_prompt`)을 플레이스홀더/기본값으로 제공합니다.

---

## 5. 추후 워치 컴패니언 앱(`:wear`) 확장 고려사항

* `:app` 모듈의 `domain/model` 및 `data/remote` 계층을 UI와 분리해 두었기 때문에, 추후 Wear OS 앱 추가 시:
  1. 워치에서 음성/텍스트 질문 또는 워치/폰 카메라 원격 셔터 트리거(`MessageClient`).
  2. 폰(`:app`)의 `SendChatMessageUseCase`가 API 통신 및 이미지 처리를 대행하고 결과 요약 텍스트를 워치로 스트리밍/전달(`DataClient` / `MessageClient`)하는 구조로 손쉽게 확장할 수 있습니다.
* 원격 셔터는 폰 화면이 꺼져 있거나 앱이 백그라운드일 때 카메라를 열 수 없으므로(백그라운드 카메라 제한), 폰 앱이 포그라운드일 때만 동작하도록 제한하거나 별도 UX를 설계해야 합니다.

---

## 6. 구현 검증 체크리스트

| 영역 | 시나리오 | 기대 결과 |
| :--- | :--- | :--- |
| 카메라 | 촬영 버튼 직후 0.1초 내 전송 | 촬영 파일 정상 저장 후 카메라 비활성화, 크래시·빈 첨부 없음 |
| 카메라 | 탐색 ↔ 대화 모드 빠른 연타(10회) | 최종 상태와 카메라 on/off 일치, `CameraState` 오류 없음 |
| 카메라 | 전면 카메라 없는 에뮬레이터에서 FRONT 선택 | 후면 폴백 + 안내, 크래시 없음 |
| 카메라 | 권한 거부 / 영구 거부 | 갤러리 전용 모드 동작 |
| 카메라 | 탐색 모드에서 입력창 포커스(IME 상승) | 프리뷰 비율 유지, 입력바만 상승 |
| UI | 드로어 열린 상태에서 뒤로 가기 / 설정 시트 위 다이얼로그에서 뒤로 가기 | 4.6 우선순위대로 한 단계씩 닫힘 (`!drawerState.isOpen` 조건으로 드로어 우선 닫힘) |
| UI | 대화 목록 맨 위에서 아래로 계속 스크롤 | 대화 패널 닫히지 않음 |
| UI | 설정 시트에서 시스템 프롬프트 입력 중 회전/다크모드 전환 | 시트·입력값 유지 |
| 다국어 | 기기 언어 영어(`en-US`) / 기타 언어(예: 일본어) 상태에서 실행 | 모든 UI·다이얼로그·오류·접근성 문구가 기본값인 영어(`en-US`)로 누락 없이 표시 |
| 다국어 | 설정 시트 또는 시스템 설정에서 `English (US)` ↔ `한국어` 전환 | 이미 기록된 오류 메시지(`errorCode` 기반)를 포함해 모든 UI 문구가 즉시 선택한 언어로 전환 |
| 프로세스 | 대기 첨부 3장 상태에서 개발자 옵션 "활동 유지 안 함" | 복원 후 첨부 유지, staging 고아 없음 |
| 프로세스 | 응답 대기 중 앱 강제 종료 후 재실행 | 해당 메시지 `ERROR`(`INTERRUPTED_BY_EXIT`) + 현재 언어로 안내 및 재시도 가능 |
| Gemini | 프리셋 A(키1)로 업로드 후 프리셋 B(키2)로 같은 대화 계속 | 키2로 재업로드, 403/404 없음 |
| Gemini | 기기 시계를 +2일 / −2일로 변경 후 전송 | 서버 만료시각 기준 판정, 불필요/누락 업로드 없음 |
| Gemini | 업로드 중 비행기 모드 전환 | 재시작 업로드 또는 오류 + 재시도, DB 불일치 없음 |
| OpenAI 호환 | Base URL을 `https://host/v1/`, `https://host/v1/chat/completions`로 각각 입력 | 동일한 최종 엔드포인트로 정규화 |
| OpenAI 호환 | `http://192.168.x.x:4000` LiteLLM, nginx `client_max_body_size 1m` | 평문 경고, Preflight 강등 또는 명확한 413 안내 |
| 이미지 | 50MP 원본 + ORIGINAL 프리셋, 파노라마(긴 변 > 16383px) | OOM 없이 처리, 16383px 클램프 |
| 이미지 | 20장 첨부 대화에서 "현재 대화 전체 적용" | 즉시 응답, 전송 시 필요한 사진만 재생성 |
| 파일 | 대화 삭제 직후 강제 종료 → 재실행 | 삭제 큐 처리로 고아 파일 0개 |
| 파일 | 사용자 메시지 수정(이후 턴 절단) | 절단된 턴의 3종 파일 모두 삭제 |

---

## 7. 변경 이력

* **Rev.2.1 (2026-11-15)** — Rev.2 정밀 기술 검토 및 다국어(`en-US` 기본값 + `ko` 한국어) 아키텍처 반영
  * 다국어(i18n): `res/values/strings.xml`(`en-US` 기본) + `res/values-ko/strings.xml`(`ko` 한국어) + `res/xml/locales_config.xml` 구성, `AppCompatDelegate.setApplicationLocales` 기반 앱 내 언어 전환(`System Default` / `English (US)` / `한국어`), `UiText` 및 `errorCode` 기반 언어 중립 DB 오류 렌더링 설계(4.8).
  * 세부 엣지 케이스 보완: ① Compose `BackHandler`의 LIFO 특성상 `ChatPanel` 핸들러가 드로어보다 먼저 가로채지 않도록 `!drawerState.isOpen && overlay == OverlayState.None` 조건 명시(4.6.5), ② `GatedLifecycleOwner` 생성 시 초기 상태 `CREATED` 설정으로 미바인딩 dispose 시 `LifecycleRegistry` 예외 방지(4.1.2), ③ `minSdk 29` 호환을 위한 API 29(`WEBP`) vs API 30+(`WEBP_LOSSY`) 분기 명시(4.2.4), ④ `network_security_config.xml`에서 공식 Gemini/OpenAI 도메인은 HTTPS 강제 유지(4.4.4).
* **Rev.2 (2026-11-15)** — 설계 리뷰 반영
  * 카메라: `unbindAll()` 직접 토글 → Lifecycle Gate + 파생 상태 + `DisposableEffect` 정리, 촬영 중 전송 레이스 방어, 권한·렌즈 폴백·`CameraState` 오류 처리, IME 찌그러짐의 실제 원인(레이아웃) 정정.
  * UI: 대화 패널을 `ModalBottomSheet` → 같은 윈도우 전체 화면 `ChatPanel`로 변경, 단일 `OverlayState`, IME·뒤로 가기 우선순위 표, 드로어 스와이프 열기 비활성, `activeConversationId` 정책 명문화.
  * API: Gemini 파일 캐시를 키(fingerprint)·payload 리비전 단위 별도 테이블로 분리, 서버 `expirationTime`/`state` 기반 판정, single-flight, 파일 참조 실패 시 재업로드·폴백, 원격 파일 삭제 큐. Base64 스트리밍 바디, 413 Preflight·단계적 강등, Base URL 정규화·`@Url`, cleartext/사용자 CA, 응답 관용 파싱, 타임아웃·로그 마스킹. 텍스트 대치 기준을 `isLatestTurn` → `isInRecentImageTurns`로 수정.
  * 데이터: staging 디렉터리, 상대 경로 저장, 원자적 쓰기, 삭제 큐 + 고아 GC, 지연 payload 생성과 `payloadMaxSide`/`payloadRevision`, `ImageDecoder` 기반 OOM 방어, WebP 16383px 제한, 백업 제외, `isSelected` 제거, `position` 유니크 인덱스, 시작 시 `LOADING` 정리, 파괴적 마이그레이션 금지.
  * 보안: `security-crypto`(deprecated) → Tink AEAD + DataStore.
