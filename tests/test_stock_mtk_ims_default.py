#!/usr/bin/env python3
"""Exercise the real IMS boot-script sections with mocked Android commands."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


TREBLE = Path(__file__).resolve().parents[1]
RW_SYSTEM = (TREBLE / "rw-system.sh").read_text()
SELECTOR = RW_SYSTEM[RW_SYSTEM.index('STOCK_MTK_IMS_FLAG='):RW_SYSTEM.index('\nfixSPL\n')]
ON_BOOT = (TREBLE / "phh-on-boot.sh").read_text()
SUPPORT = ON_BOOT[ON_BOOT.index('vendor_model='):ON_BOOT.index('\nif grep -qF android.hardware.boot')]

MOCKS = r'''
getprop() {
    case "$1" in
        ro.product.vendor.model) printf '%s\n' "$TEST_MODEL" ;;
        sys.phh.stock_mtk_ims) printf '%s\n' "$active_stack" ;;
        persist.mtk_ims_support) printf '%s\n' "$TEST_IMS_SUPPORT" ;;
        persist.mtk_epdg_support) printf '%s\n' "$TEST_EPDG_SUPPORT" ;;
    esac
}
setprop() {
    printf 'setprop %s %s\n' "$1" "$2"
    if [ "$1" = sys.phh.stock_mtk_ims ]; then active_stack="$2"; fi
}
log() { printf 'log %s\n' "$*"; }
mount() {
    printf 'mount %s\n' "$*"
    case "$*" in
        *stock_mtk_ims/MtkTelephonyAssist*) [ "$TEST_MOUNT_FAIL" != 1 ] ;;
    esac
}
umount() { printf 'umount %s\n' "$*"; }
touch() {
    if [ "${1##*/}" = "$TEST_TOUCH_FAIL" ]; then return 1; fi
    command touch "$@"
}
reboot() { printf 'reboot\n'; }
'''


class StockMtkImsDefaultTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.state = Path(self.temp.name)
        self.flag = self.state / 'dumber_mini_stock_ims'
        self.marker = self.state / 'stock_mtk_ims_default_initialized'
        self.ims_support = '0'
        self.epdg_support = '0'

    def boot(self, model='Dumber mini', mount_fail=False, touch_fail=''):
        env = dict(os.environ, PHH_STATE_DIR=str(self.state), TEST_MODEL=model,
                   TEST_IMS_SUPPORT=self.ims_support,
                   TEST_EPDG_SUPPORT=self.epdg_support,
                   TEST_MOUNT_FAIL=str(int(mount_fail)), TEST_TOUCH_FAIL=touch_fail)
        result = subprocess.run(['/bin/sh'], input=MOCKS + SELECTOR + '\n' + SUPPORT,
                                env=env, text=True, capture_output=True, check=True)
        for line in result.stdout.splitlines():
            if line.startswith('setprop persist.mtk_ims_support '):
                self.ims_support = line.split()[-1]
            elif line.startswith('setprop persist.mtk_epdg_support '):
                self.epdg_support = line.split()[-1]
        return result.stdout

    def assert_stack(self, output, stock):
        self.assertIn('setprop sys.phh.stock_mtk_ims ' + str(stock).lower(), output)

    def test_fresh_install_or_old_default_migrates_once(self):
        first = self.boot()
        self.assert_stack(first, True)
        self.assertTrue(self.flag.is_file())
        self.assertTrue(self.marker.is_file())
        self.assertEqual(first.splitlines().count('reboot'), 1)
        self.assertEqual((self.ims_support, self.epdg_support), ('1', '1'))
        second = self.boot()
        self.assert_stack(second, True)
        self.assertNotIn('reboot', second)
        self.assertNotIn('Initialized stock', second)

    def test_existing_stock_needs_no_extra_reboot(self):
        self.flag.touch()
        self.ims_support = self.epdg_support = '1'
        output = self.boot()
        self.assert_stack(output, True)
        self.assertTrue(self.marker.is_file())
        self.assertNotIn('reboot', output)

    def test_partial_support_configuration_reboots_once(self):
        self.ims_support = '1'
        self.assertIn('reboot', self.boot())
        self.assertNotIn('reboot', self.boot())

    def test_opt_out_survives_boots_and_ota_then_can_reenable(self):
        self.boot()
        # Settings selects legacy IMS by deleting only the selection flag.
        self.flag.unlink()
        output = self.boot()
        self.assert_stack(output, False)
        self.assertEqual(output.splitlines().count('reboot'), 1)
        self.assertEqual((self.ims_support, self.epdg_support), ('0', '0'))
        # An OTA replaces the scripts but preserves this metadata state.
        for _ in range(2):
            output = self.boot()
            self.assert_stack(output, False)
            self.assertFalse(self.flag.exists())
            self.assertNotIn('reboot', output)
        self.flag.touch()
        self.assert_stack(self.boot(), True)
        self.assertNotIn('reboot', self.boot())

    def test_supported_models_match_settings_normalization(self):
        for model in ('Dumber mini', 'S9', ' DUMBER MINI ', '\ts9\t'):
            with self.subTest(model=model):
                self.assert_stack(self.boot(model=model), True)
                self.flag.unlink()
                self.marker.unlink()

    def test_other_model_keeps_old_default_and_explicit_selection(self):
        self.assert_stack(self.boot(model='Other phone'), False)
        self.assertFalse(self.flag.exists())
        self.assertFalse(self.marker.exists())
        self.flag.touch()
        self.assert_stack(self.boot(model='Other phone'), True)
        self.assertFalse(self.marker.exists())

    def test_selection_write_failure_does_not_mark_initialized(self):
        output = self.boot(touch_fail=self.flag.name)
        self.assert_stack(output, False)
        self.assertFalse(self.marker.exists())
        self.assertIn('will retry next boot', output)
        self.assert_stack(self.boot(), True)
        self.assertTrue(self.marker.exists())

    def test_marker_write_failure_is_retryable(self):
        output = self.boot(touch_fail=self.marker.name)
        self.assert_stack(output, True)
        self.assertFalse(self.marker.exists())
        self.assertIn('will retry next boot', output)
        self.assert_stack(self.boot(), True)
        self.assertTrue(self.marker.exists())

    def test_mount_failure_falls_back_and_retries_next_boot(self):
        output = self.boot(mount_fail=True)
        self.assert_stack(output, False)
        self.assertTrue(self.flag.is_file())
        self.assertTrue(self.marker.is_file())
        self.assertIn('falling back to TrebleApp IMS', output)
        self.assertIn('umount /system/priv-app/ImsService', output)
        self.assertIn('mount -o bind /mnt/phh/empty_dir', output)
        self.assertNotIn('reboot', output)
        self.assert_stack(self.boot(), True)


if __name__ == '__main__':
    unittest.main()
