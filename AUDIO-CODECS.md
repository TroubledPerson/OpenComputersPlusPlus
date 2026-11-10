# Codec source, licenses and rebuilding

JLayer 1.0.1 is the original JavaZoom artifact from Maven Central. Its source
headers allow GNU Library GPL2 or later; the standard LGPL2.1 text is supplied.
The intact JAR SHA256 is850508c837454a1b06017c32a36876fae516de1e89a829f725fee1e6dcc52000.
The matching sources JAR and POM, source headers and acquisition record are in
audio-libs. Maven SHA1 was checked and SHA256 recorded over HTTPS. This is not a
claim of an independently verified publisher signature.

FLAC uses unmodified Project Nayuki source revision
c9b8727bab49f6da15f47c21bb67ef6de0306cad, LGPL3-or-later. The exact archive SHA256
is a6e3e31bdd5eaa663e11752ecf088f199c3ab0c5bc945939c49f897ef43ec597.
The official successor e807ab25 introduced Java14 switch expressions. That
archive/source is retained as a rejected Java8 candidate; the selected parent
keeps preceding changes without rewriting upstream source. Full original source
archives, selected common/decode/encode sources, GPL3 and LGPL3 texts accompany
the matching mod source ZIP. The reviewed jFLAC alternative was excluded because
its own project license notice describes unresolved licensing history.

The mod's MIT license does not replace the codecs' licenses. Their notices are
prominent in META-INF/ocpp-audio/CODEC-NOTICES.txt. Complete library source and
application source/build scripts accompany the binary in the same deliverable
folder. Personal modification and reverse engineering to debug modifications
are permitted. No private signing key, permission bypass or online activation
is needed to rebuild and use an interface-compatible modified library.

Build/relink with the supplied full JDK8/Gradle workflow: compileFlacCodec and
flacCodecJar compile the selected source as Java8; generateAudioRuntime computes
the actual hashes and bundles the original JLayer JAR and newly compiled FLAC
library. The main mod is then compiled/reobfuscated normally. For a modification,
preserve originals and edit a working copy of the corresponding library source,
retain its license/copyright headers, mark modified files/date as required, and
recompile. The build accepts trusted ocppJlayerJar and ocppFlacSource properties
for interface-compatible working copies. The resulting private cache manifest
is regenerated; hashes protect integrity rather than forbidding relinking.
Include complete modified library/application sources and build inputs when
redistributing a changed combined work. Do not copy stale signature entries
into a newly compiled derivative JAR; preserve the untouched upstream originals.

The private loader exposes no class names, host paths or URLs through Lua. It
uses fixed embedded names and a hash-verified mod-local audio-sha256 cache, without
altering the global Minecraft/Forge classpath. Only the adapter/codec namespaces
and resources load privately. The decoder calls byte-array/frame APIs only,
never a library player, file picker, physical MIDI/optical device or network API.
WAV PCM8/16 is decoded by the bounded adapter without JavaSound SPI discovery.
FLAC accepts8/16-bit mono/stereo; MP3 accepts frames within the configured rate,
channel, encoded-size and decoded-duration limits. This is not universal support
for every WAV/FLAC profile. See SOUNDCARD-API.md and final tests for exact limits.
