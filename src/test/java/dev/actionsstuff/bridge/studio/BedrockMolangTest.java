package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BedrockMolangTest {
    @Test
    void trigonometryUsesBedrockDegrees() {
        assertEquals(1d, BedrockMolang.evaluate("Math.sin(90)",Map.of()),1e-6);
        assertEquals(-1d,BedrockMolang.evaluate("Math.cos(180)",Map.of()),1e-6);
        assertEquals(2d,BedrockMolang.evaluate("Math.sin(q.anim_time*360)*2",
            Map.of("q.anim_time",0.25d)),1e-6);
    }
    @Test
    void preservesOriginalSourceWalkingFormula() {
        var v=Map.of("q.anim_time",0.25d);
        assertEquals(-35.6, BedrockMolang.evaluate("-Math.sin(q.anim_time*360)*37.2+1.6",v),1e-6);
        assertEquals(-3, BedrockMolang.evaluate("Math.cos(q.anim_time*360+45)*-3",v),1e-6);
    }
    @Test
    void comparisonClampAndTernary() {
        assertEquals(1, BedrockMolang.evaluate("Math.clamp(3,0,1)",Map.of()),1e-6);
        assertEquals(3, BedrockMolang.evaluate("q.is_moving>0 ? 3 : 7",Map.of("q.is_moving",1d)),1e-6);
        assertEquals(7, BedrockMolang.evaluate("q.is_moving>0 ? 3 : 7",Map.of("q.is_moving",0d)),1e-6);
    }
    @Test
    void samplesSourceFramesWithInterpolation() {
        var track=JsonParser.parseString("{\"0.0\":[0,0,0],\"1.0\":[40,0,0]}");
        assertArrayEquals(new float[]{20,0,0},BedrockMolang.sample(track,0.5f,Map.of()),0.01f);
        var expr=JsonParser.parseString("[\"Math.cos(q.anim_time*360)*3\",0,0]");
        assertArrayEquals(new float[]{0,0,0},BedrockMolang.sample(expr,0.25f,
            Map.of("q.anim_time",0.25d)),0.01f);
    }
    @Test
    void rejectsUnknownVariablesAndRunawayTokens() {
        assertThrows(BedrockMolang.ParseFailure.class,()->BedrockMolang.evaluate("v.private_unknown",Map.of()));
        assertThrows(BedrockMolang.ParseFailure.class,()->BedrockMolang.evaluate("1/0",Map.of()));
        assertThrows(BedrockMolang.ParseFailure.class,()->BedrockMolang.evaluate("1".repeat(1100),Map.of()));
    }
}
