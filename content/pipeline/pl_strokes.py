"""KanjiVG(S-03) 파서: main 묶음 SVG에서 kvg:StrokePaths 의 path d 만 획 순서대로 읽는다(9.4, 6.7).
kvg:element·kvg:radical 등 속성과 kvg:StrokeNumbers 그룹은 읽지 않는다."""
import re
import xml.etree.ElementTree as ET
import zipfile

from pl_common import Fail

SVG = "{http://www.w3.org/2000/svg}"
VIEWBOX = "0 0 109 109"
PATH_ID = re.compile(r"kvg:([0-9a-f]{5})-s([0-9]+)")


def svg_name(literal):
    return f"kanji/{ord(literal):05x}.svg"


def parse_strokes(data, literal):
    """SVG 바이트 → 획 순서의 d 문자열 목록."""
    root = ET.fromstring(data)
    vb = root.get("viewBox")
    if vb != VIEWBOX:
        raise Fail(f"FAIL: parse S-03 viewBox {vb if vb is not None else ''} ({literal})")
    code = f"{ord(literal):05x}"
    group = next((g for g in root.iter(SVG + "g") if g.get("id") == f"kvg:StrokePaths_{code}"), None)
    if group is None:
        raise Fail(f"FAIL: parse S-03 {literal} kvg:StrokePaths_{code} missing")
    found = {}
    for p in group.iter(SVG + "path"):
        m = PATH_ID.fullmatch(p.get("id") or "")
        if not m or m.group(1) != code or not p.get("d"):
            raise Fail(f"FAIL: parse S-03 {literal} bad path {p.get('id')}")
        n = int(m.group(2))
        if n in found:
            raise Fail(f"FAIL: parse S-03 {literal} duplicate stroke s{n}")
        found[n] = p.get("d")
    if not found or sorted(found) != list(range(1, len(found) + 1)):
        raise Fail(f"FAIL: parse S-03 {literal} stroke numbers not 1..N ({sorted(found)})")
    return [found[n] for n in sorted(found)]


def read_kanjivg(zip_path, literals):
    """zip에서 literals 의 획 경로를 읽는다. zip에 없는 한자는 결과에 없다. → [{literal, paths}] (literal 순)"""
    out = []
    with zipfile.ZipFile(zip_path) as z:
        names = set(z.namelist())
        for c in sorted(literals):
            if svg_name(c) in names:
                out.append({"literal": c, "paths": parse_strokes(z.read(svg_name(c)), c)})
    return out
