package dev.actionsstuff.bridge.studio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Real 3D world-space layer; submits source-created ModelPart cuboids to the
 * official 1.21.11 entity render queue.
 *
 * This initial opt-in layer is an OVERLAY using the local player's skin. It
 * does not yet replace the whole vanilla player body or support each
 * Bedrock render-controller texture/material.
 */
public final class BedrockPlayerFeature
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public BedrockPlayerFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!OriginalRigRuntime.enabled() || state.invisible || state.spectator) return;
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.player.getId() != state.id) return;
        if (state.skinTextures == null || state.skinTextures.body() == null) return;

        BedrockCubeModel rig = OriginalRigRuntime.pose(state);
        if (rig == null) return;
        queue.submitModelPart(rig.root, matrices,
            RenderLayers.entityCutoutNoCull(state.skinTextures.body().texturePath()),
            light, OverlayTexture.DEFAULT_UV, null);
    }
}
