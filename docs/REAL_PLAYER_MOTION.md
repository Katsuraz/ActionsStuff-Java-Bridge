# Source Player Motion — first actual original player animation playback

Minecraft Fabric **1.21.11**, Java 21. Test build v0.7.0-alpha.1.

## What changed from the previous UI-only releases

The local original **Actions and Stuff 1.13.mcpack** was inspected during development **without committing or redistributing its contents**. The Minecraft player animation aliases contain source clips `walk`, `move`, `bob`, `swim`, `jump`, `fall`, `attack`, `hurt`, `fly`, and more. In the original Bedrock data, source body bones are named `rightarm`, `leftarm`, `rightleg`, and `leftleg`, not the legacy converter's underscored `right_arm` etc.

The previous legacy renderer erroneously discarded those source bones and skipped the expression-driven tracks, so the majority of original motion never reached the player.

**SourcePlayerMotion** is a new, separate runtime; it:
- Samples direct locally imported source rotation channels from the original pack, including supported MoLang expressions based on `q.anim_time` and `q.life_time`, and numeric timed keyframes.
- Maps real Bedrock `rightarm/leftarm/rightleg/leftleg/head/body` bones to Minecraft Java's existing player body parts.
- Selects idle, walk, faster move/sprint, jump/fall, swimming, gliding, hit/use states from Java's render state.
- Does not change any model origins, dimensions, scales, skins, custom geometry, inventory attachments, or equipment pivots; source movement is blended onto the vanilla rig with conservative arm overrides during item use.
- Applies the resulting **rotations only** to clothing and hat to avoid detached cyan geometry caused by legacy positional and scale modifications.
- Starts OFF. The simple Mod Menu or B-key setup screen can now **import pack**, then **enable player motion**, then (optionally) explore the Studio.
- Keeps Legacy Player Poses and 3D overlays mutually exclusive. The old legacy adapter remains available only under Advanced Tests and is not recommended.

## Steps to test
1. Remove any other installed versions of this bridge from `mods`, install the v0.7.0-alpha.1 JAR and Fabric API on 1.21.11.
2. Put your original legally obtained `.mcpack` in your Minecraft instance's `config/bedrockbridge/packs` directory.
3. Start a world and press **B** (or use Mod Menu → Configure).
4. Click **1. Import my .mcpack** and confirm the pack status says IMPORTED.
5. Click **2. Enable Player Motion**. It should state source player motion ON and list the number of original source clips it found.
6. Switch to third person using F5. Test walking, sprinting, swimming, jumping and idle. Compare arm and leg motion while using an item.
7. Click **2. Turn OFF Player Motion** to restore vanilla rendering immediately.

## Limitations and known gaps
- **The original full Actions & Stuff pack is not yet enabled**: these are the original player's **rotation tracks** applied to the Java humanoid, not a complete 3D geometry/material/texture/accessory/mob conversion.
- The renderer is local-client third person initially. It does not synchronize animation states to other players and does not replace the first-person model.
- Source controllers, some MoLang variables, animation layering, root/waist spatial deformation, keyed Catmull-Rom interpolation, and source material-dependent effects remain incomplete.
- Unsupported source expressions are skipped; existing vanilla movement is preserved instead of inventing data.
- Compilation and tests do **not** guarantee the complete game experience. Real in-game test footage/logs are needed to validate visual fidelity.

## Copyright
Original Bedrock resource-pack data remains in the user's own `.mcpack`; the repository and distributed JAR contain only independently authored Java logic and synthetic tests.
