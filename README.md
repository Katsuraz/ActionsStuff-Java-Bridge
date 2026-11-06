# Actions & Stuff Java Bridge

Independent, **experimental** Minecraft Java Fabric runtime for privately testing the adaptation of user-supplied Actions & Stuff Bedrock animation data.

## Status
- Current runtime source target: **Fabric 1.20.1**.
- Minecraft 1.21.11 and 26.3: planned adapters, **not yet built or tested**.
- Real in-game compatibility has **not** been verified.
- This repository includes code and tooling, not Oreville Studios' copyrighted models, textures, sounds, or converted animation assets.

## Goals
1. Interpret original Bedrock animation keyframes and controller states faithfully, rather than approximate movements.
2. Replace incompatible player and vanilla mob rigs with model-aware animation support.
3. Add timing, combat, death, swimming, first-person, equipment, particles, and sounds in progressively testable milestones.
4. Build and test multiple Fabric versions separately.

## Development rules
Original Actions & Stuff files are provided only for **private authorized testing**. **Never commit or upload the .mcpack, source textures, geometry, animations, converted copies, or private test ZIPs.** The user reports permission to test; public redistribution permission has not been established.

## Building
See `PROJECT_STATUS.md`. Run the GitHub Actions build workflow or use Java 17 + Gradle 8.7 for the initial Fabric 1.20.1 foundation. A passing compile is not proof of working visuals.

## Milestones
- [ ] Verify Fabric 1.20.1 compilation and client startup
- [ ] Validate source-defined player movement and controller transitions
- [ ] Build faithful custom model and bone hierarchy support
- [ ] Implement original vanilla-mob animations and state transitions
- [ ] Add first-person actions, equipment, effects and sound
- [ ] Create and verify 1.21.11 adapter
- [ ] Create and verify 26.3 adapter
