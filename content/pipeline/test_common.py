import unittest

from pl_common import jdump


class JdumpTest(unittest.TestCase):
    def test_deterministic_utf8_no_escape(self):
        a, b = {"b": "한글", "a": ["日本語", 1]}, {"a": ["日本語", 1], "b": "한글"}
        self.assertEqual(jdump(a).encode(), jdump(b).encode())
        self.assertEqual(jdump(a), '{"a":["日本語",1],"b":"한글"}')
        self.assertNotIn("\\u", jdump(a))
