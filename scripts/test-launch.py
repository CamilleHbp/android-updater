"""Run live-catalogue launch regressions against the isolated fixture on an emulator."""
import subprocess,time,xml.etree.ElementTree as ET,sys,re,os
from pathlib import Path
ADB=str(Path(os.environ['ANDROID_HOME'])/'platform-tools/adb')
if len(sys.argv) != 3 or not sys.argv[1].startswith('emulator-'):
 raise SystemExit('Usage: test-launch.py emulator-SERIAL /path/to/fixture.apk')
PKG='studio.camille.updater.fixture'
def adb(*args):
 return subprocess.run([ADB,'-s',sys.argv[1],*args],check=True,capture_output=True,text=True).stdout
def dump():
 adb('shell','uiautomator','dump','/sdcard/update-test.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/update-test.xml'))
def prompt(timeout=25):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  messages=[n.get('text','') for n in dump().iter('node') if n.get('resource-id')=='android:id/message']
  if any('available' in s for s in messages): return messages
 return []
def start():
 adb('shell','am','start','-W','-n',PKG+'/.FixtureActivity')
def cold():
 adb('shell','am','force-stop',PKG);start()
adb('install','-r',sys.argv[2]);cold()
assert prompt(), 'No cold-start prompt'
print('PASS cold launch',flush=True)
button=next(n for n in dump().iter('node') if n.get('resource-id')=='android:id/button2')
x1,y1,x2,y2=map(int,re.findall(r'\d+',button.get('bounds')))
adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
assert not prompt(3), 'Dismissed prompt reopened without a launch'
adb('shell','input','keyevent','KEYCODE_HOME');start()
assert prompt(), 'Later suppressed next foreground check'
print('PASS immediate relaunch after Later',flush=True)
adb('shell','am','force-stop',PKG)
adb('shell','run-as',PKG,'mkdir','-p','shared_prefs')
prefs='<?xml version="1.0" encoding="utf-8"?><map>'+''.join(f'<long name="{k}" value="9223372036854775807" />' for k in ['snooze','nextCheck','nextCheckV2'])+'</map>'
subprocess.run([ADB,'-s',sys.argv[1],'shell','run-as',PKG,'sh','-c',"'cat > shared_prefs/camille-updater.xml'"],input=prefs,text=True,check=True)
start();assert prompt(), 'Old persisted deadlines suppressed launch'
print('PASS legacy future deadlines ignored',flush=True)
