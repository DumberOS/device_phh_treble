#!/usr/bin/env python3
"""Test the real wrapper with a fake vendor utility; never access a phone."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

TREBLE = Path(__file__).resolve().parents[1]
SCRIPT = (TREBLE / 'sitronix/dumber-mini-touch-update.sh').read_text()
TOOL_HASH = 'ad6bb0639605087ef0344770507a1677c0458303976f47ff175a35705428e370'

FAKE_TOOL = r'''#!/usr/bin/env python3
import os, pathlib, sys
root = pathlib.Path(os.environ['TEST_ROOT'])
action = sys.argv[3]
with (root/'actions').open('a') as log: log.write(action+'\n')
if action == '3':
    behavior = os.environ.get('TEST_QUERY', '')
    if behavior == 'malformed': print('unexpected output'); sys.exit(0)
    if behavior == 'failure': print('st_i2c_read_bytes failed! (read)')
    version = int((root/'version').read_text())
    print(f'st_query_fw_version , version: 0x{version:X} , revision:0 0 0 0')
    if behavior == 'duplicate':
        print('st_query_fw_version , version: 0x1 , revision:0 0 0 0')
    sys.exit(0)
assert action == '1'
assert pathlib.Path(sys.argv[4]).read_bytes() == (root/'firmware').read_bytes()
assert pathlib.Path(sys.argv[4]) != root/'firmware'
behavior = os.environ.get('TEST_UPDATE', '')
if behavior == 'exit': print('failed'); sys.exit(1)
if behavior == 'silent': sys.exit(0)
if behavior == 'false_success': print('st_i2c_write_bytes failed!')
if behavior == 'truncated': print('set write size to driver_dump_size')
if behavior != 'unchanged': (root/'version').write_text('3')
print('upgrad success!')
print('upgrade result : 13 ')
'''


class UpdateTest(unittest.TestCase):
    def run_wrapper(self, version=1, model='Dumber mini', node=True, query='',
                    update='', corrupt='', locked=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tool = root/'tool'
            tool.write_text(FAKE_TOOL)
            tool.chmod(0o755)
            tool_hash = hashlib.sha256(tool.read_bytes()).hexdigest()
            firmware = root/'firmware'
            firmware.write_bytes((TREBLE/'sitronix/7123.dump').read_bytes())
            (root/'version').write_text(str(version))
            work = root/'work'
            if locked: work.mkdir()
            if corrupt == 'firmware': firmware.write_bytes(b'bad firmware')
            if corrupt == 'tool': tool.write_text(FAKE_TOOL + '\n# modified\n')
            script = SCRIPT.replace('tool=/system/bin/st7123-vendor-tool', f'tool={tool}')
            script = script.replace('firmware=/system/etc/firmware/dumber-mini/7123.dump',
                                    f'firmware={firmware}')
            script = script.replace('workdir=/dev/st7123-update', f'workdir={work}')
            script = script.replace(TOOL_HASH, tool_hash)
            script = script.replace('[ -c /dev/sitronixDev ]', '[ "$TEST_NODE" = 1 ]')
            mocks = 'getprop() { printf "%s" "$TEST_MODEL"; }\nsleep() { :; }\n'
            env = dict(os.environ, TEST_ROOT=str(root), TEST_MODEL=model,
                       TEST_NODE=str(int(node)), TEST_QUERY=query, TEST_UPDATE=update)
            result = subprocess.run(['sh'], input=mocks + script, env=env,
                                    text=True, capture_output=True)
            actions = (root/'actions').read_text().splitlines() if (root/'actions').exists() else []
            self.assertEqual(work.exists(), locked, 'owned scratch directory must be cleaned')
            return result, actions

    def test_updates_original_and_previous(self):
        for version in (1, 2):
            with self.subTest(version=version):
                result, actions = self.run_wrapper(version)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(actions, ['3', '1', '3'])
                self.assertIn('version 3 verified on controller', result.stdout)

    def test_preserves_current_and_newer(self):
        for version in (3, 4, 5, 10, 255):
            result, actions = self.run_wrapper(version)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(actions, ['3'])

    def test_refuses_unknown_version(self):
        result, actions = self.run_wrapper(0)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(actions, ['3'])

    def test_guards_without_device_access(self):
        for kwargs in ({'model':'Other'}, {'node':False}):
            result, actions = self.run_wrapper(**kwargs)
            self.assertEqual(result.returncode, 0)
            self.assertEqual(actions, [])
        for kwargs in ({'corrupt':'firmware'}, {'corrupt':'tool'}, {'locked':True}):
            result, actions = self.run_wrapper(**kwargs)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(actions, [])

    def test_query_failures_prevent_update(self):
        for query in ('malformed', 'failure', 'duplicate'):
            result, actions = self.run_wrapper(query=query)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(actions, ['3'])

    def test_vendor_failure_even_with_zero_exit(self):
        for update in ('exit', 'silent', 'false_success', 'truncated'):
            result, actions = self.run_wrapper(update=update)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(actions, ['3', '1'])
            self.assertNotIn('verified on controller', result.stdout)

    def test_verification_failure_does_not_repeat_upgrade(self):
        result, actions = self.run_wrapper(update='unchanged')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(actions, ['3', '1'] + ['3'] * 5)
        self.assertIn('could not be verified', result.stderr)


if __name__ == '__main__':
    unittest.main()
