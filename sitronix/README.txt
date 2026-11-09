Dumber mini / S9 manufacturer touchscreen firmware

7123.dump is the unmodified manufacturer fix supplied in mini_ghost_touch_fix.
Size: 69632 bytes (64 KiB code + 4 KiB configuration).
SHA256: b9d86f9bbcd61619b7e9573fd7c8cb1225c0eae8dee9bb0d8aeadb0024558509
Configuration version: 3; threshold: 150; caps/windows: 70,75,80.
Code CRC24: 6EBC67; configuration CRC24: D58737.

ST_7123_Tool_ARM is the unmodified static ARM executable from the manufacturer
package. Android.bp installs it as /system/bin/st7123-vendor-tool on ARM builds.
SHA256: ad6bb0639605087ef0344770507a1677c0458303976f47ff175a35705428e370
ELF checking and stripping are disabled to preserve the supplied bytes.
This 32-bit tool requires ARM32 execution support, as on the Dumber mini.

rw-system.sh invokes dumber-mini-touch-update.sh during post-fs only on vendor
model Dumber mini or S9 and when /dev/sitronixDev exists as a character device.
The wrapper repeats these guards for direct invocation. It runs in the existing
rw-system.sh SELinux domain; no device-node permissions are relaxed.

The wrapper verifies the firmware and tool hashes, then queries live firmware
version using vendor action3. Versions1/2 are upgraded; version3 or higher is
preserved, including custom versions4/5. Version0 or failed/malformed queries
are rejected. A private /dev directory provides an invocation lock and writable
firmware copy (required because the vendor tool opens its input using r+b).
Action1 performs the update through the same driver interface as the original
ZZZ01_WriteFlash.bat. No updater code or low-level transfer logic is rewritten.

The vendor program does not reliably propagate failures to its exit status.
The wrapper also checks its output for errors and positive success markers,
then queries the controller to verify version3. This catches reported failures
and version mismatches; it cannot repair the utility's internal error handling
or guarantee a physical-flash readback. Driver READ_DUMP exposes a staging
buffer and is not used as proof that the controller was updated.

No persistent completion marker is used. Checking live version avoids rewriting
updated controllers and avoids trusting a marker after a failed update/reset.
Errors are logged under PHH and do not abort the remainder of rw-system.sh.
A failed update can be attempted next boot. The wrapper does not reboot the
phone or impose a timeout that kills an in-progress firmware write.
The lock covers this wrapper only; do not run another flashing tool concurrently.

Host tests (mocked updater; no device access):
  python3 tests/test_st7123_boot.py
  python3 tests/test_st7123_update.py

Hardware flashing has not been performed as part of this source integration.
