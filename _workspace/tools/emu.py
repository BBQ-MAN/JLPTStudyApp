# 사용: python3 -I emu.py <명령> [인자]  — adb로 화면을 덤프·탭·캡처한다.
#   dump                 화면의 텍스트·설명 노드 목록
#   tap "<텍스트>" [n]   텍스트 또는 content-desc가 정확히 같은(없으면 포함하는) n번째 노드 중심을 누른다
#   shot <이름>          _workspace/emu/<이름>.png 로 캡처
#   key <키코드> | sh <셸명령>
import re, subprocess, sys, time, xml.etree.ElementTree as ET
ADB = '/Users/bbqman/Library/Android/sdk/platform-tools/adb'
OUT = '/Users/bbqman/HermesAgents/ETC/JLPTStudyApp/_workspace/emu'
run = lambda *a: subprocess.run([ADB, *a], capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
    xml = run('exec-out', 'cat', '/sdcard/ui.xml')
    root = ET.fromstring(xml[xml.index('<'):])
    out = []
    for n in root.iter('node'):
        t, d = n.get('text') or '', n.get('content-desc') or ''
        b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
        out.append((t, d, b, n.get('clickable') == 'true'))
    return out

cmd, args = sys.argv[1], sys.argv[2:]
if cmd == 'dump':
    for t, d, b, c in nodes():
        if t or d: print(('* ' if c else '  ') + repr(t), repr(d), b)
elif cmd == 'tap':
    want, k = args[0], int(args[1]) if len(args) > 1 else 0
    ns = nodes()
    hit = [n for n in ns if want in (n[0], n[1])] or [n for n in ns if want in n[0] or want in n[1]]
    if len(hit) <= k: sys.exit(f'없음: {want}')
    b = hit[k][2]; run('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(1.2)
    print('tap', want, b)
elif cmd == 'shot':
    time.sleep(0.8)
    open(f'{OUT}/{args[0]}.png', 'wb').write(subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True).stdout)
    print('shot', args[0])
elif cmd == 'key':
    run('shell', 'input', 'keyevent', args[0]); time.sleep(1)
elif cmd == 'sh':
    print(run('shell', *args))
