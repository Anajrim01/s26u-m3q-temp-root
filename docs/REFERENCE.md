# Technical reference

## Fixed identity

| Item | Value |
| --- | --- |
| Application ID | `dev.indevelopment.m3qroot.hardened` |
| Version | `0.5.6` (`versionCode 14`) |
| Model | `SM-S948N` |
| Android build ID | `BP4A.251205.006` |
| Firmware | `S948NKSS4AZG3_OKR4AZG3` |
| Kernel | `6.12.30-android16-5-pd30ff70-abogkiS948NKSS4AZG3-4k` |
| Kernel Image SHA-256 | `d89be418252f9bd37a7f6540a3bda5ae23c683a0320855b23ad2fdecada5f7df` |
| Bundled ksud SHA-256 | `3ce5753203c93f4d733fbc10eebd7a69152189afb1d2a15bfd855bd6b5d4f622` |

## Build graph

`android/build-native.ps1` produces three ARM64 ELF files:

| Build output | APK library name | Purpose |
| --- | --- | --- |
| `su_daemon_aarch64_pie.app` | `libm3qroot.so` | root helper and bootstrap daemon |
| `slide_oracle.app.so` | `libm3qoracle.so` | exact physical-P0 KASLR fallback |
| `preload.app.so` | `libm3qpayload.so` | AZG3 kernel payload |
| `android/prebuilt/ksud-m3q-S948NKSS4AZG3-kdp` | `libm3qksud.so` | exact KernelSU late-load helper |

Gradle's `prepareM3qPayloads` task copies these files into `src/main/jniLibs/arm64-v8a` before packaging. Generated JNI files and APKs are ignored by Git.

## Source map

| Path | Responsibility |
| --- | --- |
| `android/app/src/main/java/.../M3qRootEngine.java` | identity gate, one-shot policy, native process control, KernelSU handoff |
| `android/app/src/main/java/.../MainActivity.java` | manual UI and status dashboard |
| `android/app/src/main/java/.../RootSafetyPolicy.java` | 180-second boot-settle gate |
| `exploit/src/targets/m3q-BP4A.251205.006/` | exact AZG3 payload logic and offsets |
| `exploit/src/base/samsung-bp4a/` | shared Samsung BP4A kernel layout headers |
| `exploit/src/` | shared native helpers, stage-3 support, daemon code |
| `exploit/vendor/root-my-galaxy/` | exact Image fingerprint and physical-P0 oracle |

The target directory name follows the Android build ID in the fingerprint. It does not mean that this repository supports another phone model.

## Runtime invariants

- All device identity fields must match before native execution.
- A fresh write is allowed once per kernel boot ID.
- Kernel uptime must be at least 180 seconds.
- Shizuku is optional and accepted only with an already-approved UID of 2000 or 0.
- The KASLR verdict must be unique and internally consistent.
- Two isolated preflight regions must return fast walk verdicts before carrier mutation.
- Carrier FD ownership and scratch R/W must be proven before root work.
- Any ambiguous kernel mutation or unconfirmed process termination requires reboot, not retry.
- The bundled `ksud` must match its compiled SHA-256.

## Build and release checks

Run from `android/`:

```powershell
.\build-native.ps1
.\test-safety-policy.ps1
.\gradlew.bat --no-daemon :app:lintRelease :app:assembleRelease
```

Before publishing an APK:

1. confirm the APK contains all four ARM64 libraries;
2. inspect its package/version and signing certificate;
3. calculate SHA-256 and include it in the GitHub Release notes;
4. do not commit the APK or generated JNI directory;
5. state clearly whether the APK is development-signed or production-signed.

## Porting boundary

Supporting another firmware requires re-deriving the fingerprint, kernel Image hash, KASLR evidence, structure offsets, allocator geometry, carrier layout, workqueue layout, KernelSU module, and every embedded hash. Adding a new target must not weaken the AZG3 fail-closed gate.

## Upstream references

- Root My Galaxy Payloads: <https://github.com/BuSung-dev/Root-My-Galaxy-Payloads>
- Shizuku API: <https://github.com/RikkaApps/Shizuku-API>
- Shizuku: <https://github.com/RikkaApps/Shizuku>

Vendored source provenance and licensing are recorded in `exploit/vendor/root-my-galaxy/UPSTREAM.md` and `LICENSE`.
