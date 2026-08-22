# 기술 참고서

## 고정 identity

| 항목 | 값 |
| --- | --- |
| Application ID | `dev.indevelopment.m3qroot.hardened` |
| 버전 | `0.5.6` (`versionCode 14`) |
| 모델 | `SM-S948B` |
| Android build ID | `BP4A.251205.006` |
| 펌웨어 | `S948BXXS4AZG5_OXM4AZG5` |
| 커널 | `6.12.30-android16-5-pd30ff70-abogkiS948BXXS4AZG5-4k` |
| Kernel Image SHA-256 | `ab3c3b3a69a459548fafbe0677f90a95c8ab3625f55f2b17feb7957cad758855` |
| 포함된 ksud SHA-256 | `3ce5753203c93f4d733fbc10eebd7a69152189afb1d2a15bfd855bd6b5d4f622` |

## 빌드 그래프

`android/build-native.ps1`이 ARM64 ELF 세 개를 만듭니다.

| 빌드 결과 | APK library 이름 | 역할 |
| --- | --- | --- |
| `su_daemon_aarch64_pie.app` | `libm3qroot.so` | root helper와 bootstrap daemon |
| `slide_oracle.app.so` | `libm3qoracle.so` | 정확한 physical-P0 KASLR fallback |
| `preload.app.so` | `libm3qpayload.so` | AZG5 kernel payload |
| `android/prebuilt/ksud-m3q-S948BXXS4AZG5-kdp` | `libm3qksud.so` | 정확한 KernelSU late-load helper |

Gradle `prepareM3qPayloads` task가 패키징 전에 이 파일들을 `src/main/jniLibs/arm64-v8a`로 복사합니다. 생성된 JNI 파일과 APK는 Git에서 제외합니다.

## 소스 위치

| 경로 | 역할 |
| --- | --- |
| `android/app/src/main/java/.../M3qRootEngine.java` | identity gate, one-shot 정책, native process 제어, KernelSU handoff |
| `android/app/src/main/java/.../MainActivity.java` | 수동 UI와 상태 dashboard |
| `android/app/src/main/java/.../RootSafetyPolicy.java` | 부팅 후 180초 안전 대기 |
| `exploit/src/targets/m3q-BP4A.251205.006/` | 정확한 AZG5 payload 로직과 offsets |
| `exploit/src/base/samsung-bp4a/` | 공용 Samsung BP4A 커널 레이아웃 헤더 |
| `exploit/src/` | 공용 native helper, stage-3 support, daemon 코드 |
| `exploit/vendor/root-my-galaxy/` | 정확한 Image fingerprint와 physical-P0 oracle |

Target 폴더 이름은 fingerprint의 Android build ID를 따릅니다. 다른 휴대폰 모델을 지원한다는 뜻이 아닙니다.

## 런타임 불변 조건

- Native 실행 전에 모든 기기 identity 값이 일치해야 합니다.
- Fresh write는 kernel boot ID당 한 번만 허용합니다.
- Kernel uptime은 최소 180초여야 합니다.
- Shizuku는 선택 사항이며 이미 승인된 UID 2000 또는 0만 허용합니다.
- KASLR 판정은 유일하고 내부적으로 일치해야 합니다.
- Carrier 변경 전에 분리된 preflight region 두 개가 FAST walk를 통과해야 합니다.
- Root 작업 전 carrier FD 소유권과 scratch R/W를 증명해야 합니다.
- Kernel 변경이 애매하거나 process 종료를 확인하지 못하면 재시도가 아니라 재부팅이 필요합니다.
- 포함된 `ksud`가 코드에 고정된 SHA-256과 일치해야 합니다.

## 빌드 및 Release 검사

`android/`에서 실행합니다.

```powershell
.\build-native.ps1
.\test-safety-policy.ps1
.\gradlew.bat --no-daemon :app:lintRelease :app:assembleRelease
```

APK 게시 전 확인 사항:

1. APK에 ARM64 library 네 개가 모두 포함됐는지 확인합니다.
2. Package/version과 signing certificate를 확인합니다.
3. SHA-256을 계산해 GitHub Release 설명에 기록합니다.
4. APK와 생성된 JNI 폴더는 commit하지 않습니다.
5. 개발용 서명인지 production 서명인지 분명히 밝힙니다.

## 이식 경계

다른 펌웨어 지원에는 fingerprint, Kernel Image hash, KASLR 근거, 구조체 offsets, allocator geometry, carrier layout, workqueue layout, KernelSU module, 모든 포함 파일 hash의 재산출이 필요합니다. 새 target을 추가하더라도 AZG5 fail-closed gate를 약화하면 안 됩니다.

## Upstream 참고 자료

- Root My Galaxy Payloads: <https://github.com/BuSung-dev/Root-My-Galaxy-Payloads>
- Shizuku API: <https://github.com/RikkaApps/Shizuku-API>
- Shizuku: <https://github.com/RikkaApps/Shizuku>

Vendor source의 출처와 라이선스는 `exploit/vendor/root-my-galaxy/UPSTREAM.md`와 `LICENSE`에 기록돼 있습니다.
