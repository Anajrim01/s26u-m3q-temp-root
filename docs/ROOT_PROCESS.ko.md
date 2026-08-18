# 루팅 프로세스

Android 앱이 실제로 사용하는 런타임 흐름입니다. 다른 펌웨어로 이식하기 위한 숫자 복사 문서가 아닙니다.

## 1. 정확한 기기 검사

`M3qRootEngine`은 다음 값을 모두 확인합니다.

- `Build.MODEL == SM-S948N`
- 정확한 AZG3 Android fingerprint
- 정확한 AZG3 커널 release 문자열

하나라도 다르면 앱의 모든 커널 작업을 막습니다.

## 2. 부팅 단위 안전 검사

Fresh kernel write 전에 `/proc/sys/kernel/random/boot_id`를 읽어 현재 부팅을 원자적으로 claim하고, 커널 uptime 180초 이상을 요구합니다. 실패, timeout, 종료 확인 불가가 발생해도 claim을 유지하므로 다음 시도에는 실제 커널 재부팅이 필요합니다.

Bootstrap root가 이미 살아 있으면 exploit을 다시 실행하지 않고 KernelSU 활성화만 마무리합니다.

## 3. KASLR 경로 선택

신뢰할 수 있는 가장 짧은 경로를 선택합니다.

1. 이미 승인된 Shizuku server가 UID 2000 또는 0으로 실행 중이면 shell tracefs 권한으로 KASLR slide를 계산합니다.
2. 그렇지 않으면 포함된 Root My Galaxy oracle이 정확한 32행 Image fingerprint를 검사하고 내부적으로 일치하는 physical-P0 결과가 하나일 때만 채택합니다.

사용자가 커널 주소를 입력하거나 원격 target catalog를 내려받는 방식은 사용하지 않습니다.

Physical-P0 경로는 fallback입니다. 실제 사용에서는 Shizuku를 먼저 실행하고 권한을 승인하세요. Tracefs 경로가 훨씬 빠르고 상대적으로 불안정한 fallback 탐색을 피하므로 루트 성공 가능성과 실행 속도를 크게 높입니다.

## 4. Kernel write와 제한된 R/W

Native payload는 CVE-2026-43499 dangling PI waiter 조건을 만들고 필요한 order-3 workspace를 재할당한 뒤 `pselect()` fd-set으로 waiter를 재구성합니다. Carrier write 전에 서로 분리된 희생 region 두 개가 빠르고 제한 시간 안의 walk 판정을 모두 통과해야 합니다.

AZG3 target은 소유한 `/dev/zero` file object 두 개를 address/data carrier로 전환합니다. 정확히 하나의 FD가 각 carrier를 소유하는지 확인하고 scratch round trip을 거친 뒤 임시 uinput list/minor 변경을 복원합니다. 소유권이나 복원 결과가 애매하면 workspace를 유지하고 실패로 닫습니다.

## 5. UID 제한 root bridge

Android 앱 프로세스에 직접 지속 루트를 주지 않습니다. `system_unbound_wq` 상태를 검증하고 제어된 usermode-helper work를 넣어 포함된 helper를 UID 0으로 시작합니다. 임시 Unix socket은 `SO_PEERCRED`로 실행 앱 UID만 허용합니다.

## 6. KernelSU late-load

Root helper는 다음을 수행합니다.

1. 포함된 `ksud`를 임시 경로에 복사합니다.
2. 정확한 SHA-256을 검증합니다.
3. DEFEX 호환 loader 경로를 위해 private mount namespace를 사용합니다.
4. KMI `android16-6.12`로 KernelSU late-load를 요청합니다.
5. KernelSU control version과 flags를 검증합니다.
6. Bootstrap socket을 닫고 종료합니다.

이후 앱별 권한은 KernelSU Manager가 관리합니다.

## 7. 모듈 복구

- **모듈 재로드**는 활성 KernelSU 모듈의 late-load, mount, service 단계를 다시 실행합니다. 커널이나 Android 앱 환경은 재시작하지 않습니다.
- **소프트 부팅**은 Zygisk와 LSPosed가 새 Android 앱 환경에 주입되도록 bootstrap root bridge를 통해 Zygote 재시작을 요청합니다. 실행 중인 앱과 System UI가 재시작되며, 기기에 따라 전체 재부팅으로 전환되어 임시 루트가 해제될 수 있습니다.

## 8. 종료 상태

- **KernelSU 활성:** 앱이 성공을 표시하고 Manager에서 권한을 부여할 수 있습니다.
- **Bootstrap 활성:** exploit을 다시 실행하지 않고 late-load 마무리만 제공합니다.
- **지원하지 않는 identity:** kernel action을 제공하지 않습니다.
- **이미 claim한 부팅:** 재부팅이 필요합니다.
- **종료 확인 불가:** 후속 probe와 같은 부팅 재시도를 모두 막습니다.

재부팅하면 메모리의 root 상태와 late-load module이 사라집니다.
