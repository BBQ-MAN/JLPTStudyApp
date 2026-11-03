"""테스트 공통: 합성 원본·임시 저장소 만들기."""
import csv
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from pl_common import REPO, Paths, sha256_bytes

JMDICT = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE JMdict [
<!ENTITY n "noun (common) (futsuumeishi)">
<!ENTITY adv "adverb (fukushi)">
<!ENTITY vs "noun or participle which takes the aux. verb suru">
<!ENTITY uk "word usually written using kana alone">
]>
<JMdict>
<entry><ent_seq>1000001</ent_seq><k_ele><keb>水</keb></k_ele><r_ele><reb>みず</reb></r_ele>
<sense><pos>&n;</pos><gloss>water</gloss><gloss xml:lang="ger">Wasser</gloss><gloss xml:lang="fre">eau</gloss><lsource xml:lang="ger">Wasser</lsource></sense>
<sense><gloss>fluid</gloss><gloss xml:lang="dut">vloeistof</gloss></sense></entry>
<entry><ent_seq>1000002</ent_seq><k_ele><keb>時々</keb></k_ele><r_ele><reb>ときどき</reb></r_ele>
<sense><pos>&adv;</pos><pos>&n;</pos><gloss>sometimes</gloss></sense></entry>
<entry><ent_seq>1000003</ent_seq><k_ele><keb>犬</keb></k_ele><r_ele><reb>いぬ</reb></r_ele>
<sense><pos>&n;</pos><gloss>dog</gloss></sense></entry>
<entry><ent_seq>1000004</ent_seq><k_ele><keb>端</keb></k_ele><r_ele><reb>はし</reb></r_ele><sense><pos>&n;</pos><gloss>edge</gloss></sense></entry>
<entry><ent_seq>1000005</ent_seq><k_ele><keb>端</keb></k_ele><r_ele><reb>はし</reb></r_ele><sense><pos>&n;</pos><gloss>end</gloss></sense></entry>
<entry><ent_seq>1000006</ent_seq><r_ele><reb>ねえ</reb></r_ele><sense><pos>&n;</pos><gloss>hey</gloss></sense></entry>
<entry><ent_seq>1000007</ent_seq><k_ele><keb>雨</keb></k_ele><r_ele><reb>あめ</reb></r_ele><sense><pos>&n;</pos><gloss>rain</gloss></sense></entry>
<entry><ent_seq>1000008</ent_seq><k_ele><keb>猫</keb></k_ele><r_ele><reb>ねこ</reb></r_ele><sense><pos>&n;</pos><gloss>cat</gloss></sense></entry>
<entry><ent_seq>1000009</ent_seq><k_ele><keb>鬱</keb></k_ele><r_ele><reb>うつ</reb></r_ele><sense><pos>&n;</pos><gloss>gloom</gloss></sense></entry>
<entry><ent_seq>1000010</ent_seq><k_ele><keb>日本人</keb></k_ele><r_ele><reb>にほんじん</reb></r_ele><sense><pos>&n;</pos><gloss>Japanese person</gloss></sense></entry>
<entry><ent_seq>1000011</ent_seq><k_ele><keb>有難う</keb></k_ele><r_ele><reb>ありがとう</reb></r_ele><sense><pos>&n;</pos><misc>&uk;</misc><gloss>thanks</gloss></sense></entry>
<entry><ent_seq>1000012</ent_seq><r_ele><reb>ありがとう</reb></r_ele><sense><pos>&n;</pos><gloss>thank you</gloss></sense></entry>
<entry><ent_seq>1000099</ent_seq><k_ele><keb>無関係</keb></k_ele><r_ele><reb>むかんけい</reb></r_ele><sense><pos>&n;</pos><gloss>unrelated</gloss></sense></entry>
</JMdict>
"""

KANJIDIC = """<?xml version="1.0" encoding="UTF-8"?>
<kanjidic2><header><file_version>4</file_version></header>
{chars}
</kanjidic2>
"""

CHAR = """<character><literal>{lit}</literal><codepoint><cp_value cp_type="ucs">x</cp_value></codepoint>
<dic_number><dic_ref dr_type="nelson_c">99</dic_ref></dic_number>
<query_code><q_code qc_type="skip">SKIPCODE-{lit}</q_code></query_code>
<misc><grade>1</grade><stroke_count>{sc}</stroke_count><stroke_count>9</stroke_count><freq>{freq}</freq><jlpt>4</jlpt></misc>
<reading_meaning><rmgroup><reading r_type="pinyin">pinyin1</reading><reading r_type="vietnam">viet1</reading><reading r_type="korean_r">kor_r</reading>
<reading r_type="korean_h">{ko}</reading><reading r_type="ja_on">{on}</reading><reading r_type="ja_kun">{kun}</reading>
<meaning>ENGMEANING-{lit}</meaning><meaning m_lang="fr">FRMEANING-{lit}</meaning></rmgroup><nanori>NANORI</nanori></reading_meaning></character>"""


def kanjidic(chars=("水", "時", "犬", "端", "雨", "猫", "日", "本", "人", "々")):
    spec = {"水": (4, 100, "수", "スイ", "みず"), "時": (10, 200, "시", "ジ", "とき"), "犬": (4, 900, "견", "ケン", "いぬ"),
            "端": (14, 700, "단", "タン", "はし"), "雨": (8, 500, "우", "ウ", "あめ"), "猫": (11, 800, "묘", "ビョウ", "ねこ"),
            "日": (4, 1, "일", "ニチ", "ひ"), "本": (5, 2, "본", "ホン", "もと"), "人": (2, 3, "인", "ジン", "ひと"), "々": (3, 5, "x", "x", "x")}
    return KANJIDIC.format(chars="\n".join(CHAR.format(lit=c, sc=spec[c][0], freq=spec[c][1], ko=spec[c][2], on=spec[c][3], kun=spec[c][4]) for c in chars))


def s06_csv(rows):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(["jmdict_seq", "kana", "kanji", "waller_definition"])
    w.writerows(rows)
    return buf.getvalue()


# 모두 맞는 목록(release 흐름용)
N5_ROWS = [(1000001, "みず", "水", "x"), ("", "いぬ", "犬", "x"), (1000002, "ときどき", "時々", "x"), ("", "ねえ", "", "x")]
N4_ROWS = [(1000008, "ねこ", "猫", "x"), (1000010, "にほんじん", "日本人", "x")]

BASE_SRC = {"license": "CC BY-SA 4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/", "modifications": "mod",
            "attribution": "attr", "homepage": "https://h", "status": "confirmed", "shareAlike": True}


def synthetic_sources():
    """(manifest 항목 목록, {URL: 바이트}) — S-01 JMdict, S-02 KANJIDIC2, S-06 급수 목록(파일 2개)"""
    blobs = {
        "https://example.test/JMdict_e": JMDICT.encode(), "https://example.test/kanjidic2.xml": kanjidic().encode(),
        "https://example.test/n5.csv": s06_csv(N5_ROWS).encode(), "https://example.test/n4.csv": s06_csv(N4_ROWS).encode(),
    }
    h = lambda u: sha256_bytes(blobs[u])
    man = [
        dict(BASE_SRC, id="S-01", name="JMdict", version="2026-11-01", downloadUrl="https://example.test/JMdict_e", sha256=h("https://example.test/JMdict_e"),
             pinning={"method": "snapshot", "pinnedCopy": "content/snapshot/"}, links=["https://l1", "https://l2"], extraCredits=[], attribution="EDRDG text"),
        dict(BASE_SRC, id="S-02", name="KANJIDIC2", version="2026-305 (2026-11-01)", downloadUrl="https://example.test/kanjidic2.xml", sha256=h("https://example.test/kanjidic2.xml"),
             pinning={"method": "snapshot", "pinnedCopy": "content/snapshot/"}, links=["https://l1"], extraCredits=[{"text": "한국어 음: 크레딧", "url": None}], attribution="EDRDG text"),
        dict(BASE_SRC, id="S-06", name="level list", version="b062d4e (2025-08-26)", downloadUrl=None, sha256=None,
             files=[{"path": "original_data/n5.csv", "url": "https://example.test/n5.csv", "sha256": h("https://example.test/n5.csv")},
                    {"path": "original_data/n4.csv", "url": "https://example.test/n4.csv", "sha256": h("https://example.test/n4.csv")}],
             pinning={"method": "url", "pinnedCopy": None}, links=[], extraCredits=[{"text": "원 목록", "url": "http://t"}]),
    ]
    return man, blobs


def make_opener(blobs, calls=None):
    def opener(url):
        if calls is not None:
            calls.append(url)
        return blobs[url]
    return opener


class TempRoot(unittest.TestCase):
    """임시 저장소 뼈대: 실제 스키마 JSON과 고지 틀을 복사한다."""

    def make_root(self, manifest, config, authored_sample=False):
        d = Path(tempfile.mkdtemp(prefix="jlptpl-"))
        self.addCleanup(shutil.rmtree, d, True)
        p = Paths(d)
        for rel in ("app/schemas/com.jlptstudy.app.data.content.ContentDatabase/2.json", "content/notices.ko.json"):
            (d / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(REPO / rel, d / rel)
        (d / "docs/content").mkdir(parents=True)
        (d / "docs/content/manifest.json").write_text(json.dumps(manifest, ensure_ascii=False))
        (d / "content/pipeline").mkdir(parents=True)
        (d / "content/pipeline/config.json").write_text(json.dumps(config))
        if authored_sample:
            shutil.copytree(REPO / "content/authored/sample", d / "content/authored/sample")
        return p

    def sample_root(self):
        real = json.loads((REPO / "docs/content/manifest.json").read_text())
        return self.make_root(real, {"kind": "sample", "levelSource": None, "levels": [5], "milestone": "T"}, authored_sample=True)
