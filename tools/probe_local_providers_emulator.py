#!/usr/bin/env python3
"""Exercise installed Namida/Musicolet with generated local audio, never a physical phone."""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--provider', choices=['namida', 'musicolet'], required=True)
parser.add_argument('--edition', choices=['public', 'personal'], default='public')
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'Disposable emulator required'
root = Path(__file__).resolve().parents[1]
out = root / 'work' / 'local-providers'
out.mkdir(parents=True, exist_ok=True)
provider = {'namida': 'com.msob7y.namida', 'musicolet': 'in.krosbits.musicolet'}[args.provider]
package = 'it.noxia.glancebeat' if args.edition == 'public' else 'it.noxia.ambientplayer'


def adb(*command):
    return subprocess.check_output(['adb', '-s', args.serial, *command], timeout=90)


assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Emulator required'
assert adb('shell', 'pm', 'path', provider).startswith(b'package:'), 'Install the real provider first'
for apk in [root / f'app/build/outputs/apk/{args.edition}/debug/app-{args.edition}-debug.apk',
            root / f'app/build/outputs/apk/androidTest/{args.edition}/debug/app-{args.edition}-debug-androidTest.apk']:
    assert apk.is_file(), 'Build public/personal debug and instrumentation APKs first'
    adb('install', '-r', str(apk))

# Start the real provider playing GlanceBeat Sample 1/2/3 before running. The test
# refuses other track titles and never creates a synthetic media session.
log = adb('shell', 'am', 'instrument', '-w', '-e', 'local_provider', provider,
          package + '.test/it.noxia.ambientplayer.DrivingInstrumentation')
(out / (args.provider + '-integration.log')).write_bytes(log)
raw = adb('exec-out', 'run-as', package, 'cat', 'files/local-' + provider + '.json')
result = json.loads(raw)
(out / (args.provider + '-result.json')).write_bytes(raw)
for suffix in ['ambient', 'queue', 'player']:
    try:
        png = adb('exec-out', 'run-as', package, 'cat', 'files/local-' + provider + '-' + suffix + '.png')
        if png.startswith(b'\x89PNG'):
            (out / (args.provider + '-' + suffix + '.png')).write_bytes(png)
    except subprocess.CalledProcessError:
        pass
assert result['success'], result
print(f'{args.provider}: {len(result["checks"])} PASS; queue selection verified: {result.get("queue_selection_verified", False)}')
