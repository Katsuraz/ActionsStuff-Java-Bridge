package com.ans.client.mixin.port26;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accesses the new 26.3 SwingState fields to preserve the old public animation
 * fields until the full Actions & Java source can be recompiled.
 */
@Mixin(LivingEntity.SwingState.class)
public interface SwingStateCompatAccessor {
    @Accessor("ticks")
    int ans26$getTicks();

    @Accessor("currentSwing")
    LivingEntity.SwingDescription ans26$getCurrentSwing();
}
