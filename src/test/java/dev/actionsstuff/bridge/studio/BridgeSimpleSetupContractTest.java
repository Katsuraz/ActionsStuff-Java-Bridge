package dev.actionsstuff.bridge.studio;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Avoid pretending that imported .mcpack data is a complete Java port. */
class BridgeSimpleSetupContractTest {
    private static String code(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/actionsstuff/bridge/" + name + ".java"));
    }

    @Test void homeExposesClearTwoStepSetupWithoutFalseEnablePackButton() throws Exception {
        var home = code("studio/BridgeDashboardScreen");
        assertTrue(home.contains("1. Import my .mcpack"));
        assertTrue(home.contains("2. Enable Player Motion"));
        assertTrue(home.contains("3. Explore Animations (optional)"));
        assertTrue(home.contains("SourcePlayerMotion.enable(pack)"));
        assertTrue(home.contains("Full Actions & Stuff port: NOT READY"));
        assertTrue(home.contains("NOT enabled in-game"));
        assertFalse(home.contains("ENABLE FULL PACK"));
    }

    @Test void advancedRenderingControlsAreNotOnSimpleHome() throws Exception {
        var home = code("studio/BridgeDashboardScreen");
        var advanced = code("studio/BridgeAdvancedScreen");
        assertTrue(home.contains("new BridgeAdvancedScreen(this)"));
        assertFalse(home.contains("BridgeAnimations.enable()"));
        assertFalse(home.contains("OriginalRigRuntime.enable("));
        assertTrue(advanced.contains("OriginalRigRuntime.enable(pack, selected)"));
        assertTrue(advanced.contains("BridgeAnimations.enable()"));
    }

    @Test void hotkeyOpensSimpleHome() throws Exception {
        var client = code("ActionsStuffBridgeClient");
        assertTrue(client.contains("new BridgeDashboardScreen(client.currentScreen)"));
        assertFalse(client.contains("new BedrockStudioScreen(client.currentScreen)"));
    }
}
