# OpenComputers++

An experimental Minecraft 1.12.2 fork of [MightyPirates/OpenComputers](https://github.com/MightyPirates/OpenComputers), based on release `1.12.2-forge/1.8.10` at commit `258943363ea8fc4ef14948e6bc6f67375061580c`.

## AI disclosure

This is a vibe-coded mod, developed with AI assistance. If you do not support AI-assisted development, it may be best to ignore this mod.

## Status

This is a development source snapshot, not a stable release. The main source version is `1.8.10-ocpp-dev.33`. It extends OpenComputers with additional hardware tiers, optical media/audio, and an experimental Tier 7 SuperBIOS interface. See [dev.33 notes](docs/DEV33-NOTES.md) for the current main-mod changes.

The optional offline and simulated VM companion sources are included. A separately developed embedded-QEMU prototype and its native runtime are **not included or cleared for redistribution**. No usable guest VM implementation is promised by this snapshot.

Minecraft 1.12.2, Forge 14.23.5.2860, a 64-bit Java 8 JDK, and the declared Gradle/Scala dependencies are required. The main mod replaces OpenComputers and keeps the `opencomputers` mod ID. Do not install it alongside upstream OpenComputers, another fork, or another OC++ main JAR. Test only in a separate disposable instance/world first.

## Publication limitations

The integrated Pixabay CD effects (`cd_open.ogg`, `cd_close.ogg`, and `cd_read.ogg`) are registered resources used by the mod. They remain subject to the [Pixabay Content License and Terms](https://pixabay.com/service/terms/), separately from the code's MIT licence and other assets' licences. Pixabay's [FAQ](https://pixabay.com/service/faq/) gives incorporation into an app or game as an example of a greater creative work; its Terms prohibit standalone content distribution. Preserve the [effect notices and provenance](src/main/resources/META-INF/ocpp-audio/CD-EFFECT-NOTICES.txt). Do not redistribute these sounds as a standalone collection or claim that MIT or CC0 covers them. These sources do not specifically rule on GitHub raw-file access, and this notice is not a legal guarantee.

Private build caches, logs, screenshots, local test evidence, machine-specific build drivers, and historical binary/source release fixtures are excluded. The source still contains regression tests that require those historical fixtures. See [source test prerequisites](SOURCE-TEST-PREREQUISITES.md). Their historical test results do not establish that a fresh public checkout passes the full suite.

No fresh game session, native VM, audio device, or full clean build has been run as part of publication preparation. Upstream service-publishing tasks are disabled and the upstream CurseForge/Modrinth project IDs have been cleared. No automated release/upload workflow is included.

## Build inputs and licensing

The Gradle wrapper and pinned dependency inputs are included. Use Java 8 and the repository's Gradle wrapper to configure/build the source. Network/codec JARs remain intact; their license and notice files accompany them. The missing historical fixtures and a fresh clean build still need qualification before claiming a reproducible full test/release build.

The main OpenComputers code retains its MIT license and original notices. That does not relicense all bundled material. Preserve [LICENSE](LICENSE), the other `LICENSE-*` files, resource licenses, and third-party notices. The original authors' public names and credits are intentionally retained.

- Network libraries: [third-party notices](network-libs/THIRD-PARTY-NOTICES.txt).
- JLayer and Nayuki FLAC codecs: LGPL terms, source archives, license texts, and [rebuild/relink instructions](AUDIO-CODECS.md).
- Fonts, original assets, and sounds: their resource-specific licenses and credits.
- Adapted CC BY 4.0 CD sounds: attribution and modifications in `src/main/resources/META-INF/ocpp-audio/`.
- QEMU/native runtime: no binaries distributed here; their separate licenses and corresponding-source obligations are unresolved for the private prototype.

This is an independent fork, not an official MightyPirates release. [Original upstream README](docs/UPSTREAM-README.md).