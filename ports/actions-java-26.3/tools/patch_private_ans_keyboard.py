#!/usr/bin/env python3
"""Patch the owner's local, private Actions & Java 26.3 test JAR.

Minecraft 26.3 renamed InputConstants.Type.KEYSYM to KEYBOARD. This
changes one constant-pool Utf8 symbol in AnsSettings.class, preserving
all other class files, resources, animation assets, and GeckoLib externality.

Usage:
  python3 patch_private_ans_keyboard.py <stage6.jar> <stage7.jar>

Never commit the private input/output JAR or extracted copyrighted assets.
This fix does NOT establish that other 26.3 APIs are compatible.
"""
import json
import sys
import zipfile
from pathlib import Path

OLD = b"\x00\x06KEYSYM"
NEW = b"\x00\x08KEYBOARD"
TARGET = "com/ans/client/settings/AnsSettings.class"

def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_private_ans_keyboard.py <input.jar> <output.jar>")
    incoming, outgoing = map(Path, sys.argv[1:])
    if incoming.resolve() == outgoing.resolve():
        raise SystemExit("Always write to a new JAR; don't overwrite the working copy")
    patch_count = 0
    with zipfile.ZipFile(incoming) as zin, zipfile.ZipFile(outgoing, "w", allowZip64=True) as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            if info.filename == TARGET:
                assert data[:4] == b"\xca\xfe\xba\xbe", "Invalid Java class"
                assert data.count(OLD) == 1, "KEYSYM must be exactly one Utf8 constant-pool entry"
                data = data.replace(OLD, NEW)
                patch_count += 1
            elif info.filename == "fabric.mod.json":
                manifest = json.loads(data)
                assert manifest.get("id") == "ans", "Unexpected mod id"
                manifest["version"] = "1.0.1+mc26.3.stage7.keyboard-bridge-private-test"
                data = json.dumps(manifest, indent=2).encode("utf-8")
            zout.writestr(info, data)
    with zipfile.ZipFile(outgoing) as z:
        assert z.testzip() is None, "ZIP checksum mismatch"
        assert z.read(TARGET).count(NEW) == 1
    assert patch_count == 1
    print("Patched exactly one class:", TARGET)
    print("Experimental private JAR:", outgoing)
    print("Remaining 26.3 rendering/armor APIs are still unported.")

if __name__ == "__main__":
    main()
