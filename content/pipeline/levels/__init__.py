"""급수 목록 → JMdict 매핑. 어댑터는 모두 같은 LevelRow를 다룬다(6.3)."""
import csv
import re
from typing import NamedTuple, Optional

from levels import form, seq
from pl_common import Fail

ADAPTERS = {"seq": seq, "form": form}
# 급수 목록 자료 → 어댑터. 새 목록을 확정하면 한 줄을 더한다.
LEVEL_SOURCES = {"S-06": "seq", "S-05": "form", "S-09": "form"}


class LevelRow(NamedTuple):
    level: int
    row_no: int
    jmdict_seq: Optional[int]
    kanji: Optional[str]
    kana: str


class Index:
    def __init__(self, entries):
        self.by_seq = {e["seq"]: e for e in entries}
        self.by_pair, self.by_reb = {}, {}
        for e in self.by_seq.values():
            for r in e["reb"]:
                self.by_reb.setdefault(r, []).append(e["seq"])
                for k in e["keb"]:
                    self.by_pair.setdefault((k, r), []).append(e["seq"])


def read_s06(raw_dir, source):
    """S-06 CSV(original_data/n*.csv) → LevelRow. 급수는 파일명, row_no는 데이터 줄 번호(1부터)."""
    rows = []
    for f in source["files"]:
        m = re.search(r"n([1-5])\.csv$", f["path"])
        if not m:
            continue
        level = int(m.group(1))
        with open(raw_dir / source["id"] / f["path"], newline="", encoding="utf-8-sig") as fh:
            for n, r in enumerate(csv.DictReader(fh), 1):
                s = (r.get("jmdict_seq") or "").strip()
                if s and not s.isdigit():
                    raise Fail(f"FAIL: levels {f['path']}:{n + 1} jmdict_seq not a number: {s}")
                rows.append(LevelRow(level, n, int(s) if s else None, (r.get("kanji") or "").strip() or None, (r.get("kana") or "").strip()))
    return rows


READERS = {"S-06": read_s06}


def to_tsv(rows):
    lines = ["level\trow_no\tjmdict_seq\tkanji\tkana"]
    for r in sorted(rows, key=lambda r: (r.level, r.row_no)):
        lines.append("\t".join([str(r.level), str(r.row_no), "" if r.jmdict_seq is None else str(r.jmdict_seq), r.kanji or "", r.kana]))
    return "\n".join(lines) + "\n"


def read_tsv(path):
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines()[1:]:
        if line:
            lv, no, sq, kj, kn = line.split("\t")
            rows.append(LevelRow(int(lv), int(no), int(sq) if sq else None, kj or None, kn))
    return rows


def read_overrides(path, level_source):
    """level-overrides.tsv: levelSource, level, row_no, jmdict_seq 또는 av-…, 사유 → {(level,row_no): target}"""
    out = {}
    if not path.exists():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        c = line.split("\t")
        if c[0] == "levelSource" or c[0] != level_source:
            continue
        if len(c) < 4 or not c[3].strip():
            raise Fail(f"FAIL: level-overrides bad line: {line}")
        t = c[3].strip()
        if not (c[1].isdigit() and c[2].isdigit() and (t.isdigit() or t.startswith("av-"))):
            raise Fail(f"FAIL: level-overrides bad line: {line}")
        out[(int(c[1]), int(c[2]))] = t
    return out


def wanted(rows, overrides):
    """JMdict 스트리밍 때 남길 항목의 조건: (seq 집합, kana 집합)"""
    seqs = {r.jmdict_seq for r in rows if r.jmdict_seq is not None}
    seqs |= {int(t) for t in overrides.values() if t.isdigit()}
    return seqs, {r.kana for r in rows}


def map_levels(rows, idx, adapter, overrides):
    """→ (vocab {id: (가장 쉬운 급수, 그 급수 안 row_no)}, unmatched [(row_no, level, kanji, kana, 후보들)], 후보 seq 집합)"""
    mod = ADAPTERS[adapter]
    vocab, unmatched, keep = {}, [], set()
    for r in sorted(rows, key=lambda r: (r.level, r.row_no)):
        target = overrides.get((r.level, r.row_no))
        if target is not None:
            vid = target if target.startswith("av-") else f"jm:{int(target)}"
            if vid.startswith("jm:"):
                keep.add(int(target))
        else:
            hit, cands = mod.match(r, idx)
            keep.update(cands)
            if hit is None:
                unmatched.append((r.row_no, r.level, r.kanji or "", r.kana, ",".join(map(str, cands))))
                continue
            vid = f"jm:{hit}"
        cur = vocab.get(vid)
        if cur is None or r.level > cur[0] or (r.level == cur[0] and r.row_no < cur[1]):
            vocab[vid] = (r.level, r.row_no)  # 같은 항목이 여러 급수면 가장 쉬운 급수(큰 수)
    return vocab, unmatched, keep
