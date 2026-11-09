#!/usr/bin/env python3
"""Exercise the actual device-model startup block with mocked Android commands."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

TREBLE = Path(__file__).resolve().parents[1]
SCRIPT = (TREBLE / 'rw-system.sh').read_text()
START = SCRIPT.index('case "$(getprop ro.product.vendor.model)" in')
BLOCK = SCRIPT[START:SCRIPT.index('\nesac', START) + len('\nesac')]


class TouchBootTest(unittest.TestCase):
    def boot(self, model='Dumber mini', node=True, failure=False):
        with tempfile.TemporaryDirectory() as directory:
            helper = Path(directory) / 'updater'
            helper.write_text('#!/bin/sh\necho "updater: $*"\nexit "$TEST_FAILURE"\n')
            helper.chmod(0o755)
            # Replace only the Android device probe and executable path; run
            # the production model guard, error handling and logging unchanged.
            body = BLOCK.replace('[ -c /dev/sitronixDev ]', '[ "$TEST_NODE" = 1 ]')
            body = body.replace('/system/bin/sh /system/bin/dumber-mini-touch-update.sh', str(helper))
            mocks = '''
getprop() { printf '%s' "$TEST_MODEL"; }
resetprop_phh() { :; }
log() { printf '%s\\n' "$*"; }
'''
            env = dict(os.environ, TEST_MODEL=model, TEST_NODE=str(int(node)),
                       TEST_FAILURE=str(int(failure)))
            result = subprocess.run(['sh'], input=mocks + body + '\necho continued\n',
                                    text=True, capture_output=True, env=env, check=True)
            return result.stdout

    def test_target_models(self):
        for model in ('Dumber mini', 'S9'):
            output = self.boot(model)
            self.assertIn('updater:', output)
            self.assertNotIn('update failed', output)

    def test_other_models(self):
        for model in ('Other phone', '', 'DumberOS'):
            self.assertNotIn('updater:', self.boot(model))

    def test_missing_controller(self):
        self.assertNotIn('updater:', self.boot(node=False))

    def test_failure_logs_and_continues_boot(self):
        output = self.boot(failure=True)
        self.assertIn('-p e -t PHH ST7123 firmware update failed:', output)
        self.assertTrue(output.endswith('continued\n'))


if __name__ == '__main__':
    unittest.main()
