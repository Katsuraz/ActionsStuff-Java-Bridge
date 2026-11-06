import importlib.util
import json
import pathlib
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("inspect", ROOT / "tools" / "inspect_source_pack.py")
INSPECT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INSPECT)


class SourcePackTests(unittest.TestCase):
    def test_works_with_synthetic_source_pack(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "synthetic.mcpack"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("manifest.json", json.dumps({"header": {"name": "Synthetic"}}))
                archive.writestr("__brarchive/animations.brarchive", b"fake")
                archive.writestr("sounds/sample.ogg", b"fake")
            result = INSPECT.inspect_pack(path)
            self.assertEqual(result["entry_count"], 3)
            self.assertEqual(result["categories"]["brarchive"], 1)
            self.assertEqual(result["pack_manifest_name"], "Synthetic")
            self.assertEqual(len(result["sha256"]), 64)

    def test_rejects_unsafe_path(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "unsafe.mcpack"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("../file.json", "{}")
            with self.assertRaises(ValueError):
                INSPECT.inspect_pack(path)


if __name__ == "__main__":
    unittest.main()
