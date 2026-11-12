#!/usr/bin/env python3
"""Read a GitHub commit-triggered static analysis request safely."""
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlsplit

from reverse_engineer import ALLOWED_HOSTS

REQUEST_PATH = ".github/rea-request.json"
VALID_MODES = {"validate", "build", "skip"}

def request_was_changed(event: dict, changed_files: set[str] | None = None) -> bool:
    """Use the checkout diff; some GitHub push payloads omit commit file lists."""
    if changed_files is not None:
        return REQUEST_PATH in changed_files
    return any(
        REQUEST_PATH in commit.get(change, [])
        for commit in event.get("commits", [])
        for change in ("added", "modified")
    )

def validate_request(data: dict) -> dict:
    if not isinstance(data, dict):
        raise ValueError("Analysis request must be a JSON object")
    url = data.get("jar_url", "")
    mode = data.get("gradle_mode", "validate")
    request_id = data.get("request_id", "")
    if not isinstance(url, str) or len(url) > 2000 or any(ch in url for ch in "\r\n"):
        raise ValueError("jar_url must be a single-line URL under 2000 characters")
    if url:
        parsed = urlsplit(url)
        if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS:
            raise ValueError("jar_url must use a permitted HTTPS artifact host")
        if parsed.username or parsed.password or parsed.fragment:
            raise ValueError("jar_url must not have credentials or fragments")
    if mode not in VALID_MODES:
        raise ValueError("gradle_mode must be validate, build, or skip")
    if not isinstance(request_id, str) or not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", request_id):
        raise ValueError("request_id must contain 1-80 safe characters")
    return {"jar_url": url, "gradle_mode": mode, "request_id": request_id}

def main() -> None:
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    changed_files = set(subprocess.check_output(
        ["git", "diff", "--name-only", "HEAD^", "HEAD"], text=True
    ).splitlines())
    if not request_was_changed(event, changed_files):
        print("No request file change in this commit; using standard workflow inputs.")
        return
    request = validate_request(json.loads(Path(REQUEST_PATH).read_text(encoding="utf-8")))
    with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as output:
        output.write("JAR_URL=" + request["jar_url"] + "\n")
        output.write("GRADLE_MODE=" + request["gradle_mode"] + "\n")
    result_dir = Path(os.environ["OUTPUT_DIR"])
    result_dir.mkdir(parents=True, exist_ok=True)
    (result_dir / "request.json").write_text(json.dumps(request, indent=2) + "\n", encoding="utf-8")
    print("Using committed analysis request", request["request_id"])

if __name__ == "__main__":
    main()
