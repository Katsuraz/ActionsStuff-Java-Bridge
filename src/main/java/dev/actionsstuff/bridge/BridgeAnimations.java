package dev.actionsstuff.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Preliminary local-only original-keyframe animation playback.
 * This prototype supports vanilla biped rotations, not custom geometry,
 * complete Bedrock animation controllers, or Molang evaluation.
 */
public final class BridgeAnimations {
    public static final Logger LOG = LoggerFactory.getLogger("ActionsStuffBridge");
    private static volatile Map<String, ClipSet> assets = Map.of();
    private static final WeakHashMap<LivingEntity, AnimState> states = new WeakHashMap<>();
    private static final double MOVING_THRESHOLD = 0.0015;
    private static final float DEFAULT_MIX = 0.75f;

    private BridgeAnimations() {}

    public static void reload(ResourceManager resources) {
        Map<String, ClipSet> next = new HashMap<>();
        resources.findResources("animations", id -> id.getNamespace().equals("actions_stuff_bridge")
                && id.getPath().endsWith(".json"))
            .forEach((id, res) -> {
                try (InputStreamReader in = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
                    ClipSet clips = ClipSet.fromJson(root);
                    String filename = id.getPath().substring("animations/".length());
                    if (filename.startsWith("mobs/")) filename = filename.substring("mobs/".length());
                    if (filename.endsWith(".json")) filename = filename.substring(0, filename.length() - 5);
                    if (!clips.clips.isEmpty()) next.put(filename, clips);
                } catch (Exception err) {
                    LOG.warn("Skipping malformed animation source {}: {}", id, err.toString());
                }
            });
        assets = Map.copyOf(next);
        states.clear();
        LOG.info("Loaded {} original-source animation sets", next.size());
    }

    public static void apply(EntityModel<?> model, LivingEntity entity, float tickDelta) {
        if (!(model instanceof BipedEntityModel<?> biped)) return;
        String name = entity instanceof PlayerEntity ? "player"
            : Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
        ClipSet clips = assets.get(name);
        if (clips == null) return;

        String phase = choosePhase(clips, entity);
        if (phase == null) return;
        Clip clip = clips.clips.get(phase);
        if (clip == null) return;
        AnimState state = states.computeIfAbsent(entity, key -> new AnimState());
        float tick = entity.age + tickDelta;
        if (!phase.equals(state.phase)) {
            state.phase = phase;
            state.startTick = tick;
        }
        float elapsed = Math.max(0, (tick - state.startTick) / 20f);
        float seconds = clip.loop && clip.duration > 0
            ? elapsed % clip.duration : Math.min(elapsed, clip.duration);

        for (Map.Entry<String, Bone> it : clip.bones.entrySet()) {
            ModelPart part = boneToVanillaPart(biped, it.getKey());
            if (part == null) continue;
            float[] rotation = it.getValue().rotation == null
                ? null : it.getValue().rotation.at(seconds);
            if (rotation == null) continue;
            if (part == biped.head) {
                part.roll += (float) Math.toRadians(rotation[2]) * DEFAULT_MIX;
                continue;
            }
            float mix = DEFAULT_MIX;
            part.pitch = lerp(part.pitch, (float) Math.toRadians(rotation[0]), mix);
            part.yaw = lerp(part.yaw, (float) Math.toRadians(-rotation[1]), mix);
            part.roll = lerp(part.roll, (float) Math.toRadians(-rotation[2]), mix);
        }
    }

    private static String choosePhase(ClipSet clips, LivingEntity e) {
        if (e.isDead() && clips.has("death")) return "death";
        if (e.hurtTime > 0 && clips.has("hurt")) return "hurt";
        if (e.handSwinging && clips.has("attack")) return "attack";
        if (e.isSwimming() && clips.has("swim")) return "swim";
        if (!e.isOnGround()) {
            if (e.getVelocity().y > 0.02 && clips.has("jump")) return "jump";
            if (clips.has("fall")) return "fall";
        }
        boolean moving = e.getVelocity().horizontalLengthSquared() > MOVING_THRESHOLD;
        if (moving && e.isSprinting()) {
            for (String s : new String[] {"sprint", "run", "move", "walk"})
                if (clips.has(s)) return s;
        }
        if (moving) {
            for (String s : new String[] {"walk", "move", "run"})
                if (clips.has(s)) return s;
        }
        for (String s : new String[] {"idle", "bob", "base_pose"})
            if (clips.has(s)) return s;
        return null;
    }

