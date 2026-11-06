package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Tests source clip shape observed in the original user's locally provided pack.
 * Uses synthetic values, never copies or redistributes copyrighted animation data.
 */
class SourcePlayerMotionTest {
    @Test void originalBedrockArmTracksAreAnimatedInsteadOfDiscarded() {
        var expression=JsonParser.parseString(
            "[\"Math.sin(q.anim_time*360)*35\",0,0]");
        float[] atQuarter=BedrockMolang.sample(expression,0.25f,
            Map.of("q.anim_time",0.25d));
        float[] atThreeQuarter=BedrockMolang.sample(expression,0.75f,
            Map.of("q.anim_time",0.75d));
        assertNotNull(atQuarter);
        assertNotNull(atThreeQuarter);
        assertEquals(35f,atQuarter[0],0.001f);
        assertEquals(-35f,atThreeQuarter[0],0.001f);
    }

    @Test void originalTimedLegFramesAreInterpolated() {
        var keyframes=JsonParser.parseString(
            "{\"0.0\":[30,0,0],\"0.25\":[-30,0,0],\"0.5\":[30,0,0]}");
        assertEquals(0f,BedrockMolang.sample(keyframes,0.125f,
            Map.of("q.anim_time",0.125d))[0],0.01f);
    }

    @Test void playerMotionUsesBedrockCanonicalNamesAndNoFloatingOffsets() throws Exception {
        String code=Files.readString(Path.of(
            "src/main/java/dev/actionsstuff/bridge/SourcePlayerMotion.java"));
        assertTrue(code.contains("\"rightarm\""));
        assertTrue(code.contains("\"leftarm\""));
        assertTrue(code.contains("\"rightleg\""));
        assertTrue(code.contains("\"leftleg\""));
        assertTrue(code.contains("BedrockMolang.sample("));
        assertTrue(code.contains("SourcePlayerMotion"));
        assertFalse(code.contains("part.originX +="));
        assertFalse(code.contains("part.originY +="));
        assertFalse(code.contains("part.xScale ="));
        assertFalse(code.contains("part.yScale ="));
    }
}
