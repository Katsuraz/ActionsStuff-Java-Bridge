# Simple setup — Fabric 1.21.11

This project is still an **experimental Actions & Stuff Java Bridge**, not the finished Actions & Stuff Bedrock resource pack port.

## Two clear steps

1. Place your legally obtained **original** `.mcpack` in the active Minecraft instance's `config/bedrockbridge/packs/` directory. The original files are private and are not packaged in the mod or GitHub repo.
2. Open **Mods → Actions & Stuff Java Bridge → Configure** (with optional Mod Menu installed), or press **B** while Minecraft is running.
3. Click **1. Import my .mcpack**. This only reads/recognizes the original assets.
4. Click **2. Open Animation Studio** to browse and preview animation/model data.

**Imported does NOT mean enabled in game.** The 1.13 pack still uses Bedrock geometry/render-controller/material features our Java runtime doesn't fully implement. There is no legitimate single-click enable for the complete Actions & Stuff experience in this version. The status displayed in the simple screen says **Full Actions & Stuff port: NOT READY**.

The old detailed legacy pose and 3D/controller switches have moved to **Advanced Tests / Debugging**, because they're diagnostic-only. In particular, the legacy pose adapter causes visible cyan artifacts in the user's reported tests. Leave it off during normal gameplay.

When a safe 3D model is unavailable, the experimental 3D switch is intentionally BLOCKED rather than drawing accessory cubes as a fake player body.

## Current limits

- Opening the Studio previews source geometry/animation data, not the full pack's 3D effects inside Java Minecraft.
- The advanced 3D rendering implementation is partial and requires a supported full body mesh.
- Controller coverage, original textures, UVs, all mob models and complete source player rig remain unfinished.
- Build/test success establishes compilation and unit tests only, not in-game playback/visual fidelity.

## Feedback

If the simplified screen still confuses you, provide a screenshot of it and the text at the bottom. We can adjust layout and wording without guessing which switch you meant.
