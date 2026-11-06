package dev.actionsstuff.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * First experimental 1.21.11 player animation bridge.
 *
 * The original licensed animation assets must be installed locally by a tester.
 * This engine currently supports only rotation keyframes on the six vanilla
 * player body parts; Bedrock rig extensions and controllers require future work.
 */
public final class BridgeAnimations {
    public static final Logger LOG = LoggerFactory.getLogger("ActionsStuffBridge");
    private static volatile Map<String, Clip> playerClips = Map.of();
    private static final float MIX = 0.68f;

    private BridgeAnimations() {}

    public static void reload(ResourceManager manager) {
        Map<String, Clip> imported = new HashMap<>();
        Identifier id = Identifier.of(ActionsStuffBridgeClient.MOD_ID, "animations/player.json");
        manager.getResource(id).ifPresent(resource -> {
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject clips = root.has("clips") ? root.getAsJsonObject("clips")
                    : root.has("animations") ? root.getAsJsonObject("animations") : null;
                if (clips == null) {
                    LOG.warn("Player animation file has neither 'clips' nor 'animations' section");
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : clips.entrySet()) {
                    try {
                        String shortName = entry.getKey();
                        int dot = shortName.lastIndexOf('.');
                        if (dot >= 0) shortName = shortName.substring(dot + 1);
                        Clip clip = Clip.parse(entry.getValue().getAsJsonObject());
                        imported.put(shortName, clip);
                    } catch (Exception e) {
                        LOG.warn("Skipping invalid animation {}: {}", entry.getKey(), e.toString());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Unable to load player animations: {}", e.toString());
            }
        });
        playerClips = Map.copyOf(imported);
        LOG.info("Fabric 1.21.11: {} player animation clips loaded", imported.size());
    }

    public static void apply(BipedEntityModel<?> model, PlayerEntityRenderState state) {
        Map<String, Clip> library = playerClips;
        if (library.isEmpty() || state.spectator) return;
        String phase = chooseClip(library, state);
        if (phase == null) return;
        Clip clip = library.get(phase);
        if (clip == null) return;

        float seconds = state.age / 20.0f;
        if (clip.loop && clip.duration > 0) {
            seconds %= clip.duration;
        } else {
            seconds = Math.min(seconds, clip.duration);
        }
        float mix = MIX;
        if (phase.equals("walk") || phase.equals("sprint") || phase.equals("run")) {
            mix *= Math.min(1.0f, state.limbSwingAmplitude * 2.0f);
        }

        for (Map.Entry<String, Track> entry : clip.rotations.entrySet()) {
            ModelPart part = switch (entry.getKey().toLowerCase()) {
                case "head" -> model.head;
                case "body", "torso" -> model.body;
                case "rightarm", "right_arm" -> model.rightArm;
                case "leftarm", "left_arm" -> model.leftArm;
                case "rightleg", "right_leg" -> model.rightLeg;
                case "leftleg", "left_leg" -> model.leftLeg;
                default -> null;
            };
            if (part == null) continue;
            float[] value = entry.getValue().sample(seconds);
            if (value == null) continue;
            // Preserve vanilla camera-controlled head direction.
            if (part != model.head) {
                part.pitch = lerp(part.pitch, radians(value[0]), mix);
                part.yaw = lerp(part.yaw, radians(-value[1]), mix);
            }
            part.roll = lerp(part.roll, radians(-value[2]), mix);
        }
    }

    private static String chooseClip(Map<String, Clip> library, PlayerEntityRenderState state) {
        if (state.deathTime > 0 && library.containsKey("death")) return "death";
        if (state.hurt && library.containsKey("hurt")) return "hurt";
        if (state.handSwingProgress > 0.01f && library.containsKey("attack")) return "attack";
        if (state.isSwimming && library.containsKey("swim")) return "swim";
        if (state.isGliding && library.containsKey("glide")) return "glide";
        if (state.isUsingItem && library.containsKey("use")) return "use";
        boolean walking = state.limbSwingAmplitude > 0.05f;
        if (walking) {
            if (state.limbSwingAmplitude > 0.6f && library.containsKey("sprint")) return "sprint";
            if (library.containsKey("walk")) return "walk";
            if (library.containsKey("run")) return "run";
        }
        if (state.isInSneakingPose && library.containsKey("sneak")) return "sneak";
        return library.containsKey("idle") ? "idle" : null;
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private record Clip(float duration, boolean loop, Map<String, Track> rotations) {
        static Clip parse(JsonObject root) {
            float length = root.has("animation_length") ? root.get("animation_length").getAsFloat() : 1.0f;
            if (!Float.isFinite(length) || length <= 0 || length > 120) throw new IllegalArgumentException("Bad duration");
            boolean loop = root.has("loop") && root.get("loop").isJsonPrimitive()
                && root.get("loop").getAsJsonPrimitive().isBoolean()
                && root.get("loop").getAsBoolean();
            Map<String, Track> rotations = new HashMap<>();
            JsonObject bones = root.getAsJsonObject("bones");
            if (bones != null) {
                for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
                    if (!bone.getValue().isJsonObject()) continue;
                    JsonElement rotation = bone.getValue().getAsJsonObject().get("rotation");
                    if (rotation == null) continue;
                    Track track = Track.parse(rotation);
                    if (track != null) rotations.put(bone.getKey(), track);
                }
            }
            if (rotations.isEmpty()) throw new IllegalArgumentException("No supported rotation keyframes");
            return new Clip(length, loop, Map.copyOf(rotations));
        }
    }

    private record Key(float time, float[] value) {}
    private record Track(List<Key> points) {
        static Track parse(JsonElement json) {
            List<Key> points = new ArrayList<>();
            if (json.isJsonArray()) {
                float[] value = readVec(json);
                if (value != null) points.add(new Key(0.0f, value));
            } else if (json.isJsonObject()) {
                for (Map.Entry<String, JsonElement> sample : json.getAsJsonObject().entrySet()) {
                    float time;
                    try { time = Float.parseFloat(sample.getKey()); }
                    catch (NumberFormatException e) { continue; }
                    if (!Float.isFinite(time) || time < 0 || time > 120) continue;
                    JsonElement value = sample.getValue();
                    if (value.isJsonObject()) {
                        JsonObject key = value.getAsJsonObject();
                        value = key.has("post") ? key.get("post") : key.get("pre");
                    }
                    if (value == null) continue;
                    float[] vector = readVec(value);
                    if (vector != null) points.add(new Key(time, vector));
                }
            }
            if (points.isEmpty()) return null;
            points.sort(Comparator.comparingDouble(Key::time));
            return new Track(List.copyOf(points));
        }
        private static float[] readVec(JsonElement json) {
            if (!json.isJsonArray()) return null;
            JsonArray list = json.getAsJsonArray();
            if (list.size() != 3) return null;
            float[] vector = new float[3];
            for (int i = 0; i < 3; i++) {
                try {
                    vector[i] = list.get(i).getAsFloat();
                    if (!Float.isFinite(vector[i]) || Math.abs(vector[i]) > 3600) return null;
                } catch (Exception e) {
                    // Math expressions and Molang are intentionally not guessed.
                    return null;
                }
            }
            return vector;
        }
        float[] sample(float seconds) {
            if (points.size() == 1 || seconds <= points.get(0).time) return points.get(0).value;
            for (int i = 1; i < points.size(); i++) {
                Key before = points.get(i - 1);
                Key after = points.get(i);
                if (seconds <= after.time) {
                    float fraction = (seconds - before.time) / Math.max(after.time - before.time, 0.00001f);
                    return new float[] {
                        lerp(before.value[0], after.value[0], fraction),
                        lerp(before.value[1], after.value[1], fraction),
                        lerp(before.value[2], after.value[2], fraction)
                    };
                }
            }
            return points.get(points.size() - 1).value;
        }
    }
}
