package dev.actionsstuff.bridge.mixin;

import dev.actionsstuff.bridge.BridgeAnimations;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apply locally supplied animation clips after vanilla has posed the player.
 * 1.21.11 uses render states instead of the older living-entity render signature.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(
        method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
        at = @At("TAIL")
    )
    private void actionsStuff$applyPlayerAnimation(PlayerEntityRenderState state, CallbackInfo ci) {
        BridgeAnimations.apply((BipedEntityModel<?>)(Object)this, state);
    }
}
