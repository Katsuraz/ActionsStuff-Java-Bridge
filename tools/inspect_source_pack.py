#!/usr/bin/env python3
"""Inspect a locally-owned Bedrock .mcpack without extracting or redistributing assets."""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, BadZipFile


def inspect_pack(path: Path) -> dict:
    if not path.is_file():
        raise FileNotFoundError(path)
    with path.open("rb") as stream:
        sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    with ZipFile(path) as archive:
        entries = [entry for entry in archive.infolist() if not entry.is_dir()]
        categories = collections.Counter(
            "brarchive" if "/__brarchive/" in "/" + entry.filename
                else entry.filename.split("/", 1)[0]
            for entry in entries
        )
        for entry in entries:
            filename = entry.filename.replace("\\", "/")
            if filename.startswith("/") or ".." in filename.split("/"):
                raise ValueError("Unsafe archive entry name")
        manifest = None
        if "manifest.json" in archive.namelist():
            with archive.open("manifest.json") as stream:
                manifest = json.loads(stream.read(1_000_000).decode("utf-8-sig"))
    return {
        "filename": path.name,
        "sha256": sha256,
        "entry_count": len(entries),
        "categories": dict(sorted(categories.items())),
        "pack_manifest_name": (manifest or {}).get("header", {}).get("name"),
        "note": "Inventory only. No original materials are exported or uploaded."
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Local authorized .mcpack file")
    parser.add_argument("--output", type=Path, help="Optional private JSON report path")
    args = parser.parse_args()
    try:
        result = inspect_pack(args.source)
    except (OSError, BadZipFile, ValueError, json.JSONDecodeError) as exc:
        parser.exit(1, f"Cannot inspect pack: {exc}\n")
    data = json.dumps(result, indent=2, ensure_ascii=False)
    if args.output:
        args.output.write_text(data + "\n", encoding="utf-8")
    print(data)


if __name__ == "__main__":
    main()
