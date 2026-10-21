#!/usr/bin/env python3
"""Silent emulator-only integration checks; never point synthetic fixtures at a physical phone."""
import argparse,json,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True)
p.add_argument('--edition',choices=['public','personal'],default='public')
p.add_argument('--mode',choices=['swipes','stability','continuity','columns','open_layout','bitchord','youtube_shuffle','carousel_commit','catalog','music_launch','accessory','localization','setup'],action='append')
a=p.parse_args();package='it.noxia.glancebeat' if a.edition=='public' else 'it.noxia.ambientplayer';assert a.serial.startswith('emulator-'),'Disposable emulator required'
root=Path(__file__).resolve().parents[1];(root/'work').mkdir(exist_ok=True)
def adb(*args,required=True):
 return subprocess.run(['adb','-s',a.serial,*args],check=required,capture_output=True,timeout=90).stdout
def shell(*args,required=True):return adb('shell',*args,required=required)
model=shell('getprop','ro.product.model').decode();fingerprint=shell('getprop','ro.build.fingerprint').decode()
assert 'sdk' in model or 'generic' in fingerprint,'Emulator required'
apks=[root/f'app/build/outputs/apk/{a.edition}/debug/app-{a.edition}-debug.apk',root/f'app/build/outputs/apk/androidTest/{a.edition}/debug/app-{a.edition}-debug-androidTest.apk',root/'tools/music-fixture/build/outputs/apk/debug/MusicFixture-debug.apk',root/'tools/stability-fixture/build/outputs/apk/debug/StabilityFixture-debug.apk']
if any(m in (a.mode or []) for m in ['bitchord','youtube_shuffle']):apks.append(root/'tools/bitchord-fixture/build/outputs/apk/debug/BitchordFixture-debug.apk')
if any(m in (a.mode or []) for m in ['catalog','music_launch']):
 catalog_apks=sorted(p for p in (root/'tools/catalog-fixture/build/outputs/apk').glob('*/debug/*.apk') if p.parent.parent.name!='youtube')
 assert len(catalog_apks)==5,'Build all five catalog-fixture debug flavors first'
 apks.extend(catalog_apks)
for apk in apks:
 assert apk.exists(),f'Build the app, instrumentation and fixture projects first: missing {apk}'
 adb('install','-r',str(apk))
def settle():
 shell('cmd','notification','disallow_listener',package+'/it.noxia.ambientplayer.MusicSessionService')
 shell('am','force-stop',package)
 shell('locksettings','clear','--old','1234',required=False);shell('locksettings','set-disabled','true')
 for key in ['KEYCODE_WAKEUP','KEYCODE_MENU']:shell('input','keyevent',key)
 shell('wm','dismiss-keyguard');time.sleep(1)
original_immersive=shell('settings','get','secure','immersive_mode_confirmations').decode().strip()
try:
 shell('settings','put','secure','immersive_mode_confirmations','confirmed')
 for mode in a.mode or ['stability','continuity','columns','open_layout']:
  settle()
  log=adb('shell','am','instrument','-w','-e',mode,'true',package+'.test/it.noxia.ambientplayer.DrivingInstrumentation')
  (root/'work'/f'{mode}-integration.log').write_bytes(log)
  name=mode.replace('_','-')
  data=adb('exec-out','run-as',package,'cat',f'files/{name}.json')
  result=json.loads(data);(root/'work'/f'{name}-integration.json').write_bytes(data)
  assert result['success'],result
  print(f'{mode}: {len(result["checks"])} PASS',flush=True)
finally:
 settle();shell('wm','size','reset');shell('wm','density','reset')
 if original_immersive in ('','null'):shell('settings','delete','secure','immersive_mode_confirmations')
 else:shell('settings','put','secure','immersive_mode_confirmations',original_immersive)
