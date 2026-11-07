#!/usr/bin/env python3
"""Rebuild malformed converted GeckoLib model hierarchies in a private test JAR.

All original model assets remain local. This script contains no third-party
geometry, textures, or other redistributed pack content.

Usage:
  python3 repair_private_geometry_26_3.py input.jar output.jar

Use together with the separately compiled 26.3 AvatarRenderState source class
to reproduce the private Stage 8 player render-state port.
"""
import collections
import json
import sys
import zipfile
from pathlib import Path


def fix_model(data):
    obj = json.loads(data)
    events = collections.Counter()
    for geo in obj.get("minecraft:geometry", []):
        bones = geo.get("bones", [])
        existing = {}
        renamed = {}
        for i, bone in enumerate(bones):
            name = bone.get("name")
            if not name:
                continue
            if name not in existing:
                existing[name] = i
            else:
                candidate = f"{name}__ans26_mesh_{i}"
                assert candidate not in existing
                bone["name"] = candidate
                existing[candidate] = i
                renamed[(name, i)] = candidate
                events["duplicate-name"] += 1

        for (prior, start), replacement in renamed.items():
            for later in bones[start+1:]:
                if later.get("parent") == prior:
                    later["parent"] = replacement
                    events["duplicate-child-reparent"] += 1

        all_names = {b.get("name") for b in bones}
        missing = sorted({b.get("parent") for b in bones
                          if b.get("parent") and b.get("parent") not in all_names})
        for parent in missing:
            # Identity pivot (no cubes): preserves coordinates and parent
            # animation channels instead of detaching children.
            bones.insert(0, {"name": parent, "pivot": [0, 0, 0]})
            events["inert-missing-parent-added"] += 1

        for bone in bones:
            before = bone.get("cubes", [])
            after = [c for c in before
                     if not ("uv" not in c and c.get("size") == [0, 0, 0])]
            removed = len(before) - len(after)
            if removed:
                events["invisible-cube-without-uv-removed"] += removed
                if after:
                    bone["cubes"] = after
                else:
                    bone.pop("cubes", None)

        names = [bone.get("name") for bone in bones]
        assert len(names) == len(set(names)), "duplicate bones remain"
        parents = {bone["name"]: bone.get("parent") for bone in bones}
        for bone in bones:
            seen = set()
            cursor = bone["name"]
            while cursor is not None:
                assert cursor in parents, f"missing parent {cursor}"
                assert cursor not in seen, f"cyclic hierarchy {cursor}"
                seen.add(cursor)
                cursor = parents[cursor]
            for cube in bone.get("cubes", []):
                assert "uv" in cube, "non-empty cube missing UV"

    if not events:
        return None, events
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode(), events


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: repair_private_geometry_26_3.py input.jar output.jar")
    src_path, dst_path = map(Path, sys.argv[1:])
    assert src_path.resolve() != dst_path.resolve()
    totals = collections.Counter()
    files = 0
    with zipfile.ZipFile(src_path) as src, zipfile.ZipFile(dst_path, "w") as dst:
        for info in src.infolist():
            payload = src.read(info.filename)
            if info.filename.startswith("assets/ans/geckolib/models/") and info.filename.endswith(".geo.json"):
                repaired, events = fix_model(payload)
                if repaired is not None:
                    payload = repaired
                    files += 1
                    totals.update(events)
            dst.writestr(info, payload)
    with zipfile.ZipFile(dst_path) as check:
        assert check.testzip() is None, "archive corruption"
    print(f"Patched {files} model files: {dict(totals)}")
    print("Preserved all other JAR entries. Minecraft playability not verified.")


if __name__ == "__main__":
    main()
