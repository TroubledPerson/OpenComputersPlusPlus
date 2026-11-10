# Sound cards - bounded APIs and testing access

Builtin computer.beep remains independent and unchanged. Cards append at plusplus
metadata25/26/27 for T1/T2/T3, occupying normal card-tier0/1/2 slots. All are in
Creative and have default survival recipes yielding one. The top-right speaker
symbol uses the native green card template, preserving bracket, contacts and
shading. The precise Library sketch pixels remain unavailable locally; this is
an original composition following the approved textual layout, not a pixel match.

T1: tone(wave,hz,seconds[,volume]) queues square, triangle, saw or noise with8-bit
sample precision (converted to signed16 for the game audio queue).20-8000Hz,
0.02-2seconds per tone, volume0-1; default16 queued requests and10seconds total.
One client voice is available. Example: component.sound_card.tone('square',440,0.25).

T2 inherits T1 and adds note(instrument,note[,volume]), notes0-24. Instruments:
harp,bass,basedrum,snare,hat,guitar,flute,bell,chime,xylophone,pling. These are
vanilla one-shot samples with natural decay. MIDI files use SMF0/1, PPQ1-960,
at most8 tracks,32KiB,2048 events,512 note-ons and8 note-ons per tick. Tempo and
running status are handled. Programs map to documented note-block families;
out-of-range pitches fold by octaves into MIDI54-78, percussion to drum/hat
families. Note-offs/sustain do not turn this into a General MIDI synthesizer.
SMPTE timing, format2, external MIDI devices and full GM fidelity are unsupported.

T3 inherits T1/T2 and adds WAV/MP3/FLAC requests from virtual OC files. Default
encoded limit1MiB and decoded duration30seconds; configurable ceilings8MiB/60s.
WAV is standard RIFF PCM8/16, FLAC8/16, mono/stereo,8-48kHz. Unsupported profiles,
changing stream formats, oversized metadata, malformed/truncated frames and
decoded expansion beyond bounds reject. Only one client decode worker and one
client voice are used; this is not a new Lua thread/parallelism claim. Four8KiB
PCM blocks and four game OpenAL buffers bound the PCM queue. Encoded bytes and
per-frame codec work have additional fixed bounds. Runtime CPU cost is unprofiled.

OpenOS soundplay <virtual-file.mid|wav|mp3|flac> [card-address] reads through the
ordinary virtual filesystem, closes the file and uploads8KiB chunks. Newly
installed OpenOS includes this utility; existing OS disks are not overwritten.
The lower API is begin(format,length,sourceAddress[,volume]), write(bytes),
finish(), stop(), status(), getTier(). Select explicit proxies with multiple cards.
begin requires a filesystem node visible to the calling computer on the same
network. The card itself opens no filesystem/host path. Eject, source visibility
revocation, computer stop/start and component removal cancel the bound request.
Source identity must be declared by the uploader; soundplay supplies the actual
mounted filesystem. No payload or queued playback is persisted in item/world NBT.

The server defaults to four active card requests, sends at most8KiB per card per
tick, and keeps upload/decode duration/queue limits. Client requests from a second
card may be ignored while its single voice is busy. Block-category volume and
existing OC sound volume/radius apply. Requests are explicit; inserting media
does not autoplay. Server status is idle/uploading/requested, not proof a remote
client heard audio. Decoder/device failures go to client logs. Matching client
and server builds are required for the appended packet type.
Playing a virtual file transmits its encoded bytes to nearby listening clients;
choose audio intended for that audience. This is not a private file-transfer API.

stop revokes future PCM and scheduled notes; an already-triggered vanilla note
finishes its natural decay. Pause, seek, full GM/soundfonts and external device
selection are not provided. Optional CD tray/reading effects remain unbundled:
consumer-local transfer/inspection of the supplied references is still blocked.
No real endpoints, private host files, world launch or audio-device test occurred.

Test metadata: /give @p opencomputers:plusplus 1 25 (26/27 for higher cards).
Use a matching case/card slot; leave ordinary builtin beep available to compare.
For virtual media playback use a mounted filesystem (including the new CD drive)
and select its actual address. Do not use live worlds/Harbor for first testing.
The final feature matrix/evidence distinguishes executed synthetic/offline tests
from unrun Minecraft/OpenAL, native boot, save, networking and performance checks.
