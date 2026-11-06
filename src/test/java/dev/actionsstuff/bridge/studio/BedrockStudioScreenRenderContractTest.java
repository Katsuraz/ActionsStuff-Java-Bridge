package dev.actionsstuff.bridge.studio;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Prevent reintroducing the 1.21.11 "Can only blur once per frame"
 * crash observed immediately after the Studio hotkey.
 *
 * A source-level contract check is intentionally small: actual client
 * rendering still requires an in-game smoke test.
 */
class BedrockStudioScreenRenderContractTest {
    @Test
    void studioDrawsItsOwnOpaqueBackdropWithoutBlur() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/actionsstuff/bridge/studio/BedrockStudioScreen.java"));
        assertTrue(source.contains("ctx.fill(0, 0, width, height, BG)"));
        assertFalse(source.contains("renderBackground("),
            "Studio's render() must not call Minecraft's blur background");
    }
}
