#!/system/bin/sh
# Use the manufacturer's updater unchanged, with boot-time checks around it.

case "$(getprop ro.product.vendor.model)" in
    "Dumber mini"|"S9") ;;
    *) exit 0 ;;
esac
[ -c /dev/sitronixDev ] || exit 0

tool=/system/bin/st7123-vendor-tool
firmware=/system/etc/firmware/dumber-mini/7123.dump
workdir=/dev/st7123-update
firmware_sha=b9d86f9bbcd61619b7e9573fd7c8cb1225c0eae8dee9bb0d8aeadb0024558509
tool_sha=ad6bb0639605087ef0344770507a1677c0458303976f47ff175a35705428e370

check_hash() {
    actual="$(sha256sum "$1")" || return 1
    [ "${actual%% *}" = "$2" ]
}

if [ ! -x "$tool" ] || ! check_hash "$tool" "$tool_sha" || \
        ! check_hash "$firmware" "$firmware_sha"; then
    echo "Missing or unexpected ST7123 updater/firmware; refusing update" >&2
    exit 1
fi

# /data is not available at post-fs. A private /dev directory provides both
# scratch space and a lock against another instance of this wrapper.
umask 077
if ! mkdir "$workdir"; then
    echo "Cannot reserve ST7123 update directory; another update may be running" >&2
    exit 1
fi
trap 'rm -rf "$workdir"' EXIT

query_version() {
    # The vendor main() does not reliably propagate errors to its exit status.
    if ! "$tool" 0 /dev/sitronixDev 3 > "$workdir/query.log" 2>&1 || \
            grep -qiE 'fail|error|invalid|bootcode' "$workdir/query.log"; then
        cat "$workdir/query.log" >&2
        return 1
    fi
    version_hex="$(sed -n 's/^st_query_fw_version , version: 0x\([0-9A-Fa-f][0-9A-Fa-f]*\) , revision:.*$/\1/p' "$workdir/query.log")"
    case "$version_hex" in
        ""|*[!0-9A-Fa-f]*) return 1 ;;
    esac
    [ "${#version_hex}" -le 2 ] || return 1
    printf '%d\n' "0x$version_hex"
}

if ! installed="$(query_version)"; then
    echo "Cannot read installed ST7123 version; refusing update" >&2
    exit 1
fi
if [ "$installed" -ge 3 ]; then
    echo "ST7123 version $installed already installed; preserving current firmware"
    exit 0
fi
case "$installed" in
    1|2) ;;
    *) echo "Unknown ST7123 version $installed; refusing update" >&2; exit 1 ;;
esac

# The vendor utility opens its input with r+b, so it needs a writable copy.
if ! cp "$firmware" "$workdir/7123.dump" || \
        ! check_hash "$workdir/7123.dump" "$firmware_sha"; then
    echo "Failed to prepare ST7123 firmware copy" >&2
    exit 1
fi
echo "Applying manufacturer ST7123 firmware: version $installed -> 3"
if ! "$tool" 0 /dev/sitronixDev 1 "$workdir/7123.dump" > "$workdir/update.log" 2>&1; then
    cat "$workdir/update.log" >&2
    exit 1
fi
cat "$workdir/update.log"
if grep -qiE 'fail|error|invalid|set write size' "$workdir/update.log" || \
        ! grep -q '^upgrad success!' "$workdir/update.log" || \
        ! grep -qE '^upgrade result : 13[[:space:]]*$' "$workdir/update.log"; then
    echo "Vendor updater did not report a successful ST7123 upgrade" >&2
    exit 1
fi

# The driver resets the controller during an upgrade. Allow it to become ready.
attempt=0
while [ "$attempt" -lt 5 ]; do
    sleep 1
    if installed="$(query_version)" && [ "$installed" -eq 3 ]; then
        echo "Manufacturer ST7123 version 3 verified on controller"
        exit 0
    fi
    attempt=$((attempt + 1))
done
echo "ST7123 update could not be verified on the controller" >&2
exit 1
