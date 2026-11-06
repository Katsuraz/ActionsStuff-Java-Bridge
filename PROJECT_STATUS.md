# Actions & Stuff Java Bridge — development status

**Active development target: Minecraft Java Fabric 1.21.11.**

## Current alpha test
- Java **21**, Fabric Loader **0.18.4**, Fabric API **0.141.1+1.21.11**.
- Build uses current remapping Loom with Yarn 1.21.11 mappings.
- A **synthetic** (not Oreville-owned) idle/walk animation is bundled to smoke-test real player animation hooks.
- Client reload reads `assets/actions_stuff_bridge/animations/player.json` in the private user's own resource packs.
- Third-person vanilla player model only; no Punchy! Moves dependency.
- Animation clips currently support static and timestamped numeric rotation keyframes only.

## What to verify
1. GitHub Actions Gradle build succeeds.
2. The mod JAR loads with Minecraft Fabric 1.21.11 and Fabric API installed.
3. Third-person idle arm sway and walking arm/leg test motions are visible.
4. No Mixin application errors occur in latest.log.
5. After successful synthetic smoke test, test locally imported original-source 1.13 keyframes. Do not commit them here.

## Not implemented
Original geometry hierarchy and alternate bones, MoLang evaluation, custom mob models,
multi-state Bedrock animation controllers, first-person rendering, animation blending and
synchronization, particles, sounds, and item/armor accessory mapping.

## Asset security
This public repository contains only independently authored code and synthetic smoke-test
motions. No Oreville Studios asset is included. Permission to test is not permission to
redistribute the asset source files, converted animations or the complete port.

**Passing CI is not proof of in-game fidelity.**
