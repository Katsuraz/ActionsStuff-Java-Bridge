"""Tests for safe chat-triggered GitHub Actions request parsing."""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import analysis_request as task

class ChatRequestTests(unittest.TestCase):
    def test_url_and_mode(self):
        actual = task.validate_request({
            "request_id": "run-123",
            "jar_url": "https://github.com/test/repo/releases/download/v1/mod.jar",
            "gradle_mode": "skip",
        })
        self.assertEqual(actual["gradle_mode"], "skip")

    def test_fixture_request(self):
        actual = task.validate_request({
            "request_id": "smoke-001", "jar_url": "", "gradle_mode": "validate",
        })
        self.assertEqual(actual["jar_url"], "")

    def test_rejects_newline_injection(self):
        with self.assertRaises(ValueError):
            task.validate_request({"request_id": "x", "jar_url": "a\nGRADLE_MODE=build"})

    def test_rejects_localhost_and_private_host(self):
        for url in ("http://localhost/a.jar", "https://127.0.0.1/a.jar", "https://example.org/mod.jar"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                task.validate_request({"request_id": "x", "jar_url": url})

    def test_requires_fresh_commit_change(self):
        event = {"commits": [{"modified": ["README.md"]}]}
        self.assertFalse(task.request_was_changed(event))
        event = {"commits": [{"modified": [".github/rea-request.json"]}]}
        self.assertTrue(task.request_was_changed(event))
        self.assertFalse(task.request_was_changed(event, {"README.md"}))
        self.assertTrue(task.request_was_changed({"commits": []}, {task.REQUEST_PATH}))

    def test_rejects_bad_modes_and_ids(self):
        for data in (
            {"request_id": "x", "gradle_mode": "rm -rf /"},
            {"request_id": "../bad", "gradle_mode": "skip"},
        ):
            with self.assertRaises(ValueError):
                task.validate_request(data)

if __name__ == "__main__":
    unittest.main()
