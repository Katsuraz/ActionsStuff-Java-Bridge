package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BedrockControllerMachineTest {
    @Test
    void originalStatesChooseAnimationsAndTransitionOnSupportedQuery() {
        var source = JsonParser.parseString(
          "{\"initial_state\":\"idle\",\"states\":{"
        + "\"idle\":{\"animations\":[\"bob\"],"
        + "\"transitions\":[{\"walking\":\"q.is_moving > 0\"}]},"
        + "\"walking\":{\"animations\":[{\"walk\":\"Math.clamp(q.is_moving,0,1)\"}],"
        + "\"transitions\":[{\"idle\":\"q.is_moving == 0\"}]}}}"
        ).getAsJsonObject();
        var definition=new BedrockPackCatalog.Controller(
            "controller.animation.synthetic",List.of("idle","walking"),source);
        var machine=new BedrockControllerMachine(definition);
        assertEquals("idle",machine.state());
        assertEquals(List.of("bob"),machine.tick(Map.of("q.is_moving",0d)));
        assertEquals(List.of("walk"),machine.tick(Map.of("q.is_moving",1d)));
        assertEquals("walking",machine.state());
        assertEquals(List.of("bob"),machine.tick(Map.of("q.is_moving",0d)));
        assertEquals("idle",machine.state());
    }

    @Test
    void unknownSourceConditionIsNotInventedAsTrue() {
        var source=JsonParser.parseString(
            "{\"states\":{\"idle\":{\"animations\":[\"bob\"],"
          + "\"transitions\":[{\"run\":\"v.unavailable_source_variable\"}]},"
          + "\"run\":{\"animations\":[\"run\"]}}}").getAsJsonObject();
        var machine=new BedrockControllerMachine(new BedrockPackCatalog.Controller(
            "controller.animation.synthetic",List.of("idle","run"),source));
        assertEquals(List.of("bob"),machine.tick(Map.of()));
        assertEquals("idle",machine.state());
    }
}
