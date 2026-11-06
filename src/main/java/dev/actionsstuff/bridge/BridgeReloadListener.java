package dev.actionsstuff.bridge;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class BridgeReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return Identifier.of(ActionsStuffBridgeClient.MOD_ID, "bridge_animation_reload");
    }

    @Override
    public void reload(ResourceManager manager) {
        BridgeAnimations.reload(manager);
    }
}
