# Actions & Stuff Java Bridge — Fabric 1.21.11 source-driven alpha

Independent, experimental client-side Minecraft Java animation runtime for private testing of a Bedrock asset port.

## First test: Fabric 1.21.11

This branch targets **Minecraft 1.21.11** and **Java 21**. The bundled example still uses independent synthetic poses; to test **original source movements**, enable the privately generated resource pack built from the tester's own Actions & Stuff 1.13 copy.

### Installation
1. Build with the GitHub Actions workflow (or Gradle 9.3.0 and JDK 21).
2. Download the built JAR from Actions (do not install the `-sources.jar`).
3. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
4. Place the compiled bridge JAR in `.minecraft/mods` (not `resourcepacks`).
5. Remove older Actions & Stuff **test JARs and test ZIPs** from the test instance to prevent confusion.
6. Start a separate test world, switch to third person (`F5`). The new **diagnostic** poses are deliberately exaggerated: standing still should produce very obvious arm lifting, and walking should produce extreme arm/leg movement. These are not original Actions & Stuff animations.
7. Check `logs/latest.log` for `ActionsStuffBridge`. Expected messages: `2 player animation clips loaded: ...` and periodically `Renderer applied bridge animation phase=...`. If no visible movement and the second message is missing, the render hook is not executing. If both messages appear but nothing moves, we need to examine pose application/layering. If no clips load, verify the built JAR contains `assets/actions_stuff_bridge/animations/player.json`.
8. **Do not install this alongside the older bridge JAR**; remove it first.

No Punchy! Moves dependency is required for the standalone animation runtime. The original private resource ZIP overrides the bundled synthetic clips when enabled above other resource packs.
The bridge currently accepts numeric rotation/position/scale keyframes in `assets/actions_stuff_bridge/animations/player.json`. The model is still vanilla, and original Bedrock rig/controllers/animations have **not** been installed or verified.

### Source handling
Original Actions & Stuff files and any converted asset copies must stay on the tester's device, not in this repository. The user reports permission to **test**, not a public redistribution license.

### Future plans
Faithful original player movement and custom rig, Mob model/controller systems, first-person poses, equipment/holsters, particles/sounds, followed by additional Fabric version adapters.

See [PROJECT_STATUS.md](PROJECT_STATUS.md).
