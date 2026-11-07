package com.ans.client.mixin.port26;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Minecraft 26.2 -> 26.3 backwards-compatibility bridge.
 *
 * The old Actions & Java animation pipeline takes its attack progress
 * from LivingEntity.getAttackAnim(float), which no longer exists in 26.3.
 * The new SwingState owns the hand animation interpolation.
 *
 * This method is intentionally merged onto the target by Mixin rather
 * than replacing every compiled 26.2 call site (including invokedynamic
 * method handles) with unsafe constant-pool rewrites.
 *
 * Private compatibility bridge: does not change the GeckoLib library.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingCompatMixin {
    @Shadow @Final private LivingEntity.SwingState swingState;

    public float getAttackAnim(float partialTicks) {
        return this.swingState.getAnimation(partialTicks);
    }
}
