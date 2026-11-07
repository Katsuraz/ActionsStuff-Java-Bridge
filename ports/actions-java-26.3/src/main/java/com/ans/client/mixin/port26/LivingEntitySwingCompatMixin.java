package com.ans.client.mixin.port26;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 animation ABI compatibility for Actions & Java on Minecraft 26.3.
 * Keeps legacy getAttackAnim() and one-argument swing() call sites working,
 * and mirrors the new SwingState data for legacy sound/player code that reads
 * the removed swingTime, swinging and swingingArm fields.
 *
 * This private porting module does NOT itself prove the entire 26.2 JAR is
 * compatible with 26.3. Retire the bridge once all callers are recompiled.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingCompatMixin {
    @Shadow @Final private LivingEntity.SwingState swingState;

    // 26.2 public fields removed in Minecraft 26.3.
    // Intentionally retain their original names/descriptors for old call sites.
    public int swingTime;
    public boolean swinging;
    public InteractionHand swingingArm;

    public float getAttackAnim(float partialTicks) {
        return this.swingState.getAnimation(partialTicks);
    }

    // 26.2 signature: LivingEntity.swing(InteractionHand)
    public void swing(InteractionHand hand) {
        this.swinging = true;
        this.swingTime = 0;
        this.swingingArm = hand;
        ((LivingEntity)(Object)this).swing(hand, SwingAnimation.DEFAULT, true);
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void ans26$syncLegacySwingFields(CallbackInfo ci) {
        var accessor = (SwingStateCompatAccessor)(Object)this.swingState;
        this.swinging = this.swingState.isSwinging();
        this.swingTime = this.swinging ? accessor.ans26$getTicks() : 0;
        var swing = accessor.ans26$getCurrentSwing();
        this.swingingArm = swing == null ? InteractionHand.MAIN_HAND : swing.hand();
    }
}
