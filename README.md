# M3Q Root for Galaxy S26 Ultra

[한국어](README.ko.md)

A manual, reboot-ephemeral KernelSU root launcher for one exact international Galaxy S26 Ultra firmware build.

> **Exact target only.** Do not install or run this build on another model, region, firmware, or kernel. A failed kernel attempt can panic or reboot the device.

## Supported target

| Field | Required value |
| --- | --- |
| Model | `SM-S948B` |
| Device / product | `m3q` / `m3qxeea` |
| Firmware | `S948BXXS4AZG5_OXM4AZG5` |
| Android build | `BP4A.251205.006` |
| Kernel | `6.12.30-android16-5-pd30ff70-abogkiS948BXXS4AZG5-4k` |
| App | `M3Q Root 0.5.6` (`versionCode 14`) |

The app checks the model, full build fingerprint, and kernel release before enabling a kernel action. Any mismatch is rejected.

> **Shizuku is strongly recommended and effectively required for the best result.** The fallback route remains available, but an already-running and authorized Shizuku server enables the fast tracefs KASLR route. In practice, prepare Shizuku first to greatly improve root speed and the chance of a successful run.

## App functions

- **Temporary root:** obtains RAM-only root and activates the bundled firmware-matched KernelSU late-load component.
- **Module reload:** reruns the active KernelSU modules' late-load, mount, and service stages without rebooting Android.
- **Soft boot:** restarts the Android app runtime (Zygote/System UI) so Zygisk and LSPosed can attach again.
- **Root management:** opens KernelSU Manager for per-app permission control.
- **Diagnostics:** shares a redacted local report. The app has no Internet permission and disables Android backup.

There is no automatic post-boot root. A real reboot removes the temporary root and KernelSU module.

## Install and use

1. Download the APK from [GitHub Releases](../../releases).
2. Compare its SHA-256 with the release notes.
3. Install the APK and KernelSU Manager (`me.weishu.kernelsu`).
4. Start Shizuku through ADB and approve this app once. Treat this as the standard setup for the best root speed and success rate.
5. Wait until kernel uptime reaches 180 seconds.
6. Open the app and tap **Temporary root** once. Do not retry an uncertain kernel run in the same boot.
7. If modules or LSPosed are inactive, run **Module reload**, then **Soft boot**.

## Root process

The app uses a fail-closed, per-boot flow:

1. exact model, fingerprint, and kernel gate;
2. 180-second boot-settle gate and one fresh kernel-write claim per boot;
3. Shizuku tracefs KASLR route when already authorized, otherwise the bundled exact-image physical-P0 oracle;
4. bounded carrier validation and kernel R/W setup;
5. UID-restricted bootstrap helper;
6. hash-verified KernelSU late-load handoff.

See [Root process](docs/ROOT_PROCESS.md) and [Technical reference](docs/REFERENCE.md) for the implementation boundaries.

## Build

Requirements: JDK 17, Android SDK 37, and Android NDK `29.0.14206865`.

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

APK output: `android/app/build/outputs/apk/release/app-release.apk`.

The published APK is development-signed so it can update the previously tested app. Verify the certificate and APK hash shown in each release.

## Repository layout

```text
android/                         Android app and build scripts
exploit/src/                     shared native components
exploit/src/targets/m3q-.../     exact AZG5 target
exploit/vendor/root-my-galaxy/   physical-P0 fallback and provenance
docs/                            concise runtime and technical documentation
```

Generated APKs, JNI outputs, device logs, screenshots, local paths, and research scratch files are intentionally excluded from Git history.

## Attribution and license

The one-shot Samsung 8EG5 route was cross-checked against [CyberMeowfia](https://github.com/polygraphene/CyberMeowfia). The physical-P0 fallback derives from [Root My Galaxy Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads). Shizuku integration uses [Shizuku API](https://github.com/RikkaApps/Shizuku-API).

See [LICENSE](LICENSE), [NOTICE](android/NOTICE), and the vendored [provenance](exploit/vendor/root-my-galaxy/UPSTREAM.md).
