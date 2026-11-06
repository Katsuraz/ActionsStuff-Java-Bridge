# v0.7.1-alpha.1 — Source Player Motion polish (Fabric 1.21.11)

## Based on user gameplay footage
The 24-second third-person test shows the imported source player motion running, but with visibly stiff/asymmetric arm behavior while carrying a sword and abrupt-looking stride/pose changes. This update specifically revises runtime motion; the current importer and Mod Menu setup remain in place.

## Changes
- Walking and sprinting share a **continuous, velocity-adaptive stride clock**. Switching between the source `walk` and `move` clips no longer forces the gait back to the beginning of the timeline.
- Changes in movement/idle/attack/jump phase use a **five-tick cubic crossfade between the previous source animation and the next one**, instead of briefly dropping back to an unanimated vanilla source state at every transition.
- Separate main-hand and offhand handling: an equipped tool receives a conservative amount of source arm swing while the other hand can still move normally. Actual item-use poses remain prioritized. Left-handed players use their configured main arm.
- Original source animations with `lerp_mode: "catmullrom"` now use a Catmull-Rom interpolation subset for numeric/mathematical keyframe rotation samples. Useful for smoother source jump, hurt and attack arcs. Pre/post keyframes and unsupported expressions still follow earlier behavior.
- Does not change player model origins, scale, equipment pivots, custom geometry, or skin materials. This keeps the old legacy cyan floating parts issue isolated.
- Includes unit tests for original-style curved keyframe interpolation, stride continuity, source crossfades, and equipment-hand protection.

## Testing
1. Use only one **actions-stuff-java-bridge** JAR, Fabric 1.21.11 + Fabric API + Java 21. Replace v0.7.0-alpha.1 with **v0.7.1-alpha.1**.
2. Keep original private `Actions and Stuff 1.13.mcpack` in the active instance's `config/bedrockbridge/packs/`.
3. Press **B**, **Import my .mcpack**, then **Enable Player Motion**. Don't use the Legacy Player Poses switch.
4. Test **walk → sprint → walk → stop**, with empty hand, sword, and offhand item; test jumping, sword swinging, swimming. Use F5 third-person.
5. Send gameplay video and `latest.log` on crashes. The mod still only applies source player rotation tracks to the existing Java humanoid; source rig geometry, facial accessories, full controller, multiplayer sync and textures are not yet complete.

No third-party assets are committed in this branch. A GitHub build and unit-test pass does not imply visual correctness in Minecraft; that requires in-game testing.
