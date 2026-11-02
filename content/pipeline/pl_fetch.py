"""fetch 단계(6.2, 6.7): confirmed 자료만, 체크섬 대조, 매니페스트는 고치지 않는다."""
import posixpath
import urllib.parse
import urllib.request

from pl_common import Fail, sha256_bytes


def default_opener(url):
    with urllib.request.urlopen(url, timeout=120) as r:  # file:// 도 된다(테스트)
        return r.read()


def _is_html(data):
    head = data[:512].lstrip().lower()
    return head.startswith((b"<!doctype html", b"<html"))


def file_list(src):
    """자료의 (path, url, sha256) 목록. files가 없으면 downloadUrl 한 개."""
    if src.get("files"):
        return [(f["path"], f.get("url"), f.get("sha256")) for f in src["files"]]
    url = src.get("downloadUrl")
    if not url:
        raise Fail(f"FAIL: fetch {src['id']} no download url")
    return [(posixpath.basename(urllib.parse.urlparse(url).path), url, src.get("sha256"))]


def source_url(src, path, url):
    pin = src.get("pinning") or {}
    if pin.get("method") != "archive":
        return url
    copy = pin.get("pinnedCopy")
    if not copy:
        raise Fail(f"FAIL: fetch {src['id']} pinnedCopy missing")
    if copy.endswith("/"):
        return copy + path
    if len(file_list(src)) > 1:
        raise Fail(f"FAIL: fetch {src['id']} pinnedCopy must end with / for multiple files")
    return copy


def fetch(src, raw_dir, refresh=False, opener=default_opener, out=print):
    """받은(또는 이미 있는) 파일 {path: 계산한 sha256}을 돌려준다."""
    sid = src["id"]
    if src.get("status") != "confirmed":
        raise Fail(f"FAIL: fetch {sid} not confirmed")
    if not (src.get("pinning") or {}).get("method"):
        raise Fail(f"FAIL: fetch {sid} pinning method missing")
    result, pending = {}, []
    for path, url, expected in file_list(src):
        dest = raw_dir / sid / path
        if dest.exists() and not refresh:
            data = dest.read_bytes()  # 이미 있으면 받지 않고 해시만 대조
            fresh = False
        else:
            data = opener(source_url(src, path, url))
            fresh = True
            if _is_html(data):
                raise Fail(f"FAIL: fetch {sid} not a data file ({path})")
        got = sha256_bytes(data)
        if expected is None:
            out(f"sha256 {sid} {path} {got} (manifest sha256 is null; manifest not modified - ask content-licensing-analyst to record it)")
        elif got != expected:
            raise Fail(f"FAIL: fetch {sid} sha256 mismatch {path} expected={expected} got={got}")
        if fresh:
            pending.append((dest, data))
        result[path] = got
    for dest, data in pending:  # 전부 통과한 뒤에만 쓴다
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    return result
