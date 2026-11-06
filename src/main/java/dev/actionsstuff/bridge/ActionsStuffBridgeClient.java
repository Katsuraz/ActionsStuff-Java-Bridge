package dev.actionsstuff.bridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public final class ActionsStuffBridgeClient implements ClientModInitializer {
    public static final String MOD_ID = "actions_stuff_bridge";

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new BridgeReloadListener());
        BridgeAnimations.LOG.info("Actions & Stuff Java Bridge initialized (experimental build)");
    }
}
