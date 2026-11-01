# ToDoNote

메모와 노트를 중심으로 쓰는 개인용 Android 앱입니다. 서버 없이 기기 안의 SQLite에 데이터를 저장하고, 알림만 Firebase Cloud Messaging으로 받습니다.

## 주요 기능

| 화면 | 내용 |
|---|---|
| 메모 | 짧은 메모 작성, 드래그 정렬, 스와이프 수정·삭제, 링크 자동 인식, 접이식 검색 |
| 노트 | 제목과 본문 작성, 3열 목록, 접이식 검색, 지문·암호 잠금 |
| ToDo | 오늘 날짜, 오늘의 정보(현재는 화면 틀만 구현), 남은 할 일, 시간 알림, 홈 화면 위젯 |
| 알림 | FCM으로 받은 알림을 최대 30개 보관, 읽지 않은 개수 표시 |
| 휴지통 | 삭제한 메모, 노트, 알림의 복원과 영구 삭제 |

### 데이터 보호

- **편집 보호:** 저장하지 않은 내용은 임시 저장되어, 앱이 종료되어도 다시 열 때 복원할 수 있습니다.
- **수정 이력:** 노트마다 이전 버전을 최근 10개까지 보관합니다.
- **백업:** 다운로드 폴더로 내보내고 다시 가져올 수 있습니다. 가져오기 전에 파일을 검사하고, 교체 전 데이터를 다운로드 폴더에 저장합니다.
- **손상 대비:** DB 손상이 감지되면 파일이 삭제되기 전에 사본을 남깁니다.

## 기술 구성

- Java 17, Android SDK (minSdk 34, targetSdk 36)
- SQLite (`SQLiteOpenHelper`)
- WorkManager, AndroidX Biometric
- Firebase Cloud Messaging, Firebase Analytics

## 빌드

Android Studio 또는 JDK 17과 Android SDK가 필요합니다.

1. Firebase 프로젝트에 패키지 이름 `kr.akotis.todonote`로 Android 앱을 등록합니다.
2. 내려받은 `google-services.json`을 `app/` 폴더에 넣습니다. 이 파일은 저장소에 포함되어 있지 않으며, 없으면 빌드되지 않습니다.
3. 빌드합니다.

```bash
./gradlew assembleDebug
```

release 빌드에는 직접 만든 서명 키가 필요합니다. 서명 키와 서명 설정은 저장소에 없습니다.

## 테스트

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

계측 테스트는 연결된 기기나 에뮬레이터가 필요합니다. 테스트가 끝나면 Gradle이 앱을 제거할 수 있으므로, 실제로 사용하는 기기에서는 실행하지 마세요.

## 폴더 구조

```text
app/src/main/java/kr/akotis/todonote/
├── activity/   메인 화면
├── adapter/    목록 어댑터
├── database/   SQLite 접근과 DB 손상 대비
├── fragment/   탭과 각 화면
├── model/      데이터 모델
├── service/    FCM 수신
├── util/       검색, 잠금 해제 등 공용 코드
├── widget/     홈 화면 위젯
└── worker/     할 일 알림 예약
agent-docs/     기능 검토 문서
```

## 보안 참고

- 노트 본문은 DB에 평문으로 저장됩니다. 노트 잠금과 앱 잠금은 화면 접근만 막습니다.
- 내보낸 백업 파일도 평문 SQLite 파일입니다. 다운로드 폴더에 저장되므로 공유하거나 올리지 마세요.

## 저장소에 포함하지 않는 파일

`google-services.json`, 서명 키(`*.jks`, `*.keystore`), `local.properties`, 빌드 결과물(`*.apk`, `*.aab`), DB와 백업 파일(`*.db`), IDE 설정(`.idea/`)은 `.gitignore`로 제외합니다.
