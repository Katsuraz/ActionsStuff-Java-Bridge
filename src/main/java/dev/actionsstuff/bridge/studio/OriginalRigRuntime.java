package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.model.ModelPart;
import dev.actionsstuff.bridge.SourcePlayerMotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Opt-in client-only renderer state for ORIGINAL Bedrock cube geometry.
 * The source pack stays on the player's machine, never bundled into the JAR.
 *
 * Supports hierarchy, static/rest pivots, numeric and a useful MoLang
 * subset; it does NOT claim full source controller/render-controller fidelity.
 */
public final class OriginalRigRuntime {
    private static Active active;
    private static boolean controllerLayers;
    private OriginalRigRuntime() {}

    public static boolean enabled() { return active != null; }
    public static void disable() {
        active = null;
        controllerLayers = false;
    }
    public static int controllerCount() { return active == null ? 0 : active.machines.size(); }
    public static boolean controllersAvailable() { return active != null && !active.machines.isEmpty(); }
    public static boolean controllersEnabled() { return controllersAvailable() && controllerLayers; }
    public static String controllerBlockReason() {
        if (active == null) return "3D renderer is inactive. Enable a supported full-body model first.";
        if (active.machines.isEmpty())
            return "The active source rig has no supported Bedrock animation controllers.";
        return "";
    }
    public static boolean toggleControllers() {
        // Controllers cannot drive a model that never passed the source safety gate.
        if (!controllersAvailable()) {
            controllerLayers = false;
            return false;
        }
        return controllerLayers = !controllerLayers;
    }
    public static String geometryId() {return active==null?"none":active.geometry.id();}

    public static String enable(BedrockPackCatalog catalog, BedrockPackCatalog.Geometry geometry) {
        // Never overwrite the working renderer until the source rig is valid.
        if (catalog == null) return "No local pack imported";
        if (geometry == null) geometry = BedrockCubeModel.choosePlayerGeometry(catalog);
        if (geometry == null) {
            disable();
            return "Source player uses built-in Bedrock body or conditional accessory layers; no safe standalone 3D mesh";
        }
        BedrockModelSafety.Assessment eligibility = BedrockModelSafety.assess(catalog, geometry);
        if (!eligibility.worldSafe()) {
            disable();
            return eligibility.reason();
        }
        try {
            BedrockCubeModel model = BedrockCubeModel.build(geometry);
            Map<String,BedrockPackCatalog.Animation> clips = new HashMap<>();
            var player = catalog.entities.get("minecraft:player");
            if (player != null) {
                for (var alias : player.animations().entrySet()) {
                    BedrockPackCatalog.Animation clip = catalog.animations.get(alias.getValue());
                    if (clip != null) clips.put(alias.getKey().toLowerCase(Locale.ROOT),clip);
                }
            }
            var machines = new ArrayList<BedrockControllerMachine>();
            if (player != null) {
                for (String id : player.animations().values()) {
                    if (!id.startsWith("controller.animation.")) continue;
                    var definition = catalog.controllers.get(id);
                    if (definition == null || machines.size() >= 64) continue;
                    try { machines.add(new BedrockControllerMachine(definition)); }
                    catch (RuntimeException ignored) { }
                }
            }
            // Mutually exclusive: do not overlay an old source-mapped vanilla pose.
            SourcePlayerMotion.disable();
            active = new Active(geometry,model,Map.copyOf(clips),List.copyOf(machines));
            controllerLayers = false;
            return "3D original: " + model.parts.size() + " bones, "
                + model.cubeCount + " cubes, " + clips.size() + " source clips, "
                + active.machines.size() + " controller definitions";
        } catch (RuntimeException ex) {
            disable();
            return "3D import rejected: " + ex.getMessage();
        }
    }

    public static BedrockCubeModel pose(PlayerEntityRenderState state) {
        Active data=active;
        if(data==null || state==null) return null;
        String phase=choose(data.clips,state);
        if(phase==null) return data.model;
        BedrockPackCatalog.Animation animation = data.clips.get(phase);
        if (animation == null) return data.model;
        if (!phase.equals(data.phase) || state.age < data.lastAge - 5) {
            data.phase=phase;
            data.startAge=state.age;
        }
        data.lastAge=state.age;
        float sec=Math.max(0,(state.age-data.startAge)/20f);
        if(animation.looping()&&animation.seconds()>0)sec %=animation.seconds();
        else sec=Math.min(sec,animation.seconds());
        data.model.reset();
        Map<String,Double> variables=variables(state,sec);

        applyAnimation(data.model,animation,sec,variables);
        if(controllerLayers) {
            for (BedrockControllerMachine machine : data.machines) {
                for (String alias : machine.tick(variables)) {
                    BedrockPackCatalog.Animation layered = data.clips.get(alias.toLowerCase(Locale.ROOT));
                    if(layered == null) continue;
                    float controllerTime = Math.max(0,state.age/20f);
                    if(layered.looping() && layered.seconds() > 0)
                        controllerTime %= layered.seconds();
                    else controllerTime=Math.min(controllerTime,layered.seconds());
                    variables.put("q.anim_time",(double)controllerTime);
                    applyAnimation(data.model,layered,controllerTime,variables);
                }
            }
        }
        return data.model;
    }

