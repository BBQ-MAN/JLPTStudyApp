"""JMdict·KANJIDIC2 스트리밍 파서. 쓰는 필드만 읽고 나머지는 결과에 담지 않는다(4.2, 6.2)."""
import gzip
import re
import xml.etree.ElementTree as ET

XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
ENTITY = re.compile(r'<!ENTITY\s+(\S+)\s+"([^"]*)"\s*>')


def open_stream(path):
    """gzip이면 풀어서, 아니면 그대로 연다(확장자가 아니라 머리 바이트로 판단)."""
    f = open(path, "rb")
    magic = f.read(2)
    f.seek(0)
    if magic == b"\x1f\x8b":
        f.close()
        return gzip.open(path, "rb")
    return f


def _dtd_codes(path):
    """DTD의 <!ENTITY n "설명"> → {설명: n}. iterparse는 엔티티를 설명 문장으로 펼치므로 코드를 되찾는다."""
    head = b""
    with open_stream(path) as f:
        while True:
            chunk = f.read(65536)
            if not chunk:
                break
            head += chunk
            if b"]>" in head:
                head = head.split(b"]>", 1)[0]
                break
    return {desc: name for name, desc in ENTITY.findall(head.decode("utf-8", "replace"))}


def parse_jmdict(path, xref=False):
    """항목을 하나씩 낸다: {seq, keb[], reb[], pos[](첫 sense 코드), senses[{uk, gloss[]}]} (영어 gloss가 있는 sense만).
    xref=True일 때만 최상위 "xref"(모든 sense의 <xref> 원문, 나온 순서, 중복 제거)를 더한다 — S-01 스냅샷은 xref=False 결과라 바이트가 그대로다."""
    codes = _dtd_codes(path)
    with open_stream(path) as f:
        for _, e in ET.iterparse(f, events=("end",)):
            if e.tag != "entry":
                continue
            all_senses = e.findall("sense")
            first = all_senses[0] if all_senses else None
            senses = []
            for s in all_senses:
                gloss = [g.text for g in s.findall("gloss") if g.get(XML_LANG) in (None, "eng") and g.text]
                if gloss:
                    uk = any(codes.get((m.text or ""), m.text) == "uk" for m in s.findall("misc"))
                    sense = {"uk": uk, "gloss": gloss}
                    for tag in ("stagk", "stagr"):  # 특정 표기·읽기에만 쓰는 sense
                        v = [t.text for t in s.findall(tag) if t.text]
                        if v:
                            sense[tag] = v
                    senses.append(sense)
            out = {
                "seq": int(e.findtext("ent_seq")),
                "keb": [k.findtext("keb") for k in e.findall("k_ele")],
                "reb": [r.findtext("reb") for r in e.findall("r_ele")],
                "pos": [codes.get(p.text, p.text) for p in first.findall("pos")] if first is not None else [],
                "senses": senses,
            }
            if xref:
                out["xref"] = list(dict.fromkeys(x.text for s in all_senses for x in s.findall("xref") if x.text))
            yield out
            e.clear()


def parse_kanjidic2(path):
    """한자를 하나씩 낸다: {literal, stroke_count, freq, ja_on[], ja_kun[], korean_h[]}."""
    with open_stream(path) as f:
        for _, e in ET.iterparse(f, events=("end",)):
            if e.tag != "character":
                continue
            sc = e.findtext("misc/stroke_count")
            freq = e.findtext("misc/freq")
            read = lambda t: [r.text for r in e.findall(f"reading_meaning/rmgroup/reading[@r_type='{t}']") if r.text]
            yield {
                "literal": e.findtext("literal"),
                "stroke_count": int(sc) if sc else None,
                "freq": int(freq) if freq else None,
                "ja_on": read("ja_on"),
                "ja_kun": read("ja_kun"),
                "korean_h": read("korean_h"),
            }
            e.clear()
