# Main OC++ prereleases

Main-source/build-input pushes to lowercase `ocpp-dev33` automatically attempt a
main-only Java 8 `jar reobfJar` build on a GitHub-hosted Ubuntu runner. There is
also **Actions > Main OC++ prerelease > Run workflow**, restricted to that branch.
No browser automation, personal token, new secret or persistent credential is
required. Repository Actions policy must allow these pinned official actions.

The build job has read-only contents permission. Only the separate publication
job has contents-write permission, through GitHub's ephemeral job token. It does
not compile or execute the mod. Checkout credentials are not persisted. Actions
are pinned to full commit IDs. The Gradle wrapper JAR and distribution checksum
are checked; changing them requires an intentional workflow update.

After structural and source-audio/notice checks, only the main JAR and checksum
are uploaded to Releases. Each run uses `ocpp-ci-RUN_ID`, always a prerelease,
never latest. A draft is created first; publication requires matching server
asset digests. An existing tag/release or uncertain API result stops the run,
preserving it for inspection rather than overwriting or creating a duplicate.
Partial upload failures may leave an unpublished draft. Inspect the run and
draft before any retry. No existing release is used as a test target.

The pipeline does not run aggregate historical-fixture/companion tests, launch
Minecraft/audio devices or QEMU, or package OCVMIntegration. The dependency's
separate no-bundled-Gson requirement remains unchanged; main OC++ may use Gson.
Passing this pipeline is compilation/structural evidence, not runtime testing.
Repository dependency endpoints and a fresh full build still need an actual CI
run to qualify them. Failed builds publish nothing.

The retired reading recording, registration and obsolete source records are
rejected. Existing audio and required notices must match source bytes. CC0 tray,
CC BY motor/seek and codec/network component notices remain separate from MIT.
Codec sources and licensing inputs accompany the matching Git source tag.

The workflow file uses JSON syntax, a valid YAML subset, so its syntax and
security/trigger invariants can be checked with Python's standard library.
The workflow/docs-only initial commit does not match its push paths and does
not launch a build. Future main-source/build-input pushes do. Manual runs are
available after installation on the default branch. No end-to-end publication
was inferred from the local fixture/static checks.
