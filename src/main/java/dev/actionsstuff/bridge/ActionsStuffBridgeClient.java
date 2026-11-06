package dev.actionsstuff.bridge;

import dev.actionsstuff.bridge.studio.BedrockStudioScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceType;
import org.lwjgl.glfw.GLFW;

public final class ActionsStuffBridgeClient implements ClientModInitializer {
    public static final String MOD_ID = "actions_stuff_bridge";

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new BridgeReloadListener());
        KeyBinding studio = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.actions_stuff_bridge.studio",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, KeyBinding.Category.MISC)
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (studio.wasPressed()) {
                client.setScreen(new BedrockStudioScreen(client.currentScreen));
            }
        });
        BridgeAnimations.LOG.info("Actions & Stuff Java Bridge and Bedrock Studio initialized");
    }
}
