package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Safe state-machine subset of original Bedrock animation controllers:
 * initial_state, transitions and animations (including simple weight checks).
 * Unknown MoLang queries do not trigger invented transitions.
 *
 * Does not implement full Bedrock render controllers, state variable scripts,
 * particle/sound timelines, or animation blending.
 */
public final class BedrockControllerMachine {
    private final BedrockPackCatalog.Controller source;
    private final JsonObject states;
    private String current;

    public BedrockControllerMachine(BedrockPackCatalog.Controller source) {
        this.source = source;
        this.states = object(source.originalSource(), "states");
        if(states==null || states.entrySet().isEmpty())throw new IllegalArgumentException("No source controller states");
        current = string(source.originalSource().get("initial_state"));
        if(current==null || !states.has(current))current=states.keySet().iterator().next();
    }

    public String state() {return current;}
    public String id() {return source.id();}

    public List<String> tick(Map<String,Double> variables) {
        if (states == null) return List.of();
        JsonObject currentJson=object(states,current);
        if(currentJson==null)return List.of();
        JsonArray transitions=array(currentJson,"transitions");
        if(transitions!=null) {
            for(int i=0;i<Math.min(64,transitions.size());i++){
                JsonElement candidate=transitions.get(i);
                if(!candidate.isJsonObject())continue;
                for(var entry:candidate.getAsJsonObject().entrySet()){
                    if(!states.has(entry.getKey()))continue;
                    Double value=tryEvaluate(entry.getValue(), variables);
                    if(value!=null && value>0) {
                        current=entry.getKey();
                        currentJson=object(states,current);
                        break;
                    }
                }
                if(currentJson==null)break;
                if(!current.equals(readInitialSourceState(transitions,i))) {
                    // Only one transition per update tick, as in Bedrock.
                    break;
                }
            }
        }
        if(currentJson==null)return List.of();
        JsonArray animations=array(currentJson,"animations");
        if(animations==null)return List.of();
        List<String> result=new ArrayList<>();
        for(int i=0;i<Math.min(32,animations.size());i++){
            JsonElement e=animations.get(i);
            if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()){
                result.add(e.getAsString());
            } else if(e.isJsonObject()) {
                for(var entry:e.getAsJsonObject().entrySet()) {
                    Double weight=tryEvaluate(entry.getValue(),variables);
                    if(weight!=null && weight>0.01)result.add(entry.getKey());
                }
            }
        }
        return List.copyOf(result);
    }

    private String readInitialSourceState(JsonArray transitions,int unused) {
        // Returns a sentinel to end the transition scan once a new state wins;
        // this branch intentionally avoids cascading multiple transitions.
        return "__source_controller_transition_stop__";
    }

    private static Double tryEvaluate(JsonElement element,Map<String,Double> variables){
        if(element==null)return null;
        try{
            if(element.isJsonPrimitive()&&element.getAsJsonPrimitive().isNumber())return element.getAsDouble();
            if(element.isJsonPrimitive()&&element.getAsJsonPrimitive().isBoolean())return element.getAsBoolean()?1d:0d;
            if(element.isJsonPrimitive())return BedrockMolang.evaluate(element.getAsString(),variables);
        }catch(RuntimeException ignored){ }
        return null;
    }

    private static JsonObject object(JsonObject root,String key) {
        JsonElement element=root==null?null:root.get(key);
        return element!=null&&element.isJsonObject()?element.getAsJsonObject():null;
    }
    private static JsonArray array(JsonObject root,String key){
        JsonElement element=root==null?null:root.get(key);
        return element!=null&&element.isJsonArray()?element.getAsJsonArray():null;
    }
    private static String string(JsonElement element){
        try{return element!=null&&element.isJsonPrimitive()?element.getAsString():null;}
        catch(RuntimeException ex){return null;}
    }
}
