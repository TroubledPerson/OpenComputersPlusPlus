# OpenOS factory CD source checkpoint

This feature is for ordinary OpenComputers Lua machines. It is unrelated to Tier 7
SuperBIOS, QEMU and PC ISO images. No existing media, world or installation was
modified. Offline fixtures and packaged-resource checks accompany dev.16; live boot is unrun.

New item: OpenOS CD-R, opencomputers:plusplus metadata 28 (append-only). Existing
CD-R 23, CD-RW 24 and soundcards 25–27 retain their metadata. It is a separate
Creative/testing item with the existing plain R artwork, and a source-template
craft: pristine blank CD-R plus the authentic OpenOS loot floppy, output one
master, original floppy returned unchanged. Written/data-bearing discs and an
arbitrary ordinary floppy are rejected. The real-stack recipe fixture passed; JEI and game crafting remain unrun. No recipe rewrites existing disks.

First mount in a virtual CD drive allocates the ordinary optical UUID. Only that
fresh empty medium is seeded from the bundled OpenOS manifest. Default payload is
180 files / 387,499 bytes, 13 directories; with default 512-byte entry cost it
uses 486,827 logical bytes, under the 32 MiB CD quota. Custom capacities/entry
limits remain enforced. File hashes and lengths are checked before writing and
again during copying; the manifest, file size and entry count are bounded.

After successful copy the CD-R's existing durable seal makes it read-only. The
item records the manifest hash only after successful finalization. Interrupted
writable or unverifiable media stays unmounted; its contents are retained and
never automatically replaced, reseeded or upgraded. The sealed, exact-payload
marker recovery below is the only recovery exception.
Retain the complete world optical storage and item data together in backups.

Dev.25 preserves the verified completion marker when component saving replaces
the item's data tag. For previously affected OpenOS CD-Rs with an existing optical
identity and missing marker, mount may restore only that metadata after checking
the existing durable seal and every file, directory, length and SHA-256 against
the bundled OpenOS master. No disc file is rewritten, deleted or reseeded. A
writable/interrupted disc, changed payload, extra file or malformed marker is
rejected with its contents retained. Masters from a different bundled payload
without a completion marker require separate review; they are not auto-upgraded.

Ordinary OC BIOS can discover /init.lua on the network-visible optical filesystem.
OpenOS's existing installer remains the installer: choose the intended writable
OC target explicitly and follow its confirmation. This source checkpoint has not
booted Lua/Minecraft or installed OpenOS. Do not experiment on an existing world
until a matching tested release and isolated owner-run test are available.

Existing CD-R/CD-RW semantics, explicit finalize callback, read/seek sounds,
handle revocation and duplicate-mount guards remain unchanged. Blank CD-RW can
still receive files through its normal virtual filesystem; it is not auto-seeded.
