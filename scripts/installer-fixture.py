#!/usr/bin/env python3
"""Prepare or verify the isolated updater installation test on an emulator."""
import argparse
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'studio.camille.updater.fixture'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('action', choices=['prepare', 'verify'])
parser.add_argument('serial')
parser.add_argument('--locale', default='en', choices=['en', 'fr'])
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('This test only runs on emulators')
adb = str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb')

def run(*command, **kwargs):
    return subprocess.run([adb, '-s', args.serial, *command], check=True, **kwargs)

if args.action == 'prepare':
    subprocess.run([str(ROOT / 'gradlew'), '-p', str(ROOT), '-PupdaterFixture=true',
                    ':installerFixture:assembleOldDebug', ':installerFixture:assembleUpdatedDebug'], check=True)
    apks = ROOT / 'fixtures/installer/build/outputs/apk'
    run('install', '-r', str(apks / 'old/debug/installerFixture-old-debug.apk'))
    run('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'cache/camille-updater')
    with (apks / 'updated/debug/installerFixture-updated-debug.apk').open('rb') as stream:
        run('shell', 'run-as', PACKAGE, 'sh', '-c', "'cat > cache/camille-updater/update.apk'", stdin=stream)
    run('shell', 'am', 'start', '-n', PACKAGE + '/.FixtureActivity', '--ez', 'update', 'true', '--es', 'locale', args.locale)
    print('Approve installation through the fixture UI, then run verify.')
else:
    metadata = run('shell', 'dumpsys', 'package', PACKAGE, capture_output=True, text=True).stdout
    assert 'versionCode=2 ' in metadata and 'versionName=2.0' in metadata, 'New APK was not installed'
    state = run('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/fixture.xml', capture_output=True, text=True).stdout
    assert 'preserve-me' in state, 'Application data was lost'
    print('Android installed version 2.0/code 2 and retained application data.')
