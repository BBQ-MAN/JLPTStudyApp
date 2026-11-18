"""파이프라인 공통: 경로, 실패 예외, 해시, 매니페스트·설정·스키마 읽기, 일본어 구간."""
import hashlib
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCHEMA_DIR = "app/schemas/com.jlptstudy.app.data.content.ContentDatabase"


class Fail(Exception):
    """메시지는 그대로 출력하는 'FAIL: <검사> <이유>' 한 줄."""


class Paths:
    def __init__(self, root=REPO):
        r = self.root = Path(root)
        self.manifest = r / "docs/content/manifest.json"
        self.config = r / "content/pipeline/config.json"
        self.raw = r / "content/raw"
        self.work = r / "content/work"
        self.snapshot = r / "content/snapshot"
        self.authored = r / "content/authored"
        self.ids = r / "content/ids"
        self.kogloss = r / "content/ko-gloss"
        self.dist = r / "content/dist"
        self.notices = r / "content/notices.ko.json"
        self.schemas = r / SCHEMA_DIR
        self.assets = r / "app/src/main/assets/content"
        self.fallback = r / "app/src/main/res/values/attribution_fallback.xml"
        self.reports = r / "_workspace"

    def report(self, name, milestone):
        return self.reports / f"03_content-engineer_{milestone}_{name}"


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def jdump(obj):
    """결정적 JSON(키 정렬, 한 줄)."""
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def read_json(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def read_jsonl(p):
    out = []
    for n, line in enumerate(Path(p).read_text(encoding="utf-8").splitlines(), 1):
        if line.strip():
            try:
                out.append(json.loads(line))
                if not isinstance(out[-1], dict):
                    raise ValueError("not an object")
            except ValueError as e:
                raise Fail(f"FAIL: authored {Path(p).name}:{n} invalid json ({e})")
    return out


def write_text(p, text):
    p = Path(p)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(text.encode("utf-8"))  # 줄바꿈 변환 없이 LF


def load_manifest(paths):
    return {s["id"]: s for s in read_json(paths.manifest)}


def load_config(paths):
    c = read_json(paths.config)
    c.setdefault("milestone", "M3")
    return c


def latest_schema(paths):
    """가장 큰 번호의 <버전>.json → (version, dict)."""
    found = [(int(p.stem), p) for p in Path(paths.schemas).glob("*.json") if p.stem.isdigit()]
    if not found:
        raise Fail(f"FAIL: schema no schema json in {paths.schemas}")
    v, p = max(found)
    return v, read_json(p)


def safe_version(v):
    """파일 이름에 쓸 판 문자열."""
    return re.sub(r"[^0-9A-Za-z.\-]+", "_", str(v)).strip("_")


def hira(s):
    """가타카나 U+30A1–U+30F6 → 히라가나(−0x60). ー 등 나머지는 그대로. Kotlin Kana.hira와 같은 정의(quizgen.md 3.2)."""
    return "".join(chr(ord(c) - 0x60) if "\u30a1" <= c <= "\u30f6" else c for c in s)


def snapshot_name(source, kind_ext):
    return f"{source['id']}-{safe_version(source.get('version'))}.{kind_ext}"


# 4.4: 한국어 필드 안 일본어 구간 문자 집합 J
JP_SPAN = re.compile("[぀-ゟ゠-ヿ一-鿿㐀-䶿々〜「」『』]+")
CJK = re.compile("[一-鿿㐀-䶿]")
