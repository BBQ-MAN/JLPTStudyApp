"""seq 어댑터: jmdict_seq 직결. 그 항목의 reb에 kana가 있어야 채택하고, seq가 비면 form으로(6.3)."""
from levels import form


def match(row, idx):
    if row.jmdict_seq is None:
        return form.match(row, idx)
    e = idx.by_seq.get(row.jmdict_seq)
    if e is not None and row.kana in e["reb"]:
        return row.jmdict_seq, [row.jmdict_seq]
    return None, ([row.jmdict_seq] if e is not None else [])
