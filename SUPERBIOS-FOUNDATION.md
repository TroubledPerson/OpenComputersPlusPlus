Dev.17 adds connected-screen in-world text output and controls; see TIER7-PREVIEW.md.
The API1 metadata/provider foundation below remains unchanged. The case is only
power/inventory, and all manager controls are on the screen. No QEMU runtime.

# SuperBIOS / optional QEMU companion foundation

Dev.16 offline foundation; not a functional VM integration.
SuperBIOS is the Tier 7 management UI, distinct from guest BIOS/UEFI. The optional
Forge companion is OCVMIntegration-offline-dev.1.jar, alongside the
matching main mod in the chosen instance/server mods directory. No real instance
has been selected or modified. Main Minecraft/Forge/Java requirements are unchanged.
Do not install this addon with older main-mod versions: its declared dependency
requires OC++ dev.16 or later, and the provider also checks API version 1.

SuperBios.register accepts one explicit qemu-tcg provider. Missing and offline are
the only current states. Registration performs no filesystem discovery, executable
probe, native load or host connection. A dedicated server can own the provider;
clients receive only its bounded status property and do not require QEMU. All
launch/stop/import/display operations are absent. A matching addon is optional for
ordinary tiers and the preview's finite simulated failure.

Metadata API:

- VmManifest.validate accepts strict bounded JSON: 64 KiB, depth 16, unique exact
  fields, canonical UUIDs, integer numeric types and restricted relative paths.
  Duplicate keys, unknown fields, traversal, Windows device names and external
  dependency chains fail closed. It never opens declared paths.
- OfflineVmManager stages at most 16 volatile records for one world/case/owner.
  It rejects other identities and duplicate package, aggregate-volume or NVRAM
  IDs. Revocation closes that capability. This is an in-memory planner, not a
  persistent catalogue or import implementation.
- MockVmTransport accepts only status and validate-manifest, checks world/case/
  owner, rejects replay/nonces after 64 requests, bounds payloads and supports
  revocation. It is synchronous memory-only, not a network/QMP endpoint.
- AggregateLease keeps one world/RAID/volume identity, exclusive in-memory claim
  and pending-detach state. Removal while running retains the claim; detachment
  requires observed OFF. No method deletes data. Durable ownership/leases and
  actual block/item integration remain unavailable.

Schema 1 requires these exact fields:

    schema, backend, packageId, worldId, caseId, raidId, volumeId, ownerId,
    architecture, machine, machineVersion, cpuModel, firmware, firmwareSha256,
    firmwareLicense, nvramId, hostOS, hostArchitecture, diskFormat, diskPath,
    capacityPolicy, capacityBytes, memberIds, memberBytes, externalDependencies,
    vcpus, ramMiB

backend is qemu-tcg. Declared host OS is windows/linux/macos; host architecture is
x86_64/arm64. Declared guest architectures are x86_64/aarch64/riscv64, with an exact
machine/version, CPU identifier and firmware ID/hash/licence. These tags do not
certify support: every plan is non-executable. Disk formats raw/qcow2/vdi are only
metadata choices, not parsers. The external-dependency array must be empty; later
import must prove independent, shutdown disk closure and cannot assume .vbox,
snapshots or saved states are portable. IDs and firmware placeholders in synthetic
tests do not qualify real firmware. Planning bounds are 1–4 vCPUs and 16–512 MiB
RAM per record, maximum three 1 TiB member declarations; these reserve no host
resources and authorize no execution/concurrency.

One RAID aggregate has exactly three distinct member UUIDs. capacityBytes must
equal the sum of memberBytes. capacityPolicy is unapproved or literal-bytes;
neither can launch yet. No silent MiB-to-GiB conversion. Future managed layout
uses ocpp-vm/runtime and ocpp-vm/library relative to an explicitly chosen instance/
server root. These constants do not create folders or allow arbitrary host paths.

Next qualified runtime slice needs explicit instance/server root, trusted pinned
OS/CPU-specific QEMU and firmware builds, exact source/licence inventory, bounded
owned guest/disk scope, safe storage/backup lifecycle and execution approval.
Windows/Linux/CachyOS/macOS and host/guest CPU combinations require separate real
tests. TCG is the portable baseline; KVM/WHPX/HVF remain separate optional adapters.
No networking, host shares, raw disks, USB, clipboard or public monitor/display
endpoints are enabled here. Server clients must not control arbitrary QMP/CLI.

QEMU is not bundled or downloaded. Its overall GPLv2 and component-specific terms,
firmware and library licences require an exact inventory plus corresponding source,
patches and build/install scripts for a future redistributed binary pack. Keeping
it a replaceable external CLI/QMP process is a design boundary, not a blanket legal
exemption. Existing VM software/data rights must also be respected.
