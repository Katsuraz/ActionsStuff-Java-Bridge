package dev.actionsstuff.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.actionsstuff.bridge.studio.BedrockMolang;
import dev.actionsstuff.bridge.studio.BedrockPackCatalog;
import dev.actionsstuff.bridge.studio.OriginalRigRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Working FIRST PLAYER MOTION bridge for user-supplied original Bedrock clips.
 *
 * Unlike the old numeric-only Legacy Player Poses implementation, this reads
 * original source MoLang equations and original Bedrock bone naming directly:
 * rightarm / leftarm / rightleg / leftleg (NOT right_arm).
 *
 * Safe design: ONLY rotation tracks from known humanoid bones are applied to
 * the existing Java player. No custom geometry, origins, scales, item bones,
 * equipment pivots or accessories are altered. That avoids the old legacy
 * adapter's cyan detached layer/held-item artifacts.
 *
 * This is source-driven player MOTION, not the full Actions & Stuff pack.
 */
public final class SourcePlayerMotion {
    private static final List<String> ALIASES = List.of(
        "bob", "walk", "move", "jump", "fall", "swim", "fly",
        "attack", "hurt", "eat", "use", "sneak", "death", "run");
    private static final List<String> HUMANOID_BONES = List.of(
        "head", "body", "waist", "rightarm", "leftarm", "rightleg", "leftleg");
    private static volatile Active active;
    private static final Map<Integer,Playback> players = new HashMap<>();
    private static String recentPhase = "none";
    private SourcePlayerMotion() { }

    public static boolean enabled() { return active != null; }
    public static int playableClipCount() { return active == null ? 0 : active.clips.size(); }
    public static String recentPhase() { return recentPhase; }
    public static void disable() {
        active = null;
        players.clear();
        recentPhase = "off";
    }

    public static String enable(BedrockPackCatalog catalog) {
        if (catalog == null) return "Place and import your original .mcpack first.";
        var player = catalog.entities.get("minecraft:player");
        if (player == null) return "This pack has no Minecraft player animation definition.";

        Map<String,BedrockPackCatalog.Animation> source = new LinkedHashMap<>();
        for (String alias : ALIASES) {
            String ref = player.animations().get(alias);
            if (ref == null) continue;
            BedrockPackCatalog.Animation animation = catalog.animations.get(ref);
            if (animation == null || !hasHumanRotation(animation.originalSource())) continue;
            source.put(alias, animation);
        }
        if (!source.containsKey("walk") && !source.containsKey("move"))
            return "No source walk/move animation with humanoid rotation channels found.";
        if (source.size() < 2) return "Not enough source movement states to animate the player.";

        // Perform transition only AFTER successful validation; preserve old setup
        // if importing an unsupported source pack fails.
        BridgeAnimations.disable();
        OriginalRigRuntime.disable();
        active = new Active(catalog.filename, Map.copyOf(source));
        players.clear();
        recentPhase = "ready";
        BridgeAnimations.LOG.info("Source player motion enabled from local pack {}; {} original clips",
            catalog.filename, source.size());
        return "Source player motion ON: " + source.size()
            + " original clips; MoLang arm/leg motion enabled; textures & accessories remain unsupported.";
    }

