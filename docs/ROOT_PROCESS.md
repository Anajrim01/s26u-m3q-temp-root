# Root process

This document describes the runtime path used by the Android app. It is not a porting guide for other firmware.

## 1. Exact-device gate

`M3qRootEngine` requires all of the following to match:

- `Build.MODEL == SM-S948N`
- the exact AZG3 Android fingerprint
- the exact AZG3 kernel release string

The app disables every kernel action on a mismatch.

## 2. Per-boot safety gate

Before a fresh kernel write, the app reads `/proc/sys/kernel/random/boot_id` and atomically claims that boot. It also requires at least 180 seconds of kernel uptime. A failed, timed-out, or termination-unconfirmed run keeps the claim, so the next attempt requires a real kernel reboot.

Already-active bootstrap root is handled separately: the app can finish KernelSU activation without rerunning the exploit.

## 3. KASLR route selection

The shortest trusted route is selected:

1. If an already-authorized Shizuku server is running as UID 2000 or 0, the payload uses shell tracefs access to derive the KASLR slide.
2. Otherwise, the bundled Root My Galaxy oracle checks the exact 32-row Image fingerprint and accepts only one internally consistent physical-P0 result.

No user-entered kernel address or remote target catalog is accepted.

## 4. Kernel write and bounded R/W

The native payload uses the CVE-2026-43499 dangling PI-waiter condition, reclaims the required order-3 workspace, and reconstructs the waiter through `pselect()` fd sets. Two isolated sacrificial regions must produce fast, bounded walk verdicts before carrier writes begin.

The AZG3 target then converts two owned `/dev/zero` file objects into bounded address and data carriers. It verifies unique FD ownership, performs a scratch round trip, and restores the temporary uinput list/minor mutation. Ambiguous ownership or restoration keeps the workspace alive and fails closed.

## 5. UID-scoped root bridge

The payload does not directly grant lasting root to the Android app process. It validates `system_unbound_wq`, stages a controlled usermode-helper work item, and starts the bundled helper as UID 0. The temporary Unix socket accepts only the launching app UID through `SO_PEERCRED`.

## 6. KernelSU late-load

The root helper:

1. copies the bundled `ksud` to temporary paths;
2. verifies the exact SHA-256;
3. uses a private mount namespace for the DEFEX-compatible loader path;
4. requests KernelSU late-load for KMI `android16-6.12`;
5. verifies KernelSU control version and flags;
6. closes the bootstrap socket and exits.

KernelSU Manager then owns per-app authorization.

## 7. Module recovery

- **Module reload** reruns the active KernelSU modules' late-load, mount, and service stages. It does not restart the kernel or Android app runtime.
- **Soft boot** asks the bootstrap root bridge to restart Zygote so Zygisk and LSPosed can attach to the new app runtime. Running apps and System UI restart; a device-specific fallback may become a full reboot and remove temporary root.

## 8. Terminal states

- **KernelSU active:** the app reports success and Manager can grant access.
- **Bootstrap active:** the exploit is not rerun; only late-load completion is offered.
- **Unsupported identity:** no kernel action is available.
- **Attempt already claimed:** reboot is required.
- **Termination unconfirmed:** the app forbids follow-up probes and same-boot retries.

Reboot removes the in-memory root state and late-loaded module.
