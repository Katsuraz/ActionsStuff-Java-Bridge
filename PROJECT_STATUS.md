# Actions & Stuff Java Bridge — development status

**Repository initialized:** 2026-11-06

## Current build target
- Minecraft Java **Fabric 1.20.1**, Java 17.
- The client code loads locally imported animation clip sets on resource reload.
- A prototype renderer mixin maps supported rotation keyframes onto vanilla biped body parts.
- This is not a complete faithful port and is NOT in-game verified.
- No original, converted or third-party licensed Bedrock assets are stored in the repository.

## Milestones
1. Verify GitHub Actions compilation; fix Java/Yarn/Mixin build errors.
2. Test client startup and diagnostic logging in a disposable Fabric 1.20.1 instance.
3. Replace biped-only animation with a faithful geometry/bone controller runtime.
4. Implement player and vanilla mob controller states, death animations, particles, sounds and equipment.
5. Create separate Fabric 1.21.11 and 26.3 adapters and their respective workflows.
6. Compare resulting animations to original authorized Bedrock references frame-by-frame.

## Known limitations
- The Bedrock animations contain custom bones, MoLang expressions, render controllers and geometry;
  currently unsupported and NOT silently approximated as complete behavior.
- The 1.13 resource pack contains compressed brarchive containers. The inspection tool inventories
  locally supplied archives; it does not itself decode or distribute licensed game content.
- Current test code does not include player rigs, custom mob models or first-person functionality.
- Build passing != game running != visual fidelity.

## Legal boundary
The user reports permission to *test* a Java port. The repository is **public** as currently
configured by the owner. It contains only independent source code and no original copyrighted assets.
Permission to redistribute the original assets has not been established.
