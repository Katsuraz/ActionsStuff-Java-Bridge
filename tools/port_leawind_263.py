#!/usr/bin/env python3
"""Prepare Leawind Third Person 3.0.3-beta source for an actual Fabric 26.3 compile."""
from pathlib import Path
import shutil
import sys

def replace_exact(path: Path, original: str, replacement: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(original) != 1:
        raise RuntimeError(f"Expected exactly one matching fragment in {path}: {original!r} (found {text.count(original)})")
    path.write_text(text.replace(original, replacement), encoding="utf-8")

def main(project: Path, local_api: Path) -> None:
    project = project.resolve()
    local_api = local_api.resolve()
    if not local_api.is_file() or not local_api.name.endswith(".jar"):
        raise FileNotFoundError(f"Built Perspective API 26.3 JAR required: {local_api}")
    replace_exact(project/"settings.gradle.kts",
                  'mc("26.2", "fabric", "neoforge")',
                  'mc("26.2", "fabric", "neoforge")\n        mc("26.3", "fabric")')
    replace_exact(project/"settings.gradle.kts",
                  'vcsVersion = "26.2-fabric"',
                  'vcsVersion = "26.3-fabric"')
    replace_exact(project/"stonecutter.gradle.kts",
                  'stonecutter active "26.2-fabric"',
                  'stonecutter active "26.3-fabric"')
    original_dependency = 'modstitchModImplementation("maven.modrinth:LIqveQm1:$perspectiveApiVersion+${loader}-$mcVersion")'
    replacement_dependency = 'modstitchModImplementation(files(rootProject.file("../local-dependencies/perspective_api_26_3.jar")))'
    replace_exact(project/"build.gradle.kts", original_dependency, replacement_dependency)
    # Perspective API 26.3 splits selector order, duplicate-ID precedence,
    # and dynamically resolved vanilla camera base type.
    behavior = project/"src/main/java/io/github/leawind/thirdperson/internal/logic/base/ThirdPersonPerspective.java"
    replace_exact(behavior,
                  '    priority = 10,\n    baseType = PerspectiveBehavior.BaseType.THIRD_PERSON_BACK,\n    traits = {"third_person", "controllable"})'.replace('\n','\n'),
                  '    order = 10,\n    precedence = 10,\n    traits = {"switchable", "third_person", "controllable"})'.replace('\n','\n'))
    replace_exact(behavior,
                  "  @Override\n  public void onActivate()".replace('\n','\n'),
                  "  @Override\n  public PerspectiveBehavior.BaseType getBaseType() {\n    return PerspectiveBehavior.BaseType.THIRD_PERSON_BACK;\n  }\n\n  @Override\n  public void onActivate()".replace('\n','\n'))
    verdir = project/"versions/26.3-fabric"
    verdir.mkdir(parents=True, exist_ok=True)
    (verdir/"gradle.properties").write_text(
        "modstitch.platform=fabric-loom\n"
        "mcVersion=26.3\n"
        "deps.fabricApi=0.161.0+26.3\n"
        "mod.modmenu_version=21.0.0-beta.1\n"
        "mod.yacl_version=3.9.7+26.3\n"
        "meta.mcDep=>=26.3\n", encoding="utf-8")
    local_target = project.parent/"local-dependencies/perspective_api_26_3.jar"
    local_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(local_api, local_target)
    print(f"Prepared Third Person 26.3 Fabric source with API {local_api.name}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: port_leawind_263.py THIRD_PERSON_SOURCE BUILT_PERSPECTIVE_API_JAR")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