    private static void applyAnimation(BedrockCubeModel model,
            BedrockPackCatalog.Animation animation,float sec, Map<String,Double> variables) {
        JsonObject bones=getObject(animation.originalSource(),"bones");
        if(bones==null)return;
        for (var entry : bones.entrySet()) {
            ModelPart part=model.parts.get(entry.getKey());
            if(part==null||!entry.getValue().isJsonObject())continue;
            JsonObject tracks=entry.getValue().getAsJsonObject();
            float[] rotation=BedrockMolang.sample(tracks.get("rotation"),sec,variables);
            if(rotation!=null) {
                part.pitch+=(float)Math.toRadians(rotation[0]);
                part.yaw+=(float)Math.toRadians(-rotation[1]);
                part.roll+=(float)Math.toRadians(-rotation[2]);
            }
            float[] position=BedrockMolang.sample(tracks.get("position"),sec,variables);
            if(position!=null) {
                part.originX+=position[0];
                part.originY-=position[1];
                part.originZ-=position[2];
            }
            float[] scale=BedrockMolang.sample(tracks.get("scale"),sec,variables);
            if(scale!=null) {
                part.xScale*=clamp(scale[0],0.05f,5);
                part.yScale*=clamp(scale[1],0.05f,5);
                part.zScale*=clamp(scale[2],0.05f,5);
            }
        }
    }

    private static String choose(Map<String,BedrockPackCatalog.Animation> a,PlayerEntityRenderState state) {
        if(state.deathTime>0)return first(a,"death","hurt","idle","bob");
        if(state.hurt)return first(a,"hurt","idle","bob");
        if(state.isSwimming)return first(a,"swim","idle","bob");
        if(state.isGliding)return first(a,"fly","idle","bob");
        if(state.handSwingProgress>0.04f)return first(a,"attack","walk","bob");
        if(state.isUsingItem)return first(a,"eat","use","bob");
        if(state.isInSneakingPose)return first(a,"sneak","walk","bob");
        if(state.limbSwingAmplitude>0.05f) {
            if(state.limbSwingAmplitude>0.5f)return first(a,"run","walk","move","bob");
            return first(a,"walk","move","bob");
        }
        return first(a,"bob","idle","humanoid_base_pose");
    }

    private static String first(Map<String,?> map,String... names) {
        for(String name:names)if(map.containsKey(name))return name;
        return null;
    }

    private static Map<String,Double> variables(PlayerEntityRenderState state,float seconds) {
        Map<String,Double> v=new HashMap<>();
        v.put("q.anim_time",(double)seconds);
        v.put("query.anim_time",(double)seconds);
        v.put("q.life_time",state.age/20.0);
        v.put("query.life_time",state.age/20.0);
        v.put("q.modified_move_speed",(double)state.limbSwingAmplitude);
        v.put("q.ground_speed",(double)state.limbSwingAmplitude);
        v.put("q.is_sneaking",state.isInSneakingPose?1d:0d);
        v.put("q.is_swimming",state.isSwimming?1d:0d);
        v.put("q.is_using_item",state.isUsingItem?1d:0d);
        v.put("q.is_on_ground",state.isGliding?0d:1d);
        v.put("q.yaw_speed",0d);
        v.put("q.is_moving",state.limbSwingAmplitude>0.05?1d:0d);
        return v;
    }

    private static JsonObject getObject(JsonObject parent,String key) {
        JsonElement value=parent.get(key);
        return value!=null && value.isJsonObject()?value.getAsJsonObject():null;
    }
    private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}

    private static final class Active {
        final BedrockPackCatalog.Geometry geometry;
        final BedrockCubeModel model;
        final Map<String,BedrockPackCatalog.Animation> clips;
        final List<BedrockControllerMachine> machines;
        String phase;
        float startAge,lastAge=Float.NEGATIVE_INFINITY;
        Active(BedrockPackCatalog.Geometry geometry,BedrockCubeModel model,
               Map<String,BedrockPackCatalog.Animation> clips,
               List<BedrockControllerMachine> machines) {
            this.geometry=geometry;
            this.model=model;
            this.clips=clips;
            this.machines=machines;
        }
    }
}
