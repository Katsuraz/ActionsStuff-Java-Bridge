## Alpha.2 — 3D and controller toggle diagnosis

- **3D ON is conditional on compatible geometry.** Many original Actions & Stuff player-linked entries are facial/accessory meshes selected by Bedrock visibility and material controllers, *not standalone player models*. Such geometry is intentionally blocked from the experimental world overlay, and the button now reads `3D: BLOCKED` with a concrete source-model reason.
- **Controllers depend on an active supported 3D rig AND at least one usable imported animation controller.** The UI now reads `Controllers: NEED 3D` or `Controllers: NO DATA` instead of appearing to toggle silently. An unsupported expression doesn't activate a controller.
- **Mod Menu now offers a real 3D enable/disable action**, not only a Disable button. For a new game session, open Studio and scan the original local `.mcpack` first; Mod Menu can then reuse the imported pack. If a compatible full-body mesh is not available, it explains why world rendering is blocked instead of displaying all facial/accessory cubes.
- When starting original 3D successfully, the legacy pose adapter is switched off. A failed attempt to enable 3D does *not* silently disable an already-active legacy animation test.
- The selected model can always be inspected in Studio regardless of whether it is safe for in-world rendering.

**This is not yet complete 3D Actions & Stuff support.** Source render-controller visibility, texture/material rules, custom attachments to the built-in Bedrock humanoid, and full MoLang still need implementations. The toggles should not imply those systems are finished.

# Bedrock Studio UI & optional Mod Menu integration — 1.21.11

This is the same Actions & Stuff Java Bridge project (not a replacement mod). The code remains in this repository, but its experimental render systems are now separated for safer testing.

## Open controls
- **Mods → Actions & Stuff Java Bridge → Configure** (with Mod Menu **17.0.1** installed for Minecraft 1.21.11): opens the new bridge dashboard.
- Without Mod Menu: **B** still opens the in-game Bedrock Pack Studio. Use the new **Settings** button in Studio to reach the dashboard.
- Both flows work without uploading or redistributing your original private `.mcpack`.

## Core change: avoid old/new system overlap
- The old six-part **legacy player pose engine** no longer auto-applies clips when Minecraft starts. Default: **OFF**. The previous exaggerated synthetic test animation file is now **removed from the packaged JAR** entirely. Explicitly import original source clips through Studio to enable it.
- The original 3D geometry overlay is also opt-in, and the existing safety gate blocks partial facial/accessory meshes.
- Activating source clips via **Legacy player poses** turns the 3D overlay off. Enabling the 3D overlay disables legacy posing.
- Controller layers are separate and experimental; they only affect an active original source rig.
- Dashboard displays a best-effort list of potentially conflicting OTHER animation mods installed in Fabric. This is a hint, not proof of blame.

## Important limitation regarding floating cyan geometry
The previous user recording showed cyan pieces even while **Original 3D: OFF**. That does not prove they were rendered by this particular bridge: an older distinct Actions & Stuff test mod JAR, another mod, or active resource pack can independently draw geometry. **Disable/remove earlier test JARs and old animation resource packs from the test instance**, but leave this newer bridge and the locally imported `.mcpack` in `config/bedrockbridge/packs`.

The legacy bridge itself modifies model rotations; it does not generate cyan cube meshes. This update makes the legacy posing completely opt-in so old code cannot silently interfere. It does not uninstall or override independently installed older mods.

## Studio interface changes
- Responsive four-row navigation, playback and diagnostics controls.
- Access to dashboard/Mod Menu settings without leaving Minecraft.
- Distinct legacy poses / 3D geometry / controller status.
- Previously overlapping lower-right debug text removed.
- Text clarifies that this is an incomplete source geometry/MoLang implementation, not finished faithful Bedrock rendering.

## Test checklist
1. Create a separate Fabric 1.21.11 instance, Java 21, Fabric API, new bridge JAR.
2. Temporarily disable all older Actions & Stuff test mods, prior resource packs, and Punchy! Moves for isolation.
3. Launch Minecraft and ensure legacy poses are **OFF**, 3D overlay **OFF**.
4. Observe third-person player WITHOUT enabling any bridge feature: there should be no movement or 3D graphics originating from this mod.
5. Open Studio with B; inspect the private pack. Open Settings and verify Mod Menu Configure works if installed.
6. Test legacy poses and 3D overlay **one at a time**. If cyan geometry persists with both off, compare logs and installed other mods and resource packs.

## Verification
The GitHub build must pass Gradle compilation and JUnit/source-contract checks. Visual fidelity and real client compatibility still require a human Minecraft test.
