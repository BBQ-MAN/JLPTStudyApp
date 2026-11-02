import tempfile
import unittest
from pathlib import Path

from pl_common import Fail, sha256_bytes
from pl_fetch import fetch

H = lambda b: sha256_bytes(b)


def src(**kw):
    s = {"id": "S-X", "status": "confirmed", "downloadUrl": "https://orig.test/a.csv", "sha256": H(b"data"), "pinning": {"method": "url", "pinnedCopy": None}}
    s.update(kw)
    return s


class FetchTest(unittest.TestCase):
    def setUp(self):
        self.raw = Path(tempfile.mkdtemp()) / "raw"
        self.calls, self.log = [], []
        self.blobs = {"https://orig.test/a.csv": b"data"}
        self.op = lambda u: (self.calls.append(u), self.blobs[u])[1]

    def run_fetch(self, s, **kw):
        return fetch(s, self.raw, opener=self.op, out=self.log.append, **kw)

    def fail(self, s, text):
        with self.assertRaises(Fail) as cm:
            self.run_fetch(s)
        self.assertIn(text, str(cm.exception))
        return str(cm.exception)

    def test_not_confirmed_does_not_download(self):
        self.assertEqual(self.fail(src(status="unconfirmed"), "FAIL: fetch S-X not confirmed"), "FAIL: fetch S-X not confirmed")
        self.assertEqual(self.calls, [])

    def test_ok_writes_file(self):
        self.assertEqual(self.run_fetch(src()), {"a.csv": H(b"data")})
        self.assertEqual((self.raw / "S-X/a.csv").read_bytes(), b"data")

    def test_mismatch_stops_and_writes_nothing(self):
        self.fail(src(sha256="0" * 64), "sha256 mismatch")
        self.assertFalse((self.raw / "S-X").exists())

    def test_null_sha_prints_computed_and_leaves_manifest(self):
        s = src(sha256=None)
        self.run_fetch(s)
        self.assertIn(H(b"data"), " ".join(self.log))
        self.assertIsNone(s["sha256"])

    def test_multi_file_each_checked_and_one_mismatch_stops(self):
        files = []
        for n in "abc":
            self.blobs[f"https://orig.test/{n}"] = n.encode()
            files.append({"path": f"d/{n}.csv", "url": f"https://orig.test/{n}", "sha256": H(n.encode())})
        s = src(downloadUrl=None, sha256=None, files=files)
        self.assertEqual(len(self.run_fetch(s)), 3)
        self.raw = Path(tempfile.mkdtemp()) / "raw"
        files[2]["sha256"] = "f" * 64
        self.fail(s, "sha256 mismatch d/c.csv")
        self.assertFalse((self.raw / "S-X/d/a.csv").exists())  # 하나라도 다르면 아무것도 남기지 않는다

    def test_html_response(self):
        self.blobs["https://orig.test/a.csv"] = b"  <!DOCTYPE html><html>index</html>"
        self.fail(src(sha256=None), "FAIL: fetch S-X not a data file")

    def test_existing_raw_file_is_not_downloaded_but_hash_checked(self):
        (self.raw / "S-X").mkdir(parents=True)
        (self.raw / "S-X/a.csv").write_bytes(b"data")
        self.run_fetch(src())
        self.assertEqual(self.calls, [])
        (self.raw / "S-X/a.csv").write_bytes(b"changed")
        self.fail(src(), "sha256 mismatch")
        self.run_fetch(src(), refresh=True)  # --refresh 일 때만 다시 받는다
        self.assertEqual(len(self.calls), 1)

    def test_pinning_method_missing(self):
        self.assertEqual(self.fail(src(pinning={}), "pinning method missing"), "FAIL: fetch S-X pinning method missing")
        s = src()
        del s["pinning"]
        self.fail(s, "pinning method missing")

    def test_archive_uses_pinned_copy_not_download_url(self):
        self.blobs["https://archive.test/S-X-2026/a.csv"] = b"data"
        self.run_fetch(src(pinning={"method": "archive", "pinnedCopy": "https://archive.test/S-X-2026/a.csv"}))
        self.assertEqual(self.calls, ["https://archive.test/S-X-2026/a.csv"])

    def test_archive_directory_copy_joins_file_path(self):
        self.blobs["https://archive.test/v1/d/a.csv"] = b"a"
        s = src(downloadUrl=None, sha256=None, files=[{"path": "d/a.csv", "url": "https://orig.test/zzz", "sha256": H(b"a")}],
                pinning={"method": "archive", "pinnedCopy": "https://archive.test/v1/"})
        self.run_fetch(s)
        self.assertEqual(self.calls, ["https://archive.test/v1/d/a.csv"])

    def test_file_url_and_default_opener(self):
        d = Path(tempfile.mkdtemp())
        (d / "x.bin").write_bytes(b"local")
        s = src(downloadUrl=(d / "x.bin").as_uri(), sha256=H(b"local"))
        self.assertEqual(fetch(s, self.raw, out=self.log.append), {"x.bin": H(b"local")})
