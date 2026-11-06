#!/usr/bin/env python3
"""Audit extracted ROM paths and an optional `adb shell getprop` capture.

Use --image-root with PRODUCT_OUT (system/product nesting is supported), or
with an extracted filesystem root. A properties capture must come from the
booted image after adb unroot; raw build.prop is not runtime evidence.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path

RULES = json.loads(Path(__file__).with_name("rom_compatibility_rules.json").read_text())


def candidates(root, path):
    relative = path.lstrip("/")
    yield root / relative
    if relative.startswith(("product/", "system_ext/", "vendor/")):
        yield root / "system" / relative


def audit_paths(root):
    failures = []
    for path in RULES["debug_paths"] + RULES["lineage_paths"]:
        # lstat detects even dangling symlinks, like the custom-ROM rule.
        for candidate in candidates(root, path):
            try:
                candidate.lstat()
            except FileNotFoundError:
                continue
            failures.append(f"Detected path remains: {path} ({candidate})")
            break
    for path in ("/system/framework/org.dumberos.platform.jar",
                 "/system/framework/org.dumberos.platform-res.apk",
                 "/system/etc/init/atrace_dumberos.rc",
                 "/system/etc/init/bootstat-dumberos.rc",
                 "/system/bin/dumberos-build-properties.sh"):
        if not any(p.is_file() for p in candidates(root, path)):
            failures.append(f"Required replacement missing: {path}")
    return failures


def audit_packages(root, aapt2):
    """Read binary APK manifests; installed filenames do not identify packages."""
    failures = []
    packages = set()
    seen = set()
    for partition in ("system", "product", "system_ext", "vendor", "odm"):
        for apk in sorted((root / partition).rglob("*.apk")):
            resolved = apk.resolve()
            if resolved in seen:
                continue
            seen.add(resolved)
            result = subprocess.run([str(aapt2), "dump", "packagename", str(apk)],
                                    capture_output=True, text=True)
            package = result.stdout.strip()
            if result.returncode or not re.fullmatch(r"[A-Za-z0-9_.]+", package):
                failures.append(f"Cannot inspect APK package identity: {apk}: {result.stderr.strip()}")
                continue
            packages.add(package)
            if package in RULES["calyx_packages"]:
                failures.append(f"Detected CalyxOS package remains: {package} ({apk})")
    if "org.dumberos.backup.contacts" not in packages:
        failures.append("Required contacts backup package missing: org.dumberos.backup.contacts")
    return failures


def parse_properties(text):
    return dict(re.findall(r"^\[([^\]]+)\]: \[(.*)\]$", text, re.MULTILINE))


def audit_properties(props):
    failures = []
    for key, expected in {"ro.build.type": "user", "ro.build.tags": "release-keys",
                          "ro.debuggable": "0", "ro.secure": "1"}.items():
        if props.get(key) != expected:
            failures.append(f"{key}: expected {expected!r}, got {props.get(key)!r}")
    for key in RULES["lineage_properties"]:
        if props.get(key):
            failures.append(f"Legacy property remains: {key}")
    for key in ("ro.build.display.id", "ro.build.flavor"):
        value = props.get(key, "")
        if not value or "lineage" in value.lower():
            failures.append(f"Missing or Lineage-branded metadata: {key}={value!r}")
    if props.get("service.adb.root", "") not in ("", "0"):
        failures.append("Capture must be taken after adb unroot (service.adb.root is not 0/empty)")
    for key in ("ro.dumberos.version", "ro.build.product", "ro.dumberos.build.version.plat.sdk"):
        if not props.get(key):
            failures.append(f"Migrated ROM metadata missing: {key}")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-root", type=Path, required=True)
    parser.add_argument("--properties", type=Path)
    parser.add_argument("--aapt2", type=Path, help="Also audit installed APK package identities")
    args = parser.parse_args()
    failures = audit_paths(args.image_root)
    if args.aapt2:
        failures += audit_packages(args.image_root, args.aapt2)
    if args.properties:
        failures += audit_properties(parse_properties(args.properties.read_text()))
    for failure in failures:
        print(f"FAIL: {failure}")
    if failures:
        return 1
    print("PASS: known userdebug/Lineage filesystem checks")
    print("PASS: runtime property checks" if args.properties else
          "NOT RUN: runtime properties; provide --properties from a booted device")
    print("PASS: known CalyxOS package checks" if args.aapt2 else
          "NOT RUN: APK package checks; provide --aapt2")
    print("Not covered: other CalyxOS inputs, other detectors, or the app's overall security verdict.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
