package dev.actionsstuff.bridge.studio;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against misleading ON/OFF labels for unsupported source geometries
 * and ensures that Mod Menu can enter the 3D enable path rather than only disable.
 * Minecraft UI behavior still requires a real in-game smoke test.
 */
class BridgeToggleFeedbackTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/actionsstuff/bridge/studio/", file + ".java"));
    }

    @Test void controllerRequiresAnActiveRigAndActuallyParsedControllerDefinitions() throws Exception {
        String runtime = read("OriginalRigRuntime");
        assertTrue(runtime.contains("active != null && !active.machines.isEmpty()"));
        assertTrue(runtime.contains("if (!controllersAvailable())"));
        assertTrue(runtime.contains("controllerBlockReason()"));
    }

    @Test void studioReportsBlockedModelsAndRealToggleStates() throws Exception {
        String ui = read("BedrockStudioScreen");
        assertTrue(ui.contains("3D: BLOCKED"));
        assertTrue(ui.contains("Controllers: NEED 3D"));
        assertTrue(ui.contains("3D BLOCKED: "));
        assertTrue(ui.contains("refreshToggleLabels()"));
        assertTrue(ui.contains("BedrockModelSafety.assess(catalog, selected)"));
    }

    @Test void modMenuHas3dEnablePathAndShowsWhyControllersAreBlocked() throws Exception {
        String dashboard = read("BridgeAdvancedScreen");
        assertTrue(dashboard.contains("OriginalRigRuntime.enable(pack, selected)"));
        assertTrue(dashboard.contains("OriginalRigRuntime.controllerBlockReason()"));
        assertTrue(dashboard.contains("3D overlay: BLOCKED"));
        assertFalse(dashboard.contains("ButtonWidget.builder(Text.literal(\"Disable 3D overlay\")"));
    }
}
