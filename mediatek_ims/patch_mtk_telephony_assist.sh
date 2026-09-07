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

python3 - "$smali_dir/com/mediatek/telephony/DataStateController.smali" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()

old = '''.method private getDataSim()I
    .registers 2

    .line 298
    invoke-static {}, Lcom/android/internal/telephony/SubscriptionController;->getInstance()Lcom/android/internal/telephony/SubscriptionController;

    move-result-object p0

    invoke-virtual {p0}, Lcom/android/internal/telephony/SubscriptionController;->getDefaultDataSubId()I

    move-result p0

    .line 299
    invoke-static {}, Lcom/android/internal/telephony/SubscriptionController;->getInstance()Lcom/android/internal/telephony/SubscriptionController;

    move-result-object v0

    invoke-virtual {v0, p0}, Lcom/android/internal/telephony/SubscriptionController;->getSlotIndex(I)I

    move-result p0

    return p0
.end method
'''

new = '''.method private getDataSim()I
    .registers 1

    .line 298
    invoke-static {}, Landroid/telephony/SubscriptionManager;->getDefaultDataSubscriptionId()I

    move-result p0

    .line 299
    invoke-static {p0}, Landroid/telephony/SubscriptionManager;->getSlotIndex(I)I

    move-result p0

    return p0
.end method
'''

if new in text:
    pass
elif old not in text:
    raise SystemExit(f"default-data compatibility patch target not found in {path}")
else:
    text = text.replace(old, new, 1)

path.write_text(text)
PY

java -jar prebuilts/extract-tools/common/smali/smali.jar assemble \
    "$smali_dir" \
    -o "$classes_dir/classes.dex"

cp "$input_apk" "$stage_apk"
zip -q -j "$stage_apk" "$classes_dir/classes.dex"
prebuilts/build-tools/linux-x86/bin/zipalign -f -p 4 "$stage_apk" "$output_apk"
