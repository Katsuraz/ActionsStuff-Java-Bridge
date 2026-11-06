# Bedrock Studio UI & optional Mod Menu integration — 1.21.11

This is the same Actions & Stuff Java Bridge project (not a replacement mod). The code remains in this repository, but its experimental render systems are now separated for safer testing.

## Open controls
- **Mods → Actions & Stuff Java Bridge → Configure** (with Mod Menu **17.0.1** installed for Minecraft 1.21.11): opens the new bridge dashboard.
- Without Mod Menu: **B** still opens the in-game Bedrock Pack Studio. Use the new **Settings** button in Studio to reach the dashboard.
- Both flows work without uploading or redistributing your original private `.mcpack`.

## Core change: avoid old/new system overlap
- The old six-part **legacy player pose engine** no longer auto-applies bundled synthetic clips when Minecraft starts. Default: **OFF**.
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
