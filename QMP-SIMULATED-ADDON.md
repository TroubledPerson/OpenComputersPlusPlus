# Simulated QMP addon dev3

This optional development addon provides an explicitly constructed, memory-only QMP fixture controller. Production API1/API2 registration stays offline and mayExecuteGuest remains false. It contains no executable runner, real IPC, guest boot, disk attachment, native framebuffer or screen rendering. SuperBIOS and normal OpenOS CDs remain separate.

Memory transport has 32 chunks per direction, 16 KiB chunks and 256 KiB combined lifetime bytes; close releases queues without resetting lifetime counters. A transport binds once. QMP transcript framing is CRLF with 16 KiB per-line UTF-8 byte bounds, 256 KiB lifetime, depth 8, 256 nodes and duplicate/unknown-field rejection. Only a documented subset of greetings, returns/errors and STOP/RESUME/SHUTDOWN events is supported. Client IDs are canonical UUIDs. This intentionally strict subset is not a general/native QMP compatibility claim.

The controller requires an API2 server-authorized session and its dispatcher. It caps 16 pending operations, 128 lifetime correlations and 16 retained results. Async results are addon-owned and explicitly simulated; API2's existing synchronous Reply remains offline. Greeter/capability and request deadlines are 2 s; idle expires after 30 s. START maps to a fixture cont request, with RUNNING requiring a separate RESUME event. Acknowledgements alone never prove running or stopped. Graceful system_powerdown waits before a bounded quit fallback; clean stop requires a separate fixture exit(0), while uncertainty/crash quarantines the in-memory model without deleting data. There is no native force-kill operation.

SyntheticIoBridge delegates raw BGRA and finite guest-directed input to existing API2 budgets, only in simulated RUNNING state, with session/layout/focus validation and release-all cleanup. No pixels or input reach Minecraft or a host/guest OS. API1/API2 ABI, main dev33, old addon dev2, audio, EEPROM and world/disc files are preserved. Only one addon with modid ocpp_vmintegration may be installed; never co-load dev2 and dev3.

Protocol sources consulted as documentation only:
https://www.qemu.org/docs/master/interop/qmp-spec.html
https://www.qemu.org/docs/master/interop/qemu-qmp-ref.html

Any native backend needs separate approval and exact platform/build/firmware/source/licence inventory, owned runtime/library root, disk-image leases and acquisition/execution budget. Windows/Linux/macOS and host/guest architecture declarations do not certify runtime support. RAID remains one aggregate virtual disk; no disks are opened here.