    private static ModelPart boneToVanillaPart(BipedEntityModel<?> m, String bone) {
        return switch (bone.toLowerCase()) {
            case "head" -> m.head;
            case "body", "torso" -> m.body;
            case "rightarm", "right_arm" -> m.rightArm;
            case "leftarm", "left_arm" -> m.leftArm;
            case "rightleg", "right_leg" -> m.rightLeg;
            case "leftleg", "left_leg" -> m.leftLeg;
            default -> null;
        };
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static final class AnimState { String phase = ""; float startTick = 0; }

    private record ClipSet(Map<String, Clip> clips) {
        boolean has(String name) { return clips.containsKey(name); }

        static ClipSet fromJson(JsonObject root) {
            Map<String, Clip> clips = new HashMap<>();
            JsonObject map = root.getAsJsonObject("clips");
            if (map == null) return new ClipSet(Map.of());
            for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
                try { clips.put(entry.getKey(), Clip.parse(entry.getValue().getAsJsonObject())); }
                catch (RuntimeException ex) { LOG.warn("Invalid clip {}: {}", entry.getKey(), ex.toString()); }
            }
            return new ClipSet(Map.copyOf(clips));
        }
    }

    private record Clip(float duration, boolean loop, Map<String, Bone> bones) {
        static Clip parse(JsonObject j) {
            float duration = j.has("animation_length") ? j.get("animation_length").getAsFloat() : 1f;
            duration = Math.max(0.01f, Math.min(duration, 60f));
            boolean loop = j.has("loop") && j.get("loop").isJsonPrimitive()
                && j.get("loop").getAsJsonPrimitive().isBoolean() && j.get("loop").getAsBoolean();
            Map<String, Bone> bones = new HashMap<>();
            JsonObject b = j.getAsJsonObject("bones");
            if (b != null) {
                for (Map.Entry<String, JsonElement> entry : b.entrySet()) {
                    JsonObject tracks = entry.getValue().getAsJsonObject();
                    Track rotation = tracks.has("rotation") ? Track.parse(tracks.get("rotation")) : null;
                    if (rotation != null) bones.put(entry.getKey(), new Bone(rotation));
                }
            }
            return new Clip(duration, loop, Map.copyOf(bones));
        }
    }

    private record Bone(Track rotation) {}
    private record Key(float time, float[] value) {}
    private record Track(List<Key> frames) {
        static Track parse(JsonElement channel) {
            List<Key> keys = new ArrayList<>();
            if (channel.isJsonArray()) {
                float[] v = vec(channel);
                if (v != null) keys.add(new Key(0, v));
            } else if (channel.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : channel.getAsJsonObject().entrySet()) {
                    float t;
                    try { t = Float.parseFloat(entry.getKey()); }
                    catch (NumberFormatException ex) { continue; }
                    if (t < 0 || t > 60 || !Float.isFinite(t)) continue;
                    JsonElement el = entry.getValue();
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        el = obj.has("post") ? obj.get("post")
                            : obj.has("pre") ? obj.get("pre") : el;
                    }
                    float[] v = vec(el);
                    if (v != null) keys.add(new Key(t, v));
                }
            }
            if (keys.isEmpty()) return null;
            keys.sort(Comparator.comparingDouble(Key::time));
            return new Track(List.copyOf(keys));
        }

        private static float[] vec(JsonElement v) {
            if (!v.isJsonArray()) return null;
            JsonArray a = v.getAsJsonArray();
            if (a.size() != 3) return null;
            float[] out = new float[3];
            for (int i = 0; i < 3; i++) {
                try {
                    out[i] = a.get(i).getAsFloat();
                    if (!Float.isFinite(out[i]) || Math.abs(out[i]) > 10000) return null;
                } catch (Exception ex) { return null; }
            }
            return out;
        }

        float[] at(float sec) {
            if (frames.size() == 1 || sec <= frames.get(0).time) return frames.get(0).value;
            for (int i = 1; i < frames.size(); i++) {
                Key b = frames.get(i), a = frames.get(i - 1);
                if (sec <= b.time) {
                    float t = (sec - a.time) / Math.max(b.time - a.time, 0.00001f);
                    return new float[] {
                        lerp(a.value[0], b.value[0], t),
                        lerp(a.value[1], b.value[1], t),
                        lerp(a.value[2], b.value[2], t)
                    };
                }
            }
            return frames.get(frames.size() - 1).value;
        }
    }
}
