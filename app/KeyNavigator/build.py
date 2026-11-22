#!/usr/bin/env python3
"""Offline SDK build. Uses only Java 17, Android SDK 34 and build-tools 34.0.0."""
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk"))))
TOOLS = SDK / "build-tools/34.0.0"
ANDROID = SDK / "platforms/android-34/android.jar"


def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


def main():
    for required in (ANDROID, TOOLS / "aapt2", TOOLS / "d8", TOOLS / "apksigner"):
        if not required.is_file():
            raise SystemExit(f"Missing {required}; set ANDROID_SDK_ROOT to an existing SDK.")
    for directory in ("classes", "generated", "dex", "tests"):
        (BUILD / directory).mkdir(parents=True, exist_ok=True)
    # No third-party test library or Android runtime needed for matching/routing tests.
    package = ROOT / "src/com/dumbermini/keynavigator"
    run("javac", "--release", "8", "-d", BUILD / "tests", package / "Keypad.java",
        package / "KeyPresses.java", package / "ReadingScreen.java", package / "SpatialNavigation.java", package / "AppPolicy.java",
        package / "ScrollNavigation.java", ROOT / "tests/AdapterTest.java", ROOT / "tests/SpatialNavigationTest.java",
        ROOT / "tests/AppPolicyTest.java", ROOT / "tests/ScrollNavigationTest.java",
        package / "ServiceList.java", ROOT / "tests/ServiceListTest.java",
        package / "WiseKeypad.java", ROOT / "tests/WiseKeypadTest.java")
    run("java", "-cp", BUILD / "tests", "com.dumbermini.keynavigator.AdapterTest")
    run("java", "-cp", BUILD / "tests", "com.dumbermini.keynavigator.SpatialNavigationTest")
    run("java", "-cp", BUILD / "tests", "com.dumbermini.keynavigator.AppPolicyTest")
    run("java", "-cp", BUILD / "tests", "com.dumbermini.keynavigator.ScrollNavigationTest")
    run("java", "-cp", BUILD / "tests", "com.dumbermini.keynavigator.ServiceListTest")
    run("java", "-cp", BUILD / "tests", "com.dumbermini.keynavigator.WiseKeypadTest")
    run(TOOLS / "aapt2", "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip")
    run(TOOLS / "aapt2", "link", "-o", BUILD / "resources.apk", "-I", ANDROID,
        "--manifest", ROOT / "AndroidManifest.xml", "--java", BUILD / "generated",
        BUILD / "resources.zip")
    # Remove stale class outputs only, never signing keys or source files.
    for directory in ("classes", "generated", "dex", "tests"):
        if directory == "classes":
            for old in (BUILD / directory).rglob("*.class"):
                old.unlink()
    sources = sorted((ROOT / "src").rglob("*.java")) + sorted((BUILD / "generated").rglob("*.java"))
    run("javac", "--release", "8", "-classpath", ANDROID, "-d", BUILD / "classes", *sources)
    run("jar", "cf", BUILD / "classes.jar", "-C", BUILD / "classes", ".")
    run(TOOLS / "d8", "--lib", ANDROID, "--min-api", "26", "--output", BUILD / "dex",
        BUILD / "classes.jar")
    with zipfile.ZipFile(BUILD / "resources.apk") as source, zipfile.ZipFile(BUILD / "unsigned.apk", "w") as dest:
        for item in source.infolist():
            dest.writestr(item, source.read(item.filename))
        dest.write(BUILD / "dex/classes.dex", "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
    run(TOOLS / "zipalign", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")
    key = BUILD / "development.p12"
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-storetype", "PKCS12", "-storepass", "android",
            "-keypass", "android", "-alias", "adapter", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000", "-dname", "CN=BankID Key Adapter Development")
    apk = BUILD / "KeyNavigator.apk"
    run(TOOLS / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--out", apk,
        BUILD / "aligned.apk")
    run(TOOLS / "apksigner", "verify", "--verbose", apk)
    print(f"Built {apk}")


if __name__ == "__main__":
    main()
