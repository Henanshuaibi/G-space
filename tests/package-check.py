from pathlib import Path
import subprocess, zipfile, xml.etree.ElementTree as ET
root = ET.parse('AndroidManifest.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'
version = root.attrib[a+'versionName']
apk_path = f'build/G-space-{version}.apk'
assert root.attrib['package'] == 'cn.g.space'
assert {p.attrib[a+'name'] for p in root.findall('uses-permission')} == {'android.permission.PACKAGE_USAGE_STATS'}
assert root.find('queries/intent/category').attrib[a+'name'] == 'android.intent.category.LAUNCHER'
assert root.find('application').attrib[a+'allowBackup'] == 'false'
assert root.find('application/activity').attrib[a+'exported'] == 'true'
assert not root.findall('application/service')
with zipfile.ZipFile(apk_path) as apk:
    assert {'classes.dex', 'AndroidManifest.xml', 'resources.arsc'} <= set(apk.namelist())
    assert not any(x.endswith(('.keystore', '.jks', '.java')) or 'signing-password' in x for x in apk.namelist())
    dex = apk.read('classes.dex')
    password = Path('tools/signing-password').read_bytes().strip()
    assert password not in dex
    assert b'android.intent.action.DELETE' not in dex
    assert b'android.intent.action.UNINSTALL_PACKAGE' not in dex
    assert b'cn/pulse/player/MainActivity' not in dex
subprocess.run(['apksigner', 'verify', '--verbose', apk_path], check=True)
subprocess.run(['zipalign', '-c', '4', apk_path], check=True)
print('PASS: package identity, permission scope, launcher queries, no uninstall action, no signing secrets, signature and alignment')
