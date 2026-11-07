package com.ans.client.mixin.port26;

import com.ans.client.port26.HandPortTransforms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 26.3 source-level replacement entrypoint for the removed
 * ItemInHandRenderer mixin.
 *
 * The old binary expects LocalPlayer and a mutable ItemInHandRenderer. The
 * 26.3 renderer receives immutable/extracted PlayerRenderState and a separate
 * FirstPersonHandsAndItemsRenderState; old invokers are NOT reused here.
 *
 * Stage 1: vanilla hand submissions still work and a small, bounded source
 * sway can be applied without any old renderer class reference. The original
 * custom Bedrock first-person player geometry requires a later source port.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
    @Unique private boolean ans26$hasTransform;

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), require = 0)
    private void ans26$begin(float partialTicks, PoseStack poseStack,
                             SubmitNodeCollector collector, PlayerRenderState player,
                             FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        // Experiment is opt-in for now: old pack's complex first-person
        // geometry/hand state is not yet compatible with this 26.3 signature.
        if (!Boolean.getBoolean("ans26.experimentalHandSway")) return;
        var offset = HandPortTransforms.evaluate(
            partialTicks, hands.xBob, hands.mainHandSwapScale, hands.viewXRot);
        poseStack.pushPose();
        ans26$hasTransform = true;
        poseStack.translate(offset.x(),offset.y(),offset.z());
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"), require = 0)
    private void ans26$end(float partialTicks, PoseStack poseStack,
                           SubmitNodeCollector collector, PlayerRenderState player,
                           FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        if (ans26$hasTransform) {
            ans26$hasTransform = false;
            poseStack.popPose();
        }
    }
}
