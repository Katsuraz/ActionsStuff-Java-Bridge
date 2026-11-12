"""Tests for the standalone reverse-engineering pipeline."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import reverse_engineer as rea


class ReverseEngineerTests(unittest.TestCase):
    def test_fixture_and_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / "example.jar"
            results = Path(tmp) / "results"
            rea.fixture(jar)
            summary = rea.inspect(jar, results)
            self.assertEqual(summary["class_count"], 1)
            self.assertEqual(summary["loader_manifests"]["fabric.mod.json"]["id"], "rea-ci-fixture")
            self.assertIn("ExampleMod.class", (results / "classes.txt").read_text())
            self.assertTrue((results / "report.md").exists())
            self.assertEqual(json.loads((results / "metadata.json").read_text())["sha256"], summary["sha256"])

    def test_invalid_jar_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "invalid.jar"
            path.write_text("not a jar")
            with self.assertRaises(ValueError):
                rea.inspect(path, Path(tmp) / "out")

    def test_download_host_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                rea.download("http://localhost:8080/test.jar", Path(tmp) / "download.jar")
            with self.assertRaises(ValueError):
                rea.download("https://example.com/test.jar", Path(tmp) / "download.jar")

    def test_detects_mixin_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / "mixin.jar"
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("fabric.mod.json", '{"id":"mixin-test","version":"1.0"}')
                z.writestr("example.mixins.json", '{"required":true,"mixins":[]}')
            result = rea.inspect(jar, Path(tmp) / "output")
            self.assertEqual(result["mixin_json_files"], ["example.mixins.json"])


if __name__ == "__main__":
    unittest.main()
