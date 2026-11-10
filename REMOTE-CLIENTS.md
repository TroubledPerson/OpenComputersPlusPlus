# Private OC++ network development milestone

This development source adds creative-access Network Card T2 and T3. No real
network profiles or host grants are provided. Both new cards retain the virtual
`modem` and a separately addressed `internet` component. The latter inherits the
existing HTTP/HTTPS and raw TCP API and adds `remoteStatus()` and
`remoteRequest(profile, operation, relativePath[, bytes])`. Supported new client
operations are list, read and write. There are no listeners, shares, servers,
mount commands or arbitrary URL/credential parameters in this API.

Remote requests return a handle with `ready()`, `finish()` and `close()`. Polling
does not block. `finish()` returns bytes for reads, a byte count for writes, a
filename array for SMB listings, or bounded MLSD text for FTP listings. It
releases the request. Failed requests return a generic error without credentials.
Requests do not resume after saving/loading a computer. Each card allows two
pending requests; the server has two workers and an eight-request queue. Transfer
payloads are limited to 1 MiB. SMB listings are limited to 1,024 entries/64 KiB of
names. Connection/read timeouts are ten seconds. These are conservative initial
budgets, not measured throughput claims.

## Disabled configuration/test surface

`plusplus.remoteClients.enabled` and `allowPlainFtp` default to false; `profiles`
defaults to an empty array. The existing `internet.enableTcp` and address filters
also apply. Possessing a card grants no profile or endpoint permission. Profiles
are held only in the server's configuration, not in item or world NBT. Treat that
configuration as a private credential file; its existing OS permissions apply.
Never paste actual secrets into chat, logs, item names, Lua programs or URLs.

The following is a schema example, not an active profile or instruction to
connect this installation. Enabling a real profile requires the host admin to
approve its endpoint, credentials and exact computer address separately.

```hocon
remoteClients {
  enabled: false
  allowPlainFtp: false
  profiles: [{
    id: "EXAMPLE_ONLY"
    computers: ["EXACT_AUTHORIZED_COMPUTER_ADDRESS"]
    protocol: "ftps" # ftps, ftp or smb
    host: "example.invalid"
    port: 21
    username: "SYNTHETIC"
    password: "NO_REAL_SECRET"
    domain: "" # SMB only
    share: "" # SMB only; one share, not a UNC path
    basePath: "" # relative path inside the remote account/share
    writable: false
    requireEncryption: true # SMB3 encryption; SMB2 needs explicit false
  }]
}
```

SMB uses SMB2/3 only, requires signing, disables DFS, and defaults to encryption.
Its socket factory pins connections to addresses checked against the current
Internet Card policy. FTP is passive-only, ignores PASV-advertised hosts and
denies privileged passive data ports. Plaintext FTP requires its separate opt-in.
FTPS uses the JVM's normal trust context, hostname checking, and encrypted data
channels; there is no trust-all or TLS-error fallback. No private-IP exception is
added: access to a LAN server requires an explicit existing-filter admin choice.

Stopping/removing the computer or card revokes its pending handles. Profile
removal/change also revokes work on subsequent checks. Sockets close immediately;
graceful protocol cleanup runs on a worker. The OS account and remote account
permissions continue to apply. No elevation, firewall changes, service setup or
system configuration changes are performed.

## Private runtime libraries

Install only the dev.5 mod JAR in a separately authorized test instance. All
seven original runtime archives are embedded inside it. When clients are used,
it creates a verified private cache at `<mod-JAR-folder>/.ocpp-runtime/sha256/`.
The existing OS account must be able to write there; no elevation or permission
changes occur. Existing valid files are reused; bad/partial files are preserved
and rejected. Missing archives are never downloaded. The explicit
`ocpp.libraryDirectory` override remains a developer-only test option, unnecessary
for normal installation.

A fixed child loader uses SHA-256 pins and isolates classes, Java 8 package
metadata and private service resources. Minecraft's older Commons IO/Net and
other libraries stay separate. Original archives are not flattened, relocated,
re-signed or stripped. Their notices/signatures and service files stay intact.
The source bundle retains originals, metadata and notices. No installation into
the owner's current Minecraft instance occurred.

Dependencies: SMBJ 0.14.0, Commons Net 3.13.0, Commons IO 2.21.0, Bouncy Castle
bcprov-jdk18on 1.86, SLF4J API 2.0.9, MBassador 1.3.0, ASN-One 0.6.0.
SMBJ/ASN-One/Commons use Apache-2.0; MBassador/SLF4J/Bouncy Castle use their
included MIT notices. Optional JUEL expression evaluation is not enabled or
bundled. Dependency initialization is tested without connecting.

Maven HTTPS downloads matched repository SHA-1 sidecars; local SHA-256 pins are
recorded for reproducibility. The Bouncy Castle JAR's cryptographic signature
verified, but JDK8 jarsigner could not build its certificate chain to a trusted
root. This is not a verified-publisher claim. No trust store or security setting
was changed. Before wider use, review dependency advisories and actual runtime
compatibility; the archived upstream does not provide future security maintenance.

## Host-filesystem options: unfinished backend, access always off

Network Card T3 reports these choices: Off (default), Minecraft directory
read-only, System read-only and System read/write. Configuration stores
`plusplus.hostFilesystem.mode`; `hostFilesystemStatus()` always reports
`active=false` and an unfinished backend. Changing the mode cannot expose files.

`HostAccessPolicy` is a pure, offline-tested authorization model. A future real
provider must bind server/world/computer identity and player UUIDs, enforce OS
permissions and authorization on every operation, close/revoke handles, and use
race-safe containment against traversal, symlinks, Windows junctions, aliases,
alternate streams and device namespaces. A path string check alone is not such
a provider. No path is currently passed to Java's host filesystem from Lua.

The Minecraft scope means the running server/instance directory. On a dedicated
server all modes concern that server's host; a joining client's machine is never
exposed. Integrated singleplayer concerns its local host. System read-only can
reveal passwords, tokens, private keys and personal files; system read/write can
also alter saves, mods and startup files. Neither is implicitly authorized.
Actual activation needs separate explicit host-admin confirmation of mode,
server/account, local-volume scope, computer/world, player UUIDs and duration.
Whether system means the OS volume or all accessible local regular-file volumes
still needs to be settled before activation. No elevation or permission bypass
will be implemented.

## Remaining verification

Offline tests are not live FTP/SMB/TLS interoperability tests. No real endpoint,
credential, host file, Minecraft world, Harbor setup or game launch is used.
Graphics performance/rendering, actual inventories/case wiring, protocol
interoperability, real-world save migration and host-filesystem containment still
need separately authorized isolated integration tests. Host backend is unfinished;
this milestone is not completion of the full agreed project.
