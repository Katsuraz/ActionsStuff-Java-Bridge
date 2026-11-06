package dev.actionsstuff.bridge.mixin;

import dev.actionsstuff.bridge.BridgeAnimations;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    // Mixin target is specific to Minecraft 1.20.1 Yarn.
    // Other versions need their own validated renderer hooks.
    @Inject(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V",
            shift = At.Shift.AFTER),
        require = 1
    )
    private void actionsStuff$afterVanillaPose(LivingEntity entity, float entityYaw, float tickDelta,
                MatrixStack matrices, VertexConsumerProvider providers, int light, CallbackInfo ci) {
        LivingEntityRenderer<?, ?> renderer = (LivingEntityRenderer<?, ?>) (Object) this;
        BridgeAnimations.apply(renderer.getModel(), entity, tickDelta);
    }
}
