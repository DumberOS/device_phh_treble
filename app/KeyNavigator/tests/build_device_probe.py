#!/usr/bin/env python3
"""Build a separate opt-in instrumentation APK; never included in the adapter."""
import importlib.util
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("adapter_build", ROOT / "build.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)
run, tools, android = build.run, build.TOOLS, build.ANDROID
out = build.BUILD / "probe"
(out / "classes").mkdir(parents=True, exist_ok=True)
(out / "dex").mkdir(exist_ok=True)
run(tools / "aapt2", "link", "-o", out / "resources.apk", "-I", android,
    "--manifest", ROOT / "tests/device/AndroidManifest.xml")
run("javac", "--release", "8", "-classpath", android, "-d", out / "classes",
    *sorted((ROOT / "tests/device").glob("*.java")),
    *(ROOT / "src/com/dumbermini/keynavigator" / name for name in
      ("Keypad.java", "ReadingScreen.java", "WiseKeypad.java", "WisePinScreen.java")))
run("jar", "cf", out / "classes.jar", "-C", out / "classes", ".")
run(tools / "d8", "--lib", android, "--min-api", "26", "--output", out / "dex", out / "classes.jar")
with zipfile.ZipFile(out / "resources.apk") as source, zipfile.ZipFile(out / "unsigned.apk", "w") as dest:
    for item in source.infolist():
        dest.writestr(item, source.read(item.filename))
    dest.write(out / "dex/classes.dex", "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
run(tools / "zipalign", "-f", "4", out / "unsigned.apk", out / "aligned.apk")
run(tools / "apksigner", "sign", "--ks", build.BUILD / "development.p12", "--ks-pass", "pass:android",
    "--out", out / "DeviceProbe.apk", out / "aligned.apk")
print(out / "DeviceProbe.apk")
