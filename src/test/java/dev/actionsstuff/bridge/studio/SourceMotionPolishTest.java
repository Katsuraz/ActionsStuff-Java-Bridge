package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonParser;
import dev.actionsstuff.bridge.SourceMotionTiming;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SourceMotionPolishTest {
    @Test void strideClockStaysContinuousAcrossWalkingAndSprinting() {
        float clock = 0f;
        for(int tick=0;tick<10;tick++)
            clock=SourceMotionTiming.advance(clock,1f,1f);
        assertEquals(0.5f,clock,0.001f);
        float speed=SourceMotionTiming.rate(1f,0.24f,true,1f);
        assertTrue(speed>1f);
        float next=SourceMotionTiming.advance(clock,1f,speed);
        assertTrue(next>clock);
        assertTrue(next<0.61f);
    }

    @Test void sourceClipTransitionsFadeSmoothlyInsteadOfSnapping() {
        assertEquals(0f,SourceMotionTiming.crossfade(100,100,5),0.001f);
        assertEquals(0.5f,SourceMotionTiming.crossfade(102.5f,100,5),0.001f);
        assertEquals(1f,SourceMotionTiming.crossfade(105,100,5),0.001f);
        assertEquals(1f,SourceMotionTiming.crossfade(108,100,5),0.001f);
    }

    @Test void originalCatmullromFrameInterpolatesCurvedInsteadOfLinear() {
        var track=JsonParser.parseString("{"
           + "\"0.00\":[0,0,0],"
           + "\"0.25\":{\"post\":[0,0,0],\"lerp_mode\":\"catmullrom\"},"
           + "\"0.50\":{\"post\":[50,0,0],\"lerp_mode\":\"catmullrom\"},"
           + "\"0.75\":[50,0,0]}"
        );
        float[] curved=BedrockMolang.sample(track,0.3125f,Map.of("q.anim_time",0.3125d));
        assertNotNull(curved);
        assertTrue(curved[0]>0f && curved[0]<12.5f);
        assertEquals(0f,BedrockMolang.sample(track,0.25f,Map.of())[0],0.01f);
        assertEquals(50f,BedrockMolang.sample(track,0.5f,Map.of())[0],0.01f);
    }

    @Test void originalSourcePlayerOnlyChangesRotationAndProtectsEquippedHand() throws Exception {
        String code=Files.readString(Path.of("src/main/java/dev/actionsstuff/bridge/SourcePlayerMotion.java"));
        assertTrue(code.contains("SourceMotionTiming.advance("));
        assertTrue(code.contains("SourceMotionTiming.crossfade("));
        assertTrue(code.contains("self.getMainHandStack().isEmpty()"));
        assertTrue(code.contains("self.getMainArm()"));
        assertTrue(code.contains("previousBones"));
        assertFalse(code.contains("part.originX +="));
        assertFalse(code.contains("part.originY +="));
        assertFalse(code.contains("part.xScale ="));
    }
}
