# Galaxy S26 Ultra용 M3Q Root

[English](README.md)

정확히 한 가지 국내판 Galaxy S26 Ultra 펌웨어만 지원하는 수동 KernelSU 임시 루트 앱입니다. 재부팅하면 루트가 해제됩니다.

> **지원 대상이 정확히 일치할 때만 사용하세요.** 다른 모델, 지역, 펌웨어, 커널에서는 설치하거나 실행하지 마세요. 커널 작업 실패 시 기기가 재부팅되거나 커널 패닉이 발생할 수 있습니다.

## 지원 대상

| 항목 | 필수 값 |
| --- | --- |
| 모델 | `SM-S948N` |
| 기기 / 제품 | `m3q` / `m3qksx` |
| 펌웨어 | `S948NKSS4AZG3_OKR4AZG3` |
| Android 빌드 | `BP4A.251205.006` |
| 커널 | `6.12.30-android16-5-pd30ff70-abogkiS948NKSS4AZG3-4k` |
| 앱 | `M3Q Root 0.5.6` (`versionCode 14`) |

앱은 모델, 전체 빌드 fingerprint, 커널 release 문자열을 모두 확인합니다. 하나라도 다르면 커널 작업을 허용하지 않습니다.

> **Shizuku를 강력히 권장하며, 실제 사용에서는 사실상 필수 준비 단계로 보세요.** fallback 경로도 남아 있지만, 미리 실행하고 권한을 승인한 Shizuku가 있으면 빠른 tracefs KASLR 경로를 사용합니다. 루트 성공 가능성과 실행 속도를 크게 높이므로 임시 루트보다 먼저 준비하세요.

## 앱 기능

- **임시 루트:** RAM에서만 유지되는 루트를 획득하고 펌웨어에 맞는 KernelSU late-load 구성요소를 활성화합니다.
- **모듈 재로드:** Android를 재부팅하지 않고 활성 KernelSU 모듈의 late-load, mount, service 단계를 다시 실행합니다.
- **소프트 부팅:** Zygisk와 LSPosed가 다시 주입되도록 Android 앱 환경(Zygote/System UI)을 재시작합니다.
- **루트 권한 관리:** KernelSU Manager에서 앱별 권한을 관리합니다.
- **진단 보고서:** 개인정보성 실행값을 마스킹한 로컬 보고서를 공유합니다. 앱에는 인터넷 권한이 없고 Android 백업도 비활성화되어 있습니다.

재부팅 후 자동 루트 기능은 없습니다. 실제 재부팅을 하면 임시 루트와 KernelSU 모듈이 사라집니다.

## 설치 및 사용

1. [GitHub Releases](../../releases)에서 APK를 받습니다.
2. Release 설명의 SHA-256과 파일 해시를 비교합니다.
3. APK와 KernelSU Manager(`me.weishu.kernelsu`)를 설치합니다.
4. ADB로 Shizuku를 시작하고 이 앱의 권한을 한 번 승인합니다. 가장 빠르고 성공 가능성이 높은 표준 준비 단계입니다.
5. 커널 uptime이 180초를 넘을 때까지 기다립니다.
6. 앱에서 **임시 루트 활성화**를 한 번 실행합니다. 결과가 불확실하면 같은 부팅에서 재시도하지 마세요.
7. 모듈이나 LSPosed가 비활성 상태라면 **모듈 재로드**, **소프트 부팅** 순서로 실행합니다.

## 루트 과정

앱은 다음 순서로 실패 안전하게 동작합니다.

1. 정확한 모델, fingerprint, 커널 검사
2. 부팅 후 180초 대기와 부팅당 한 번의 fresh kernel-write claim
3. 승인된 Shizuku tracefs KASLR 경로 또는 포함된 exact-image physical-P0 oracle
4. 제한된 carrier 검증과 kernel R/W 준비
5. 앱 UID만 허용하는 bootstrap helper
6. 해시를 검증한 KernelSU late-load 전달

구체적인 경계는 [루팅 프로세스](docs/ROOT_PROCESS.ko.md)와 [기술 참고서](docs/REFERENCE.ko.md)를 참고하세요.

## 빌드

JDK 17, Android SDK 37, Android NDK `29.0.14206865`가 필요합니다.

Linux x86_64:

```sh
cd android
./build-native.sh
./test-safety-policy.sh
./gradlew --no-daemon :app:lintRelease :app:assembleRelease
```

Windows PowerShell:

```powershell
cd android
.\build-native.ps1
.\test-safety-policy.ps1
.\gradlew.bat --no-daemon :app:lintRelease :app:assembleRelease
```

APK는 `android/app/build/outputs/apk/release/app-release.apk`에 생성됩니다.

공개 APK는 기존 실기기 검증 앱에 업데이트 설치할 수 있도록 개발용 인증서로 서명합니다. 각 Release의 인증서와 APK 해시를 확인하세요.

## 저장소 구성

```text
android/                         Android 앱과 빌드 스크립트
exploit/src/                     공용 네이티브 구성요소
exploit/src/targets/m3q-.../     정확한 AZG3 target
exploit/vendor/root-my-galaxy/   physical-P0 fallback과 출처
docs/                            간결한 실행 과정과 기술 문서
```

생성된 APK/JNI 파일, 기기 로그, 스크린샷, 로컬 경로, 조사용 중간 자료는 Git 이력에 포함하지 않습니다.

## 출처와 라이선스

Samsung 8EG5 one-shot 경로는 [CyberMeowfia](https://github.com/polygraphene/CyberMeowfia)의 성공 사례와 교차 검증했습니다. Physical-P0 fallback은 [Root My Galaxy Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads)에서 파생됐고, Shizuku 연동에는 [Shizuku API](https://github.com/RikkaApps/Shizuku-API)를 사용합니다.

[LICENSE](LICENSE), [NOTICE](android/NOTICE), vendored [출처 기록](exploit/vendor/root-my-galaxy/UPSTREAM.md)을 참고하세요.
