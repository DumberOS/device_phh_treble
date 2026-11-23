#!/usr/bin/env python3
"""Exercise NFC feature activation in the production early-boot model block."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

TREBLE = Path(__file__).resolve().parents[1]
REPO = TREBLE.parents[2]
SCRIPT = (TREBLE / "rw-system.sh").read_text()
START = SCRIPT.index('case "$(getprop ro.product.vendor.model)" in')
BLOCK = SCRIPT[START:SCRIPT.index("\nesac", START) + len("\nesac")]
SOURCE = "/system/phh/permissions-dumber-nfc.xml"
TARGET = "/system/etc/permissions/dumberos-nfc.xml"
RF_SOURCE = "/system/phh/libnfc-tms_RF-dumber.conf"
RF_TARGET = "/vendor/etc/libnfc-tms_RF.conf"


def features(path):
    root = ET.parse(path).getroot()
    if root.tag != "permissions":
        raise AssertionError(f"Invalid permissions root in {path}")
    return {feature.attrib["name"] for feature in root.findall("feature")}


def original_features():
    result = set()
    for suffix in ("", ".hce", ".hcef", ".uicc"):
        result.update(features(REPO / "frameworks/native/data/etc" /
                               f"android.hardware.nfc{suffix}.xml"))
    return result


class NfcBootTest(unittest.TestCase):
    def boot(self, model="Dumber mini", controller=True, missing=None, failure=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = {SOURCE: root / "features-source.xml", TARGET: root / "features-target.xml",
                     RF_SOURCE: root / "rf-source.conf", RF_TARGET: root / "rf-target.conf"}
            paths[SOURCE].write_bytes((TREBLE / "nfc/permissions-dumber.xml").read_bytes())
            paths[TARGET].write_bytes((TREBLE / "nfc/permissions-empty.xml").read_bytes())
            paths[RF_SOURCE].write_text("manufacturer RF configuration")
            paths[RF_TARGET].write_text("stock RF configuration")
            if missing:
                paths[missing].unlink()

            # A regular file must not count as an NFC controller. /dev/null
            # supplies the positive character-device case without hardware.
            node = root / "tms_nfc"
            if controller is not None:
                node.write_text("")
            controller_path = "/dev/null" if controller else str(node)
            body = BLOCK.replace("/dev/tms_nfc", controller_path)
            body = body.replace("/dev/sitronixDev", str(root / "no-touch-controller"))
            for original, replacement in paths.items():
                body = body.replace(original, f'"{replacement}"')
            mocks = '''
getprop() { printf '%s' "$TEST_MODEL"; }
resetprop_phh() { :; }
log() { printf '%s\\n' "$*"; }
mount() {
    printf 'mount: %s\\n' "$*"
    if [ "$3" = "$TEST_FEATURE_SOURCE" ] && [ "$TEST_MOUNT_FAIL" = 1 ]; then
        return 1
    fi
    cp "$3" "$4"
}
'''
            env = dict(os.environ, TEST_MODEL=model, TEST_FEATURE_SOURCE=str(paths[SOURCE]),
                       TEST_MOUNT_FAIL=str(int(failure)))
            result = subprocess.run(["sh"], input=mocks + body + "\necho continued\n",
                                    text=True, capture_output=True, env=env, check=True)
            active = features(paths[TARGET]) if paths[TARGET].exists() else set()
            return active, paths[RF_TARGET].read_text(), result.stdout

    def test_supported_models_keep_all_existing_features_and_rf_fix(self):
        for model in ("Dumber mini", "S9"):
            with self.subTest(model=model):
                active, rf, output = self.boot(model)
                self.assertEqual(active, original_features())
                self.assertEqual(rf, "manufacturer RF configuration")
                self.assertEqual(output.count("mount:"), 2)

    def test_other_models_never_activate_fallback_even_with_controller(self):
        for model in ("F21 Pro", "Other phone", "", "DumberOS"):
            for controller in (True, False):
                with self.subTest(model=model, controller=controller):
                    active, rf, output = self.boot(model, controller=controller)
                    self.assertEqual(active, set())
                    self.assertEqual(rf, "stock RF configuration")
                    self.assertNotIn("mount:", output)

    def test_supported_models_without_controller_keep_empty_features(self):
        for model in ("Dumber mini", "S9"):
            for controller in (False, None):
                with self.subTest(model=model, controller=controller):
                    active, _, output = self.boot(model, controller=controller)
                    self.assertEqual(active, set())
                    self.assertNotIn("features-source.xml", output)

    def test_missing_xml_logs_and_continues_without_features(self):
        for missing in (SOURCE, TARGET):
            with self.subTest(missing=missing):
                active, rf, output = self.boot(missing=missing)
                self.assertEqual(active, set())
                self.assertIn("Missing Dumber mini NFC feature declarations source or target", output)
                self.assertEqual(rf, "manufacturer RF configuration")
                self.assertTrue(output.endswith("continued\n"))

    def test_mount_failure_keeps_empty_features_and_continues(self):
        active, rf, output = self.boot(failure=True)
        self.assertEqual(active, set())
        self.assertIn("-p e -t PHH Failed to mount Dumber mini NFC feature declarations", output)
        self.assertEqual(rf, "manufacturer RF configuration")
        self.assertTrue(output.endswith("continued\n"))


class NfcPackagingTest(unittest.TestCase):
    def test_only_empty_features_installed_in_scanned_directory(self):
        copies = re.findall(r"([^\s:]+):([^\s\\]+)", (TREBLE / "base.mk").read_text())
        nfc_copies = [(source, dest) for source, dest in copies
                      if dest.startswith("system/etc/permissions/") and "nfc" in dest]
        self.assertEqual(len(nfc_copies), 1)
        source, dest = nfc_copies[0]
        self.assertEqual(dest, TARGET.lstrip("/"))
        self.assertEqual(features(REPO / source), set())
        fallback = [source for source, dest in copies if dest == SOURCE.lstrip("/")]
        self.assertEqual(len(fallback), 1)
        self.assertEqual(features(REPO / fallback[0]), original_features())


if __name__ == "__main__":
    unittest.main()
