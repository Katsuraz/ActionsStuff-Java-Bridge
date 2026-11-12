#!/usr/bin/env python3
"""Safe, offline-oriented inspection of mod JAR metadata and a CI smoke fixture."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import zipfile

MAX_DOWNLOAD = 60 * 1024 * 1024
ALLOWED_HOSTS = {
    "github.com", "release-assets.githubusercontent.com",
    "objects.githubusercontent.com", "cdn.modrinth.com",
    "repo.maven.apache.org", "maven.fabricmc.net",
}

def download(url: str, output: Path) -> None:
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS:
        raise ValueError("URL must be HTTPS from a permitted public download host")
    req = Request(url, headers={"User-Agent": "Minecraft-REA-CI/1.0"})
    output.parent.mkdir(parents=True, exist_ok=True)
    with urlopen(req, timeout=45) as response:
        target_host = urlsplit(response.geturl()).hostname
        if target_host not in ALLOWED_HOSTS:
            raise ValueError("Download redirected to an unapproved host")
        size = 0
        with output.open("wb") as dest:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > MAX_DOWNLOAD:
                    output.unlink(missing_ok=True)
                    raise ValueError("JAR exceeds the 60 MiB download limit")
                dest.write(chunk)
    if not zipfile.is_zipfile(output):
        output.unlink(missing_ok=True)
        raise ValueError("Downloaded file is not a valid JAR/ZIP archive")

def inspect(jar: Path, output_dir: Path) -> dict:
    if not jar.is_file() or not zipfile.is_zipfile(jar):
        raise ValueError("Input must be an existing valid JAR/ZIP archive")
    output_dir.mkdir(parents=True, exist_ok=True)
    sha256 = hashlib.sha256(jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(jar) as archive:
        infos = archive.infolist()
        if len(infos) > 150_000 or sum(i.file_size for i in infos) > 1_000_000_000:
            raise ValueError("Archive is too large to inspect safely")
        names = [i.filename for i in infos]
        classes = sorted(n for n in names if n.endswith(".class"))
        mixes = sorted(n for n in names if n.endswith(".json") and "mixin" in n.lower())
        metadata = {}
        for entry in ("fabric.mod.json", "quilt.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
            if entry in names:
                raw = archive.read(entry)
                if len(raw) > 512_000:
                    continue
                if entry.endswith(".json"):
                    try:
                        metadata[entry] = json.loads(raw.decode("utf-8"))
                    except (UnicodeDecodeError, json.JSONDecodeError):
                        metadata[entry] = {"warning": "Could not parse JSON"}
                else:
                    metadata[entry] = raw.decode("utf-8", "replace")[:20000]
    report = {
        "file_name": jar.name,
        "sha256": sha256,
        "size_bytes": jar.stat().st_size,
        "entry_count": len(names),
        "class_count": len(classes),
        "mixin_json_files": mixes,
        "loader_manifests": metadata,
    }
    (output_dir / "metadata.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output_dir / "classes.txt").write_text("\n".join(classes) + ("\n" if classes else ""), encoding="utf-8")
    mods = metadata.get("fabric.mod.json", {})
    if not isinstance(mods, dict):
        mods = {}
    lines = [
        "# Minecraft JAR reverse-engineering report", "",
        f"- Input: \`{jar.name}\`",
        f"- SHA-256: \`{sha256}\`",
        f"- File size: {jar.stat().st_size:,} bytes",
        f"- Entries: {len(names):,}; classes: {len(classes):,}",
        f"- Fabric mod ID: \`{mods.get('id', 'not declared')}\`",
        f"- Fabric mod version: \`{mods.get('version', 'not declared')}\`",
        f"- Loader manifests: {', '.join(metadata) or 'none found'}",
        f"- Mixin config JSON files: {len(mixes)}",
        "", "## Mixin-related JSON files", "",
    ] + [f"- \`{name}\`" for name in mixes]
    lines += ["", "## Limitations", "", 
              "Static JAR and bytecode inspection does not prove that a mod works in Minecraft.",
              "Run integration tests and in-game tests separately before claiming compatibility.", ""]
    (output_dir / "report.md").write_text("\n".join(lines), encoding="utf-8")
    return report

def fixture(jar: Path) -> None:
    jar.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        (root / "ExampleMod.java").write_text(
            'public class ExampleMod { public static String greet() { return "REA smoke test"; } }\n',
            encoding="utf-8",
        )
        subprocess.run(["javac", "-d", str(root), str(root / "ExampleMod.java")], check=True)
        fabric = {
            "schemaVersion": 1, "id": "rea-ci-fixture",
            "version": "1.0.0", "name": "REA CI Fixture",
            "environment": "*", "entrypoints": {},
        }
        with zipfile.ZipFile(jar, "w", compression=zipfile.ZIP_DEFLATED) as out:
            out.write(root / "ExampleMod.class", "ExampleMod.class")
            out.writestr("fabric.mod.json", json.dumps(fabric))

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    p = subs.add_parser("download")
    p.add_argument("--url", required=True)
    p.add_argument("--dest", type=Path, required=True)
    p = subs.add_parser("fixture")
    p.add_argument("--dest", type=Path, required=True)
    p = subs.add_parser("inspect")
    p.add_argument("--jar", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "download":
        download(args.url, args.dest)
        print(f"Downloaded {args.dest}")
    elif args.command == "fixture":
        fixture(args.dest)
        print(f"Created smoke-test JAR: {args.dest}")
    else:
        result = inspect(args.jar, args.out)
        print(f"Inspected {result['class_count']} classes: {args.out}")

if __name__ == "__main__":
    main()
