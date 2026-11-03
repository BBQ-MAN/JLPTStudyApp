"""콘텐츠 파이프라인 진입점.
  build.py refresh-snapshot [--refresh <id>]...   fetch → parse → levels → snapshot (네트워크 필요)
  build.py                                       export-gloss-input → ko-gloss → authored → build → check (네트워크·content/raw 불필요)
  build.py <단계>                                  export-gloss-input | ko-gloss | authored | build | check
  build.py review-sample --target T --ratio R --seed S | review-sheet --target T --ids F
           | review-apply --target T --sheet F.tsv --date YYYY-MM-DD [--batch-sampled]   검수 도구(architecture 6.8)
  build.py ko-gloss --check                        content/ko-gloss 형식·허용 값 + en-gloss export와 vocabId·enHash 대조
실행은 python3 -I content/pipeline/build.py."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # python3 -I 는 스크립트 폴더를 경로에 넣지 않는다

import check as chk
import pl_attr as at
import pl_authored as au
import pl_db as db
import pl_review as rv
from pl_common import Fail, Paths, load_config, load_manifest
from pl_snapshot import refresh_snapshot

STAGES = ["export-gloss-input", "ko-gloss", "authored", "build", "check"]


def need_release(config, manifest):
    """release 빌드는 levelSource(D-06)가 정해지고 confirmed여야 한다."""
    if config["kind"] != "release":
        return
    ls = config.get("levelSource")
    if not ls:
        raise Fail("FAIL: build kind=release requires levelSource (config.json levelSource is null)")
    if manifest.get(ls, {}).get("status") != "confirmed":
        raise Fail(f"FAIL: build levelSource {ls} is not confirmed")


def stage_export(p, c, m, out):
    if c["kind"] == "release" or c.get("levelSource"):  # M8: 릴리스 DB 전에도 스냅샷에서 N5 입력을 낸다
        db.export_gloss_input(p, c, m, out)
    else:
        out("export-gloss-input: skipped (kind=sample reads content/authored/sample only)")


def stage_ko_gloss(p, c, m, out):
    if c["kind"] != "release":
        return out("ko-gloss: skipped (kind=sample)")
    recs = db.load_ko_gloss(p, c)
    rows = [r for v, rec in recs.items() for r in au.jp_span_rows("ko-gloss", v, rec["meanings"])]
    au.report_spans(p, c, "ko-gloss", rows, out)


def stage_authored(p, c, m, out):
    if c["kind"] == "sample":
        ds = db.sample_dataset(p, c)
    else:
        avs, _ = au.load_authored(p.authored / "vocab", "*.jsonl", au.vocab_problems)
        ds = db.Dataset("release")
        ds.spans = [row for r in avs for row in au.jp_span_rows("authored", r["id"], r["meanings"])]
    au.report_spans(p, c, "authored", [s for s in ds.spans if s[0] == "authored"], out)
    lines = rv.similarity_lines(p)
    for l in lines:
        out(l)
    if any(l.startswith("FAIL: ") for l in lines):
        raise Fail("\n".join(l for l in lines if l.startswith("FAIL: ")))


def stage_build(p, c, m, out):
    need_release(c, m)
    ds = db.sample_dataset(p, c) if c["kind"] == "sample" else db.release_dataset(p, c, m)
    version, dbfile = db.build_db(p, c, ds, out)
    attr = at.write_attribution(p, m, dbfile, out)
    z = at.write_dist(p, attr, dbfile)
    out(f"build: attribution.json, attribution_fallback.xml, {z.relative_to(p.root)}")


def stage_check(p, c, m, out):
    fails = chk.run_checks(p)
    if fails:
        raise Fail("\n".join(fails))
    out("check: OK")


RUN = {"export-gloss-input": stage_export, "ko-gloss": stage_ko_gloss, "authored": stage_authored, "build": stage_build, "check": stage_check}


def review_cmd(p, argv, out):
    cmd, rest = argv[0], argv[1:]
    opts = {}
    for i, a in enumerate(rest):
        if a.startswith("--") and a != "--batch-sampled":
            if i + 1 >= len(rest):
                raise Fail(f"FAIL: review usage {a} needs a value")
            opts[a[2:]] = rest[i + 1]
    need = {"review-sample": ("target", "ratio", "seed"), "review-sheet": ("target", "ids"),
            "review-apply": ("target", "sheet", "date")}[cmd]
    miss = [k for k in need if k not in opts]
    if miss:
        raise Fail(f"FAIL: review usage {cmd} needs --{miss[0]}")
    if cmd == "review-sample":
        try:
            ratio, seed = float(opts["ratio"]), int(opts["seed"])
        except ValueError:
            raise Fail("FAIL: review usage --ratio float, --seed int")
        out(f"review-sample: {rv.sample(p, opts['target'], ratio, seed).relative_to(p.root)}")
    elif cmd == "review-sheet":
        out("review-sheet: " + " ".join(str(f) for f in rv.sheet(p, opts["target"], opts["ids"])))
    else:
        n, k = rv.apply(p, opts["target"], opts["sheet"], opts["date"], "--batch-sampled" in rest)
        out(f"review-apply: {n} rows updated, {k} fix rows listed")


def main(argv, paths=None, out=print, opener=None):
    p = paths or Paths()
    try:
        c, m = load_config(p), load_manifest(p)
        if argv[:1] == ["refresh-snapshot"]:
            if argv[-1:] == ["--refresh"]:
                raise Fail("FAIL: usage --refresh needs a source id")
            refresh = [argv[i + 1] for i, a in enumerate(argv) if a == "--refresh"]
            refresh_snapshot(p, c, m, refresh=refresh, opener=opener, out=out)
            return 0
        if argv[:1] and argv[0] in ("review-sample", "review-sheet", "review-apply"):
            review_cmd(p, argv, out)
            return 0
        if argv == ["ko-gloss", "--check"]:
            db.check_ko_gloss(p, c, out)
            return 0
        stages = argv or STAGES
        for s in stages:
            if s not in RUN:
                raise Fail(f"FAIL: usage unknown stage {s}")
        need_release(c, m)
        for s in stages:
            RUN[s](p, c, m, out)
        return 0
    except Fail as e:
        print(e, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
