# Bedrock Pack Bridge Studio — Fabric 1.21.11 experimental alpha

This branch adds a **local-only Bedrock pack inspector and player-motion preview** to the existing Fabric bridge. The user may privately test the Actions & Stuff 1.13 pack they supplied. It is NOT a complete Bedrock resource-pack compatibility layer, 3D Bedrock renderer, or full Java port.

## Installation
1. Download the **compiled non-sources JAR** from this branch's successful GitHub Actions build. Use Java 21, Fabric Loader for Minecraft **1.21.11**, and Fabric API.
2. Remove older `actions-stuff-java-bridge` JARs from the same Minecraft instance.
3. Put the compiled JAR in **mods**, not resourcepacks.
4. Put your personal original `Actions and Stuff 1.13.mcpack` in `<your Minecraft game directory>/config/bedrockbridge/packs/`.
   If you use a version-specific TLauncher instance, that means `I:\\AppData\\Roaming\\.minecraft\\versions\\AAS\\config\\bedrockbridge\\packs\\`.
5. Start a test world. Press **B** to open *Bedrock Pack Bridge Studio*.
6. Click **Scan / Reload**, choose the pack if more than one is present, browse an entity/model/animation, and use Play/Pause, seek, speed and bone controls.
7. Press **Use for player** if you want the numerical original player motion channels applied in third person. This **does not use the original custom mesh**, only the six vanilla body parts and body pivots currently supported by the bridge.
8. Close the studio and change perspective with F5. If the game crashes, supply `logs/latest.log` and the crash report.

## Studio v0.4 features
- Directly reads a local .mcpack and the original BRARCHIVE v1 packed JSON without exporting or uploading assets.
- Indexes Bedrock entity descriptions, geometry IDs, bone parent/pivot hierarchy, cube counts, animation IDs, keyframe tracks and controller state names.
- Entity/model/clip navigation, keyframe timeline with speed/seek/play controls and numeric per-bone inspector.
- 2D bone-pivot graph (NOT a skinned 3D Bedrock mesh).
- Optional local original-player animation import directly into the existing Fabric player model, without a manually converted ZIP.
- Unit tests for archive bounds, traversal rejection, entity alias resolution and numeric keyframe interpolation.

## Boundaries and known shortcomings
- Geometry and texture mapping are **not rendered in the world**. The studio's bone graph is a 2D debug visualization.
- Only numeric vector animation keyframes can be evaluated; many authentic Actions & Stuff channels use **MoLang**, blend controllers, conditions and custom bone names, and are currently incomplete.
- Default pack data only: SP0, SP1 and SP2 variant subpacks are not indexed in this prototype.
- `Use for player` currently maps a subset of original player action aliases onto Java vanilla-model body parts. No faithful custom-rig, death-system, first-person, mob animation or 3D material implementation yet.
- Resource reload resets the prior playback data. Open Studio and press Use for player again if needed.
- A successful CI build only proves compilation and synthetic unit tests, not that the screen or gameplay works on your PC.

## Privacy and rights
The original Oreville Studios assets remain on your own computer. **Never commit or publish the .mcpack or extracted/converted source assets** in this public repository unless explicit redistribution permission is provided. Testing permission is not publication permission.

## GitHub
This branch builds with Java 21 and the existing Fabric 1.21.11 workflow. Do not merge to main before client runtime tests.
