package dev.actionsstuff.bridge.studio;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional integration: the bridge can run with no Mod Menu installed. */
public final class BedrockModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BridgeDashboardScreen::new;
    }
}
