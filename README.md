# ToDoNote

메모와 노트를 중심으로 쓰는 개인용 Android 앱입니다. 메모와 노트는 기기 안의 SQLite에 저장하고, 알림과 오늘의 소식만 Firebase Cloud Messaging으로 받습니다.

## 주요 기능

| 화면 | 내용 |
|---|---|
| 오늘 | 오늘 소식(IT, AI, 보안 뉴스)과 새로운 기술(연구소 발표, 논문, 새 모델) 두 탭. 날짜별 묶음, 썸네일 카드, 새 소식 강조, 누르면 원문 열기 |
| 보관함 | 소식 카드를 길게 누르거나 밀어서 저장. 내보내기에 포함 |
| 메모 | 짧은 메모 작성, 드래그 정렬, 스와이프 수정·삭제, 링크 자동 인식, 접이식 검색 |
| 노트 | 제목과 본문 작성, 3열 목록, 접이식 검색, 지문·암호 잠금 |
| 할 일 | 메뉴에서 열기. 남은 할 일과 완료한 일, 시간 알림, 홈 화면 위젯 |
| 알림 | FCM으로 받은 알림을 최대 30개 보관, 읽지 않은 개수 표시 |
| 휴지통 | 삭제한 메모, 노트, 알림의 복원과 영구 삭제 |

### 데이터 보호

- **편집 보호:** 저장하지 않은 내용은 임시 저장되어, 앱이 종료되어도 다시 열 때 복원할 수 있습니다.
- **수정 이력:** 노트마다 이전 버전을 최근 10개까지 보관합니다.
- **백업:** 다운로드 폴더로 내보내고 다시 가져올 수 있습니다. 가져오기 전에 파일을 검사하고, 교체 전 데이터를 다운로드 폴더에 저장합니다.
- **손상 대비:** DB 손상이 감지되면 파일이 삭제되기 전에 사본을 남깁니다.

## 오늘 화면 수집기

`server/today_feed/`는 무료 소스에서 소식을 모아 FCM으로 보내는 Python 스크립트입니다. API 키 없이 동작하고, 무료 Gemini API 키를 넣으면 한국어 번역과 AI 요약을 더합니다. 주기 실행은 직접 운영하는 서버의 cron에 맡깁니다.

```bash
cd server/today_feed
python3 collect.py --dry-run
```

`--dry-run`은 표준 라이브러리만 쓰고 아무것도 보내지 않습니다. 전송(`--send`)에는 `firebase-admin` 패키지, FCM 토큰, 서비스 계정 키가 필요합니다. **앱을 1.13 이상으로 먼저 업데이트한 뒤에 전송하세요.** 서버 설정, crontab, 소스 목록은 [수집기 문서](agent-docs/오늘화면수집기-v3.md)에 있습니다.

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

수집기 테스트는 네트워크 없이 실행됩니다.

```bash
cd server/today_feed && python3 -m unittest
```

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
server/today_feed/  오늘 화면 수집기
agent-docs/     기능 검토와 운영 문서
```

## 보안 참고

- 노트 본문은 DB에 평문으로 저장됩니다. 노트 잠금과 앱 잠금은 화면 접근만 막습니다.
- 내보낸 백업 파일도 평문 SQLite 파일입니다. 다운로드 폴더에 저장되므로 공유하거나 올리지 마세요.

## 저장소에 포함하지 않는 파일

`google-services.json`, 서명 키(`*.jks`, `*.keystore`), `local.properties`, 빌드 결과물(`*.apk`, `*.aab`), DB와 백업 파일(`*.db`), IDE 설정(`.idea/`)은 `.gitignore`로 제외합니다. `server/today_feed/`에서는 `*.py`, `*.sh`, `*.example`, `sources.json`만 저장소에 올라가며, 서비스 계정 키와 토큰은 그 밖의 위치에 두는 것을 권장합니다.
