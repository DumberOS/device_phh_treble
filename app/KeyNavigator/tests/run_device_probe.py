#!/usr/bin/env python3
"""Run only on an explicitly selected, verified keypad evdev device, with rooted ADB.

The probe waits for an empty PIN screen and requests only Linux KEY_1 and KEY_BACK.
It never requests confirmation. Root is needed by this test, not by the adapter.
"""
import argparse
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--input-device", required=True)
args = parser.parse_args()
if not re.fullmatch(r"/dev/input/event[0-9]+", args.input_device):
    parser.error("Expected /dev/input/eventN")
adb = ["adb", "-s", args.serial]
if subprocess.check_output(adb + ["shell", "id", "-u"], text=True).strip() != "0":
    raise SystemExit("This hardware-path test requires adb root; the adapter does not.")
process = subprocess.Popen(adb + ["shell", "am", "instrument", "-w",
    "com.dumbermini.keynavigator.probe/.DeviceProbe"], stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT, text=True, bufsize=1)
passed = False
try:
    for line in process.stdout:
        print(line, end="", flush=True)
        request = re.fullmatch(r"INSTRUMENTATION_STATUS: progress=REQUEST_KEY (2|158) (tap|hold)\s*", line)
        if request:
            scan, mode = request.groups()
            device = args.input_device
            down = f"sendevent {device} 1 {scan} 1 && sendevent {device} 0 0 0"
            repeat = f"sleep 0.6 && sendevent {device} 1 {scan} 2 && sendevent {device} 0 0 0 && sleep 0.3"
            up = f"sendevent {device} 1 {scan} 0; sendevent {device} 0 0 0"
            try:
                subprocess.run(adb + ["shell", down + " && " + (repeat if mode == "hold" else "sleep 0.08")], check=True)
            finally:
                subprocess.run(adb + ["shell", up], check=True)
        if "INSTRUMENTATION_RESULT: result=PASS:" in line:
            passed = True
    process.wait()
finally:
    if process.poll() is None:
        process.terminate()
if not passed:
    raise SystemExit("Device probe did not pass")
