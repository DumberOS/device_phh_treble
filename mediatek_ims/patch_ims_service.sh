#!/bin/bash
set -euo pipefail

if [ "$#" -ne 1 ] && [ "$#" -ne 2 ]; then
    echo "usage: $0 <input-apk> [output-apk]" >&2
    exit 1
fi

input_apk=$1
output_apk=${2:-$1}
if [[ "$output_apk" != /* ]]; then
    output_apk="$PWD/$output_apk"
fi

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

classes_dir="$tmpdir/classes"
smali_dir="$tmpdir/smali"
stage_apk="$tmpdir/stage.apk"

mkdir -p "$classes_dir"
unzip -p "$input_apk" classes.dex > "$classes_dir/classes.dex"

java -jar prebuilts/extract-tools/common/smali/baksmali.jar disassemble \
    "$classes_dir/classes.dex" \
    -o "$smali_dir"

python3 - "$smali_dir/com/mediatek/ims/internal/ImsVTProviderUtil\$NetworkAvailableCallback.smali" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()

available_old = '''    invoke-static {}, Lcom/mediatek/ims/internal/ImsVTProviderUtil;->isVideoCallOnByPlatform()Z

    move-result v1

    if-eqz v1, :cond_6e

    .line 420
    const/4 v1, 0x1
'''

available_new = '''    invoke-static {}, Lcom/mediatek/ims/internal/ImsVTProviderUtil;->isVideoCallOnByPlatform()Z

    move-result v1

    if-eqz v1, :cond_6e

    const-string v1, "media.VTS"

    invoke-static {v1}, Landroid/os/ServiceManager;->checkService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v1

    if-eqz v1, :cond_6e

    .line 420
    const/4 v1, 0x1
'''

lost_old = '''    invoke-static {}, Lcom/mediatek/ims/internal/ImsVTProviderUtil;->isVideoCallOnByPlatform()Z

    move-result v1

    if-eqz v1, :cond_39

    .line 444
    const/4 v1, 0x0
'''

lost_new = '''    invoke-static {}, Lcom/mediatek/ims/internal/ImsVTProviderUtil;->isVideoCallOnByPlatform()Z

    move-result v1

    if-eqz v1, :cond_39

    const-string v1, "media.VTS"

    invoke-static {v1}, Landroid/os/ServiceManager;->checkService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v1

    if-eqz v1, :cond_39

    .line 444
    const/4 v1, 0x0
'''

for old, new, name in (
    (available_old, available_new, "VT onAvailable guard"),
    (lost_old, lost_new, "VT onLost guard"),
):
    if new in text:
        continue
    if old not in text:
        raise SystemExit(f"{name} patch target not found in {path}")
    text = text.replace(old, new, 1)

path.write_text(text)
PY

java -jar prebuilts/extract-tools/common/smali/smali.jar assemble \
    "$smali_dir" \
    -o "$classes_dir/classes.dex"

cp "$input_apk" "$stage_apk"
zip -q -j "$stage_apk" "$classes_dir/classes.dex"
prebuilts/build-tools/linux-x86/bin/zipalign -f -p 4 "$stage_apk" "$output_apk"
