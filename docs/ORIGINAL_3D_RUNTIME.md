# Actions & Stuff Bedrock Bridge — Source 3D Rig Prototype (Fabric 1.21.11)

## Scope of v0.5.0-alpha.1
A separate, opt-in, source-driven 3D player geometry **overlay** is integrated into the existing Bedrock Pack Studio. The actual geometry is built at runtime from the user's original local `.mcpack`; none of the copyrighted assets are in this repository or in the compiled JAR.

### Implemented
- Existing Studio (press B): archive importer, entity/model browser, animation inspector.
- **3D Original ON/OFF** button: constructs a real parent-child `ModelPart` hierarchy with source Bedrock pivots, box cuboids, rest rotations, and conservative geometry size limits.
- Registers a Fabric player feature renderer that submits the source mesh into Minecraft's real 1.21.11 render command queue, using the local player's skin as an initial compatible material.
- Optional original source animation channels are sampled on the **full imported source bone hierarchy**, not only the six vanilla body bones.
- Adds partial safe MoLang numeric evaluation: arithmetic, degree-based sine/cosine/tangent, min/max/clamp/lerp, comparison and ternary, `q.anim_time`, life time, movement state aliases, etc.
- Reads numeric timed keyframes, plus pre/post value pairs, with linear interpolation.
- Keeps the earlier vanilla player animation test available under `Use for player`; this is separate from the 3D overlay.
- Unit tests for Bedrock archive decoding, basic importer, MoLang expressions and crash regressions.

### What is NOT finished
- **This is not a complete original Actions & Stuff 3D replacement.** It is a new **rendered overlay** above the vanilla character. We have not implemented original avatar replacement, correct multi-pass skins, armor, render controllers, all UV faces, missing vanilla geometry, per-face UV, material opacity or entity texture switching.
- Full MoLang variable scope, condition/query data, assignment/execution, random operators and animation controller state machines still need implementation. Unknown expressions are rejected, not guessed.
- Bedrock animation alias state selection currently maps some Java player actions to known source aliases. It does not yet execute source controllers.
- Only the local player's third-person model is affected. Mob rendering and first-person presentation are not implemented.
- Original geometry selection matters: some of the 62 player-linked geometries are isolated accessories. Choose a model with sensible texture dimensions and cubes. Most unusual high-cube geometries are blocked for safety.
- **A CI pass does not establish in-game compatibility, correct visuals or full fidelity**.

## Installation / testing
1. Create a separate **Fabric Minecraft 1.21.11** instance with Java 21 and Fabric API.
2. Remove all older Actions & Stuff Bridge JARs and install the compiled **v0.5.0-alpha.1** JAR from this branch's GitHub Actions artifact (not `-sources.jar`).
3. Copy your ORIGINAL owned `Actions and Stuff 1.13.mcpack` into `<instance game directory>/config/bedrockbridge/packs/`. Do not unzip it.
4. Start a world, press **B**, click Scan / Reload.
5. Select `minecraft:player`, choose an appropriate model (the 64x64 source face model with 88 bones can be a diagnostic), and press **3D Original ON/OFF**. Close the screen.
6. In third person (`F5`), observe source geometry overlay and different movements. Press B again to turn the overlay off.
7. To compare the old vanilla pose adapter, click `Use for player` separately.
8. If crashes occur, send the complete `logs/latest.log` and crash report, plus a screenshot or recording.

## Rights
Private testing permission does not imply public redistribution. This branch and the build artifact include independently written code and synthetic test fixtures only. Leave original/private source packs on your computer.

## Source validation
Run `gradle --no-daemon build` with Java 21; GitHub Actions uses Gradle 9.8 and the pinned Loom toolchain. Automated tests check expression handling and archive parsing, but not GPU rendering.
