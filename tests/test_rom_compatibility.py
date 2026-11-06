#!/usr/bin/env python3
"""Host regressions for property behavior and the image audit."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from audit_rom_compatibility import audit_paths, audit_properties, audit_packages, parse_properties

TREBLE = Path(__file__).resolve().parents[1]


class BuildPropertiesTest(unittest.TestCase):
    def test_presentation_preserves_debug_capability_and_boot_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state = root / "props.json"
            initial = {
                "ro.build.type": "userdebug", "ro.build.tags": "test-keys",
                "ro.build.display.id": "LineageOS 21 20261105",
                "ro.build.flavor": "lineage_vanilla31-userdebug",
                "service.adb.root": "1",
                "ro.debuggable": "0", "ro.secure": "1", "ro.adb.secure": "1",
                "ro.boot.verifiedbootstate": "green", "ro.boot.vbmeta.device_state": "locked",
                "ro.build.fingerprint": "original-fingerprint",
                "ro.build.version.security_patch": "2026-06-01",
            }
            state.write_text(json.dumps(initial))
            mock = root / "property-tool"
            mock.write_text("#!/usr/bin/env python3\nimport json,os,sys\nfrom pathlib import Path\n"
                            "p=Path(os.environ['PROPERTY_STATE']);d=json.loads(p.read_text())\n"
                            "if Path(sys.argv[0]).name=='getprop': print(d.get(sys.argv[1],''))\n"
                            "else:\n d[sys.argv[1]]=sys.argv[2];p.write_text(json.dumps(d))\n"
                            " with open(os.environ['PROPERTY_WRITES'],'a') as f:f.write(sys.argv[1]+'\\n')\n")
            mock.chmod(0o755)
            for name in ("getprop", "resetprop_phh"):
                (root / name).symlink_to(mock)
            writes = root / "writes"
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                       PROPERTY_STATE=str(state), PROPERTY_WRITES=str(writes))
            command = ["sh", str(TREBLE / "dumberos-build-properties.sh")]
            subprocess.run(command, env=env, check=True)
            expected = dict(initial, **{"ro.build.type": "user", "ro.build.tags": "release-keys",
                            "ro.build.display.id": "DumberOS 21 20261105",
                            "ro.build.flavor": "dumberos_vanilla31-user"})
            self.assertEqual(json.loads(state.read_text()), expected)
            first_writes = writes.read_text()
            subprocess.run(command, env=env, check=True)
            self.assertEqual(writes.read_text(), first_writes, "Repeated execution must be idempotent")


class AuditTest(unittest.TestCase):
    def test_valid_runtime_properties(self):
        props = {
            "ro.build.type": "user", "ro.build.tags": "release-keys",
            "ro.debuggable": "0",
            "ro.secure": "1", "service.adb.root": "0",
            "ro.build.display.id": "DumberOS 21", "ro.build.flavor": "dumberos_vanilla31-user",
            "ro.dumberos.version": "21.0", "ro.build.product": "vanilla31",
            "ro.dumberos.build.version.plat.sdk": "9",
        }
        self.assertEqual(audit_properties(props), [])
        del props["service.adb.root"]
        self.assertEqual(audit_properties(props), [])

    def test_apk_filename_does_not_hide_detected_package(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "system/product/app/RenamedBackup/RenamedBackup.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"fixture")
            result = subprocess.CompletedProcess([], 0, "org.calyxos.backup.contacts\n", "")
            with patch("audit_rom_compatibility.subprocess.run", return_value=result):
                failures = audit_packages(root, Path("aapt2"))
            self.assertTrue(any("Detected CalyxOS package remains" in x for x in failures))

    def test_new_backup_package_passes_identity_audit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "system/product/app/LocalContactsBackup/LocalContactsBackup.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"fixture")
            result = subprocess.CompletedProcess([], 0, "org.dumberos.backup.contacts\n", "")
            with patch("audit_rom_compatibility.subprocess.run", return_value=result):
                self.assertEqual(audit_packages(root, Path("aapt2")), [])

    def test_unreadable_manifest_fails_identity_audit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "system/app/broken.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"fixture")
            result = subprocess.CompletedProcess([], 1, "", "invalid manifest")
            with patch("audit_rom_compatibility.subprocess.run", return_value=result):
                failures = audit_packages(root, Path("aapt2"))
            self.assertTrue(any("Cannot inspect APK package identity" in x for x in failures))

    def test_dangling_symlink_is_a_match(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "system/product/etc/sysconfig/lineage-sysconfig.xml"
            target.parent.mkdir(parents=True)
            target.symlink_to("missing")
            self.assertTrue(any("Detected path remains: /product/etc/sysconfig/lineage-sysconfig.xml"
                                in message for message in audit_paths(root)))

    def test_missing_replacements_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(len(audit_paths(Path(directory))), 5)

    def test_properties_require_nonroot_and_migrated_sdk(self):
        props = parse_properties("[ro.build.type]: [user]\n[service.adb.root]: [1]\n"
                                 "[ro.lineage.version]: [21]\n")
        failures = audit_properties(props)
        self.assertTrue(any("adb unroot" in x for x in failures))
        self.assertTrue(any("Legacy property" in x for x in failures))
        self.assertTrue(any("plat.sdk" in x for x in failures))


class UpdateCompatibilityTest(unittest.TestCase):
    def test_device_identity_uses_standard_property_and_accepts_old_recovery(self):
        repo = TREBLE.parents[2]
        source = (repo / "vendor/lineage/build/envsetup.sh").read_text()
        helper = source[source.index("function __dumberos_device_name()"):
                        source.index("function __print_lineage_functions_help()")]
        mock = '''adb() {
            case "$3" in
                ro.build.product) printf '%s\\n' "$NEW_DEVICE" ;;
                ro.lineage.device) printf '%s\\n' "$OLD_DEVICE" ;;
            esac
        }
        '''
        for new, old, expected in [("vanilla31", "old", "vanilla31"), ("", "vanilla31", "vanilla31")]:
            env = dict(os.environ, NEW_DEVICE=new, OLD_DEVICE=old)
            result = subprocess.run(["bash", "-c", mock + helper + "__dumberos_device_name"],
                                    env=env, text=True, capture_output=True, check=True)
            self.assertEqual(result.stdout.strip(), expected)


if __name__ == "__main__":
    unittest.main()
