# Actions & Java 26.3 engine port — source stage 1

This is **not** the older `ActionsStuff-Java-Bridge` implementation and is **not** another metadata-only JAR. It is a new Java 25/Fabric 26.3 source module for porting the **working user-supplied Actions & Java 1.0.1, Minecraft 26.2** codebase.

## What is already implemented

- A Java 25 Gradle/Fabric Loom module that compiles directly against **Minecraft 26.3**.
- A replacement Mixin targeting `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.submitHandsWithItems`.
- Uses the new `PlayerRenderState` and `FirstPersonHandsAndItemsRenderState` instead of the removed 26.2 `ItemInHandRenderer`/old `LocalPlayer` signature.
- Safe bounded hand camera movement is **off by default** (opt-in JVM option `-Dans26.experimentalHandSway=true`). The stage-1 replacement does *not* yet try to call the legacy first-person custom model path.
- A GitHub Actions workflow builds these classes with Java 25, Gradle 9.8.1, and actual Minecraft 26.3 dependencies.

## Why the earlier 26.3 test crashed

User's crash log:
```
InvalidMixinException: @Mixin target
net.minecraft.client.renderer.ItemInHandRenderer was not found
ans.client.mixins.json:ItemInHandRendererAccessor from mod ans
```

The original JAR's `ItemInHandRendererMixin` also targets that deleted class. The old `HeldArmorFirstPersonMixin` depends on `ItemInHandRendererAccessor`. The 26.3 API has moved camera/hand state to `FirstPersonHandsAndItemsRenderState`; it is **not** a pure rename of a target.

The old `FirstPerson.submit(ItemInHandRenderer, ..., LocalPlayer, ...)` custom render routine cannot simply be called from 26.3's `submitHandsWithItems(..., PlayerRenderState, FirstPersonHandsAndItemsRenderState)`. Its logic will require source-level porting, including GeckoLib rendering and item/armor/attachment handling.

## IMPORTANT stage boundaries

This source module is only the **first compatibility subsystem**. It has not yet ported the original mod's other 300+ compiled classes, approximately 20 unrelated Mixin targets, the player custom geometry renderer, Bedrock 1.13 content pipeline, particle systems, entities, or the 26.3 GeckoLib API. Successful compilation only proves the new source is valid against Minecraft 26.3; it does *not* prove the entire private base JAR starts or plays.

The user's owned JAR and original 1.13 Bedrock assets must remain on their machine or in their private test environment, **not in this public repository**.

## Testing workflow

1. Let the `Actions & Java 26.3 first-person source port` GitHub workflow compile this module.
2. Download its JAR containing **only independently written source-level compatibility classes**.
3. Use the private local patcher to replace exactly the stale first-person mixin registrations in a **copy** of the user's 26.3 experimental JAR. Keep the working 26.2 mod untouched.
4. Launch Minecraft **26.3** with Java **25**, matching Fabric API, GeckoLib **5.5.7**, and no duplicate `ans` mods.
5. Inspect `latest.log` for next missing target/class and iterate **source code** until every affected renderer/mixin compiles and launches. Do not assume all source APIs are backward compatible.
6. Only after a complete client smoke test should a 26.3-compatible build be considered a working port.

See: [Fabric's Minecraft 26.3 release notes](https://fabricmc.net/2026/09/15/263.html) and [official Fabric 26.3 example-mod](https://github.com/FabricMC/fabric-example-mod/tree/26.3).
