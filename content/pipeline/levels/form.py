"""form 어댑터: 표기·읽기 정확 일치, 정확히 한 항목일 때만 채택(6.3)."""


def candidates(row, idx):
    if row.kanji:
        return list(idx.by_pair.get((row.kanji, row.kana), []))
    out = []
    for seq in idx.by_reb.get(row.kana, []):
        e = idx.by_seq[seq]
        if not e["keb"] or any(s["uk"] for s in e["senses"]):
            out.append(seq)
    return out


def match(row, idx):
    """(채택한 seq 또는 None, 후보 seq 목록)"""
    c = sorted(set(candidates(row, idx)))
    return (c[0] if len(c) == 1 else None), c
