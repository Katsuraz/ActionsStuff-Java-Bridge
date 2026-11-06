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
        if (!phase.equals(playback.phase) || age < playback.lastAge - 5) {
            playback.phase = phase;
            playback.startedAt = age;
            playback.changedAt = age;
        }
        playback.lastAge = age;
        recentPhase = phase;

        float localSeconds = Math.max(0,(age - playback.startedAt) / 20f);
        float seconds = clip.looping() && clip.seconds() > 0
            ? localSeconds % clip.seconds() : Math.min(localSeconds,clip.seconds());
        Map<String,Double> vars = new HashMap<>();
        vars.put("q.anim_time",(double)seconds);
        vars.put("query.anim_time",(double)seconds);
        vars.put("q.life_time",age / 20d);
        vars.put("query.life_time",age / 20d);
        vars.put("q.is_moving",render.limbSwingAmplitude > 0.04 ? 1d : 0d);
        vars.put("q.ground_speed",(double)render.limbSwingAmplitude);
        vars.put("q.modified_move_speed",(double)render.limbSwingAmplitude);
        vars.put("q.is_sneaking",render.isInSneakingPose ? 1d : 0d);
        vars.put("q.is_swimming",render.isSwimming ? 1d : 0d);
        vars.put("q.is_using_item",render.isUsingItem ? 1d : 0d);

        JsonObject bones = object(clip.originalSource(), "bones");
        if (bones == null) return;

        float transition = Math.min(1f, Math.max(0f,(age-playback.changedAt) / 5f));
        // Preserve the vanilla base pose for item use, look direction and
        // awkward Minecraft body transformations; apply only bounded rotations.
        float headBlend = 0.24f * transition;
        float bodyBlend = 0.40f * transition;
        float limbBlend = 0.83f * transition;
        float holding = render.isUsingItem || render.handSwingProgress > 0.04 ? 0.16f : 1f;

        sampleAndPose(model.head, bones, "head", seconds,vars, headBlend, 45);
        // The source waist is a separate parent bone, but vanilla has no waist.
        // Apply the BODY's rotation to its own vanilla body; waist yaw/roll is a
        // small secondary influence rather than displacing player geometry.
        if (object(bones,"body") != null)
            sampleAndPose(model.body,bones,"body",seconds,vars,bodyBlend,55);
        else
            sampleAndPose(model.body,bones,"waist",seconds,vars,bodyBlend,35);
        sampleAndPose(model.rightArm,bones,"rightarm",seconds,vars,limbBlend*holding,125);
        sampleAndPose(model.leftArm,bones,"leftarm",seconds,vars,limbBlend*holding,125);
        sampleAndPose(model.rightLeg,bones,"rightleg",seconds,vars,limbBlend,100);
        sampleAndPose(model.leftLeg,bones,"leftleg",seconds,vars,limbBlend,100);

        // Clothing layers follow the rotations only, not the source's
        // translations/scales (the latter caused offset geometry in legacy).
        copyRotation(model.rightArm, model.rightSleeve);
        copyRotation(model.leftArm, model.leftSleeve);
        copyRotation(model.rightLeg, model.rightPants);
        copyRotation(model.leftLeg, model.leftPants);
        copyRotation(model.body, model.jacket);
        copyRotation(model.head, model.hat);
    }

    private static void sampleAndPose(ModelPart part, JsonObject bones, String bone,
                                      float time,Map<String,Double> vars,
                                      float weight,float maxDegrees) {
        JsonObject source = object(bones,bone);
        if (source == null) return;
        float[] rotation = BedrockMolang.sample(source.get("rotation"),time,vars);
        if (rotation == null) return;
        // Bedrock's animation frame angles are absolute. On the Java humanoid
        // they blend into the existing vanilla pose without moving item pivots.
        float pitch = radians(clamp(rotation[0],-maxDegrees,maxDegrees));
        float yaw = radians(clamp(-rotation[1],-65,65));
        float roll = radians(clamp(-rotation[2],-65,65));
        if (bone.equals("head")) {
            // Keep mouse-look tracking from Minecraft's original head pose.
            part.pitch += pitch * weight;
            part.yaw += yaw * weight * 0.3f;
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
        String phase;
        float startedAt,changedAt,lastAge=Float.NEGATIVE_INFINITY;
    }
}
