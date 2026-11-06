package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Guards the legacy-animation auto-activation and UI overlap regressions. */
class BridgeUiIsolationContractTest {
    private static String source(String suffix) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/actionsstuff/bridge/" + suffix));
    }

    @Test void defaultLegacyPoseLayerIsNotInjectedUnconditionally() throws Exception {
        String source=source("BridgeAnimations.java");
        assertTrue(source.contains("legacyEnabled = false"));
        assertTrue(source.contains("if (!legacyEnabled) return;"));
        assertTrue(source.contains("public static void disable()"));
    }

    @Test void studioHasDistinctControlsAndNoDuplicateBackgroundBlur() throws Exception {
        String source=source("studio/BedrockStudioScreen.java");
        assertTrue(source.contains("this::toggleLegacy"));
        assertTrue(source.contains("OriginalRigRuntime.disable();"));
        assertTrue(source.contains("new BridgeDashboardScreen(this)"));
        assertFalse(source.contains("renderBackground("));
    }

    @Test void manifestRegistersOptionalModMenuEntrypoint() throws Exception {
        String manifest=Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        JsonObject json=JsonParser.parseString(manifest).getAsJsonObject();
        assertTrue(json.getAsJsonObject("entrypoints").has("modmenu"));
        assertTrue(json.getAsJsonObject("suggests").has("modmenu"));
        assertFalse(json.getAsJsonObject("depends").has("modmenu"));
    }
}