    /** No copyright content is packaged; this runs exclusively on the local user's imported pack. */
    private static boolean hasHumanRotation(JsonObject original) {
        JsonObject bones = object(original, "bones");
        if (bones == null) return false;
        for (String name : HUMANOID_BONES) {
            JsonObject bone = object(bones, name);
            if (bone != null && bone.has("rotation")) return true;
        }
        return false;
    }

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState render) {
        Active session = active;
        if (session == null || render.spectator || !Float.isFinite(render.age)) return;
        // First milestone is local player third-person only. Remote player
        // animation requires proper state/entity synchronization.
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (self == null || render.id != self.getId()) return;

        String phase = choose(render, self, session.clips);
        if (phase == null) return;
        BedrockPackCatalog.Animation clip = session.clips.get(phase);
        if (clip == null) return;

        Playback playback = players.computeIfAbsent(render.id, ignored -> new Playback());
        if (players.size() > 8) players.clear();
        float age = render.age;
        boolean reset = Float.isFinite(playback.lastAge) && age < playback.lastAge - 5f;
        if (reset) playback.reset();
        float deltaTicks = Float.isFinite(playback.lastAge)
            ? clamp(age - playback.lastAge, 0f, 3f) : 0f;

        // Bedrock animation source has a single 1-second stride, but Steve can
        // walk or sprint at different speeds. Keep the clock continuous when
        // switching walk <-> move so neither leg snaps back to frame zero.
        boolean locomotion = isLocomotion(phase);
        float speed = (float)self.getVelocity().horizontalLength();
        float targetRate = clamp(speed / (self.isSprinting() ? 0.18f : 0.11f),0.7f,1.7f);
        if (!Float.isFinite(playback.strideRate)) playback.strideRate = 1f;
        playback.strideRate = lerp(playback.strideRate,targetRate,clamp(deltaTicks * 0.14f,0,0.4f));

        if (!phase.equals(playback.phase)) {
            playback.previousPhase = playback.phase;
            playback.previousSeconds = playback.lastSampleSeconds;
            playback.phase = phase;
            playback.startedAt = age;
            playback.changedAt = age;
            if (locomotion && !isLocomotion(playback.previousPhase)) {
                playback.strideSeconds = 0f;
            }
        }
        if (locomotion) playback.strideSeconds += deltaTicks / 20f * playback.strideRate;
        else if (playback.previousPhase != null && !locomotion && !isLocomotion(playback.previousPhase)) {
            playback.strideSeconds = 0f;
        }
        playback.lastAge = age;
        recentPhase = phase;

        float localSeconds = locomotion ? playback.strideSeconds
            : Math.max(0f,(age - playback.startedAt) / 20f);
        float seconds = clipTime(clip,localSeconds);
        playback.lastSampleSeconds = seconds;
        Map<String,Double> vars = variables(render,age,seconds);

        JsonObject bones = object(clip.originalSource(), "bones");
        if (bones == null) return;
        BedrockPackCatalog.Animation previousClip = playback.previousPhase == null
            ? null : session.clips.get(playback.previousPhase);
        JsonObject previousBones = previousClip == null ? null
            : object(previousClip.originalSource(), "bones");
        float priorSeconds = previousClip == null ? 0f
            : clipTime(previousClip,playback.previousSeconds);
        Map<String,Double> oldVars = variables(render,age,priorSeconds);

        float fade = smoothstep(clamp((age - playback.changedAt) / 5f,0f,1f));
        if (fade >= 1f) {
            previousBones = null;
            fade = 1f;
        }
        // Keep the vanilla head and held-item poses. Source rotations are
        // blended onto those existing parts; never modify origins or scale.
        float headBlend = 0.26f;
        float bodyBlend = 0.25f;
        float limbBlend = 0.82f;

        // The user's video shows the equipped sword arm looking unnaturally
        // lifted during normal walking. Protect just the equipped hand; leave
        // the empty other arm free to use the source motion. Attack clips still
        // animate the sword arm, but don't fully replace Minecraft's hit pose.
        boolean activeSwing = render.handSwingProgress > 0.04f;
        float heldWeight = render.isUsingItem ? 0.14f : activeSwing ? 0.65f : 0.42f;
        boolean rightMain = self.getMainArm() == net.minecraft.util.Arm.RIGHT;
        float rightHeld = (rightMain && !self.getMainHandStack().isEmpty())
            || (!rightMain && !self.getOffHandStack().isEmpty()) ? heldWeight : 1f;
        float leftHeld = (!rightMain && !self.getMainHandStack().isEmpty())
            || (rightMain && !self.getOffHandStack().isEmpty()) ? heldWeight : 1f;

        sampleAndPose(model.head,bones,previousBones,"head",seconds,priorSeconds,
            vars,oldVars,fade,headBlend,45);
        if (object(bones,"body") != null) {
            sampleAndPose(model.body,bones,previousBones,"body",seconds,priorSeconds,
                vars,oldVars,fade,bodyBlend,50);
        } else {
            sampleAndPose(model.body,bones,previousBones,"waist",seconds,priorSeconds,
                vars,oldVars,fade,bodyBlend,35);
        }
        sampleAndPose(model.rightArm,bones,previousBones,"rightarm",seconds,priorSeconds,
            vars,oldVars,fade,limbBlend*rightHeld,125);
        sampleAndPose(model.leftArm,bones,previousBones,"leftarm",seconds,priorSeconds,
            vars,oldVars,fade,limbBlend*leftHeld,125);
        sampleAndPose(model.rightLeg,bones,previousBones,"rightleg",seconds,priorSeconds,
            vars,oldVars,fade,limbBlend,100);
        sampleAndPose(model.leftLeg,bones,previousBones,"leftleg",seconds,priorSeconds,
            vars,oldVars,fade,limbBlend,100);

        // Clothing layers follow the rotations only, not the source's
        // translations/scales (the latter caused offset geometry in legacy).
        copyRotation(model.rightArm, model.rightSleeve);
        copyRotation(model.leftArm, model.leftSleeve);
        copyRotation(model.rightLeg, model.rightPants);
        copyRotation(model.leftLeg, model.leftPants);
        copyRotation(model.body, model.jacket);
        copyRotation(model.head, model.hat);
    }

    private static float clipTime(BedrockPackCatalog.Animation clip,float seconds) {
        return clip.looping() && clip.seconds() > 0f
            ? seconds % clip.seconds() : Math.min(Math.max(0f,seconds),clip.seconds());
    }
    private static boolean isLocomotion(String phase) {
        return "walk".equals(phase) || "move".equals(phase) || "run".equals(phase);
    }
    private static float smoothstep(float t) { return t*t*(3f-2f*t); }

    private static Map<String,Double> variables(PlayerEntityRenderState render,float age,float sec) {
        Map<String,Double> vars = new HashMap<>();
        vars.put("q.anim_time",(double)sec);
        vars.put("query.anim_time",(double)sec);
        vars.put("q.life_time",age/20d);
        vars.put("query.life_time",age/20d);
        vars.put("q.is_moving",render.limbSwingAmplitude>0.04f?1d:0d);
        vars.put("q.ground_speed",(double)render.limbSwingAmplitude);
        vars.put("q.modified_move_speed",(double)render.limbSwingAmplitude);
        vars.put("q.is_sneaking",render.isInSneakingPose?1d:0d);
        vars.put("q.is_swimming",render.isSwimming?1d:0d);
        vars.put("q.is_using_item",render.isUsingItem?1d:0d);
        return vars;
    }

    private static float[] rotation(JsonObject bones,String bone,float time,Map<String,Double> vars) {
        JsonObject source = object(bones,bone);
        return source == null ? null : BedrockMolang.sample(source.get("rotation"),time,vars);
    }

    private static void sampleAndPose(ModelPart part,JsonObject bones,JsonObject previousBones,
                                      String bone,float time,float priorTime,
                                      Map<String,Double> vars,Map<String,Double> oldVars,
                                      float fade,float weight,float maxDegrees) {
        float[] target = rotation(bones,bone,time,vars);
        float[] prior = previousBones == null ? null
            : rotation(previousBones,bone,priorTime,oldVars);
        if (target == null && prior == null) return;
        float[] blended = new float[3];
        for (int i=0;i<3;i++) {
            float oldAngle = prior == null ? 0f : prior[i];
            float newAngle = target == null ? 0f : target[i];
            blended[i] = lerp(oldAngle,newAngle,fade);
        }
        float pitch = radians(clamp(blended[0],-maxDegrees,maxDegrees));
        float yaw = radians(clamp(-blended[1],-50f,50f));
        float roll = radians(clamp(-blended[2],-55f,55f));
        if (bone.equals("head")) {
            part.pitch += pitch * weight;
            part.yaw += yaw * weight * 0.25f;
            part.roll += roll * weight;
        } else {
            part.pitch = lerp(part.pitch,pitch,weight);
            part.yaw = lerp(part.yaw,yaw,weight);
            part.roll = lerp(part.roll,roll,weight);
        }
    }

    private static String choose(PlayerEntityRenderState state, ClientPlayerEntity player,
                                 Map<String,BedrockPackCatalog.Animation> clips) {
        if (state.deathTime > 0) return first(clips,"death","hurt","bob");
        if (state.hurt) return first(clips,"hurt","bob");
        if (state.isSwimming) return first(clips,"swim","bob");
        if (state.isGliding) return first(clips,"fly","fall","bob");
        if (state.handSwingProgress > 0.04) return first(clips,"attack","bob");
        if (state.isUsingItem) return first(clips,"eat","use","bob");
        if (state.isInSneakingPose) return first(clips,"sneak","walk","bob");
        if (!player.isOnGround() && player.getVelocity().y > 0.04)
            return first(clips,"jump","fall","bob");
        if (!player.isOnGround() && player.getVelocity().y < -0.05)
            return first(clips,"fall","jump","bob");
        if (state.limbSwingAmplitude > 0.055f) {
            if (player.isSprinting()) return first(clips,"move","run","walk","bob");
            return first(clips,"walk","move","bob");
        }
        return first(clips,"bob","walk");
    }

    private static String first(Map<String,?> clips,String... aliases) {
        for(String alias:aliases) if(clips.containsKey(alias)) return alias;
        return null;
    }
    private static JsonObject object(JsonObject root,String key) {
        JsonElement element = root==null?null:root.get(key);
        return element!=null&&element.isJsonObject()?element.getAsJsonObject():null;
    }
    private static void copyRotation(ModelPart main,ModelPart overlay) {
        overlay.pitch=main.pitch;
        overlay.yaw=main.yaw;
        overlay.roll=main.roll;
    }
    private static float radians(float deg) {return (float)Math.toRadians(deg);}
    private static float lerp(float a,float b,float amount){return a+(b-a)*amount;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}

    private record Active(String filename,Map<String,BedrockPackCatalog.Animation> clips) { }
    private static final class Playback {
        String phase,previousPhase;
        float startedAt,changedAt,strideSeconds,previousSeconds,lastSampleSeconds;
        float lastAge=Float.NaN;
        float strideRate=1f;
        void reset() {
            phase=previousPhase=null;
            startedAt=changedAt=strideSeconds=previousSeconds=lastSampleSeconds=0f;
            lastAge=Float.NaN;
            strideRate=1f;
        }
    }
}
