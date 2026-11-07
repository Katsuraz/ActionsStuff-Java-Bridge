package com.ans.client.mob.player;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Minecraft 26.3 replacement for the 26.2 binary PlayerRenderState.
 *
 * The original 26.2 class extended HumanoidRenderState and redeclared fields
 * such as skin, showCape, fallFlyingTimeInTicks, isSpectator, and the parrot
 * variants. In 26.3 AvatarRenderState already owns these same public members.
 *
 * Do NOT redeclare those fields: external 26.2 compiled GETFIELD/PUTFIELD
 * symbolic references to this subclass resolve to inherited AvatarRenderState
 * fields at JVM link time. This lets the local-player LevelExtractor accept
 * our render state without losing existing GeckoLib extraction data.
 *
 * This source-level class replaces ONLY PlayerRenderState.class in the private
 * mod JAR. All existing animation assets and renderer classes are retained.
 */
public final class PlayerRenderState extends AvatarRenderState {
    /** Original Actions & Java render opacity multiplier, not a vanilla field. */
    public float cameraFade = 1.0F;
}
