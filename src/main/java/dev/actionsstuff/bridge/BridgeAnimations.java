package dev.actionsstuff.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.actionsstuff.bridge.studio.BedrockPackCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
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
 * Player state machine and animation tracks for 1.21.11.
 *
 * Loads a tester-supplied, locally installed, source-derived resource pack.
 * Supports clipped numeric tracks, phase-local clocks, directional movement,
 * idle/move transitions, attack/hurt and water/air states.
 *
 * Does NOT implement Bedrock's full rig, MoLang, custom vertices, or controllers.
 * Original Oreville files are never bundled in this public repository.
 */
public final class BridgeAnimations {
    public static final Logger LOG = LoggerFactory.getLogger("ActionsStuffBridge");
    private static volatile Map<String, Clip> clips = Map.of();
    // Legacy six-part player posing is opt-in; bundled test clips must not
    // silently modify the vanilla player at startup.
    private static volatile boolean legacyEnabled = false;
    private static final HashMap<Integer, Playback> playing = new HashMap<>();
    private static final float FADE_SECONDS = 0.18f;
    private static int lastLoggedTick = Integer.MIN_VALUE;

    private BridgeAnimations() {}

    public static boolean isEnabled() { return legacyEnabled; }
    public static int loadedClipCount() { return clips.size(); }

    public static void disable() {
        legacyEnabled = false;
        playing.clear();
        LOG.info("Legacy vanilla-biped animation adapter disabled");
    }

    public static void enable() {
        SourcePlayerMotion.disable();
        legacyEnabled = true;
        playing.clear();
        LOG.info("Legacy vanilla-biped animation adapter explicitly enabled");
    }

    public static void reload(ResourceManager manager) {
        Map<String, Clip> newClips = new HashMap<>();
        Identifier id = Identifier.of(ActionsStuffBridgeClient.MOD_ID, "animations/player.json");
        manager.getResource(id).ifPresent(resource -> {
            try (InputStreamReader reader =
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject source = root.has("clips") ? root.getAsJsonObject("clips")
                        : root.has("animations") ? root.getAsJsonObject("animations") : null;
                if (source == null) throw new IllegalArgumentException("No clips or animations mapping");
                for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                    try {
                        String name = entry.getKey();
                        // Retain original action aliases supplied by private test importer.
                        int separator = name.lastIndexOf('.');
                        if (separator >= 0) name = name.substring(separator + 1);
                        newClips.put(name, Clip.parse(entry.getValue().getAsJsonObject()));
                    } catch (RuntimeException ex) {
                        LOG.warn("Skipped invalid clip {}: {}", entry.getKey(), ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                LOG.warn("Could not load local animation clips: {}", ex.toString());
            }
        });
        clips = Map.copyOf(newClips);
        playing.clear();
        lastLoggedTick = Integer.MIN_VALUE;
        LOG.info("Loaded {} private/synthetic player clips: {}", clips.size(), clips.keySet());
    }

    /**
     * Directly loads numeric player animation tracks from a locally indexed .mcpack.
     * It never writes or transmits the original asset data. Unsupported MoLang
     * channels are skipped and reported by the Studio inspector.
     */
    public static int loadBedrockPlayer(BedrockPackCatalog catalog) {
        var player = catalog.entities.get("minecraft:player");
        if (player == null) return 0;
        Map<String, Clip> imported = new HashMap<>();
        for (var alias : player.animations().entrySet()) {
            if (!List.of("walk", "move", "bob", "humanoid_base_pose", "attack",
                    "hurt", "jump", "fall", "swim", "fly", "run", "sneak",
                    "eat", "use", "death").contains(alias.getKey())) continue;
            BedrockPackCatalog.Animation anim = catalog.animations.get(alias.getValue());
            if (anim == null) continue;
            JsonObject original = new JsonObject();
            original.addProperty("animation_length", anim.seconds());
            original.addProperty("loop", anim.looping());
            JsonObject bones = new JsonObject();
            for (var bone : anim.bones().entrySet()) {
                JsonObject channels = new JsonObject();
                putChannel(channels, "rotation", bone.getValue().rotation());
                putChannel(channels, "position", bone.getValue().position());
                putChannel(channels, "scale", bone.getValue().scale());
                if (!channels.entrySet().isEmpty()) bones.add(bone.getKey(), channels);
            }
            if (bones.entrySet().isEmpty()) continue;
            original.add("bones", bones);
            try {
                imported.put(alias.getKey(), Clip.parse(original));
            } catch (RuntimeException ex) {
                LOG.warn("Ignoring unsupported original animation {}: {}",
                    alias.getKey(), ex.getMessage());
            }
        }
        if (imported.containsKey("walk")) imported.putIfAbsent("walk_forward", imported.get("walk"));
        if (imported.containsKey("bob")) imported.putIfAbsent("idle", imported.get("bob"));
        if (imported.containsKey("move")) imported.putIfAbsent("walk_forward", imported.get("move"));
        if (imported.isEmpty()) return 0;
        clips = Map.copyOf(imported);
        enable();
        playing.clear();
        LOG.info("Direct local Bedrock player motion loaded: {} playable aliases from {}",
            imported.size(), catalog.filename);
        return imported.size();
    }

    private static void putChannel(JsonObject dst, String name, BedrockPackCatalog.Track track) {
        if (track == null) return;
        JsonObject keyframes = new JsonObject();
        for (var key : track.keys()) {
            JsonObject frame = new JsonObject();
            frame.add("pre", vecToJson(key.pre()));
            frame.add("post", vecToJson(key.post()));
            keyframes.add(Float.toString(key.time()), frame);
        }
        dst.add(name, keyframes);
    }

    private static JsonArray vecToJson(float[] vector) {
        JsonArray array = new JsonArray();
        for (float value : vector) array.add(value);
        return array;
    }

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState render) {
        if (!legacyEnabled) return;
        Map<String, Clip> available = clips;
        if (available.isEmpty() || render.spectator) return;
        int entityId = render.id;
        float now = render.age;
        if (!Float.isFinite(now)) return;
        if (playing.size() > 512) playing.clear();

        Playback playback = playing.computeIfAbsent(entityId, key -> new Playback());
        if (now < playback.lastAge - 5) playback.reset();
        playback.lastAge = now;

        String next = selectClip(render, available);
        if (next == null) return;
        if (!next.equals(playback.current)) {
            playback.previous = playback.current;
            playback.previousSeconds = Math.max(0, (now - playback.startedAt) / 20f);
            playback.current = next;
            playback.startedAt = now;
            playback.changedAt = now;
        }

        Clip clip = available.get(next);
        if (clip == null) return;
        float speed = speedMultiplier(render, next);
        float elapsed = (now - playback.startedAt) / 20f * speed;
        float t = normalizeTime(clip, Math.max(0, elapsed));
        float fade = clamp((now - playback.changedAt) / (FADE_SECONDS * 20f), 0, 1);
        // Soft cubic fade. The old phase is sampled from its last pose.
        fade = fade * fade * (3 - 2 * fade);
        // The first rendered player frame has no previous animation.
        // Map.copyOf(...) rejects null keys, so never look one up.
        Clip previous = ClipLookup.previousOrNull(available, playback.previous);
        float priorTime = previous == null ? 0 : normalizeTime(previous, playback.previousSeconds);

        for (String bone : List.of("body", "waist", "head", "right_arm", "left_arm",
                                  "right_leg", "left_leg")) {
            ModelPart part = part(model, bone);
            if (part == null) continue;
            BoneTrack nextBone = clip.bones.get(bone);
            BoneTrack oldBone = previous == null ? null : previous.bones.get(bone);
            if (nextBone == null && oldBone == null) continue;
            float mix = bone.equals("head") ? 0.42f : 0.86f;
            // Protect arm actions and held items: do not fight vanilla weapon posing fully.
            if (bone.endsWith("arm") && (render.isUsingItem || render.handSwingProgress > 0)) {
                mix *= 0.65f;
            }
            if (bone.equals("waist")) mix *= 0.45f;

            applyChannel(part, nextBone, oldBone, t, priorTime, fade, mix, bone);
        }
        syncOuterLayers(model);

        int diagnosticTick = (int) now;
        if (diagnosticTick % 100 == 0 && diagnosticTick != lastLoggedTick) {
            lastLoggedTick = diagnosticTick;
            LOG.info("Animation controller entity={} phase={} time={} sources={}",
                    entityId, next, String.format(java.util.Locale.ROOT, "%.2f", t), available.size());
        }
    }

    private static void applyChannel(ModelPart part, BoneTrack current, BoneTrack previous,
                                     float time, float previousTime, float fade, float mix, String bone) {
        float[] rotation = blended(current == null ? null : current.rotation,
                previous == null ? null : previous.rotation, time, previousTime, fade);
        if (rotation != null) {
            float layer = bone.equals("waist") ? 0.38f : 1f;
            if (part == null) return;
            if (bone.equals("head")) {
                part.pitch += radians(rotation[0]) * mix * 0.15f;
                part.roll += radians(-rotation[2]) * mix;
            } else if (bone.equals("waist")) {
                part.roll += radians(-rotation[2]) * mix * layer;
                part.yaw += radians(-rotation[1]) * mix * layer;
            } else {
                part.pitch = lerp(part.pitch, radians(rotation[0]), mix);
                part.yaw = lerp(part.yaw, radians(-rotation[1]), mix);
                part.roll = lerp(part.roll, radians(-rotation[2]), mix);
            }
        }
        // Position and scale tracks apply to existing vanilla body parts only;
        // this is not a substitute for source custom-bone geometry.
        if (bone.equals("waist") || bone.equals("head")) return;
        float[] translation = blended(current == null ? null : current.position,
                previous == null ? null : previous.position, time, previousTime, fade);
        if (translation != null) {
            float a = 0.35f * mix;
            part.originX += clamp(translation[0], -6, 6) * a;
            part.originY -= clamp(translation[1], -6, 6) * a;
            part.originZ += clamp(translation[2], -6, 6) * a;
        }
        float[] scale = blended(current == null ? null : current.scale,
                previous == null ? null : previous.scale, time, previousTime, fade);
        if (scale != null) {
            part.xScale = lerp(part.xScale, clamp(scale[0], 0.5f, 1.5f), mix * 0.4f);
            part.yScale = lerp(part.yScale, clamp(scale[1], 0.5f, 1.5f), mix * 0.4f);
            part.zScale = lerp(part.zScale, clamp(scale[2], 0.5f, 1.5f), mix * 0.4f);
        }
    }

    private static float[] blended(Track current, Track previous,
                                   float time, float oldTime, float fade) {
        float[] a = previous == null ? null : previous.sample(oldTime);
        float[] b = current == null ? null : current.sample(time);
        if (a == null) return b;
        if (b == null) return fade >= 1 ? null : new float[] {
                a[0] * (1 - fade), a[1] * (1 - fade), a[2] * (1 - fade)
        };
        return new float[] {lerp(a[0], b[0], fade), lerp(a[1], b[1], fade), lerp(a[2], b[2], fade)};
    }

    private static float normalizeTime(Clip clip, float seconds) {
        return clip.loop && clip.duration > 0 ? seconds % clip.duration
                : Math.min(seconds, clip.duration);
    }

    private static float speedMultiplier(PlayerEntityRenderState state, String phase) {
        if (phase.startsWith("walk_") || phase.startsWith("run_")) {
            return clamp(state.limbSwingAmplitude * 4.5f, 0.65f, 1.6f);
        }
        return 1f;
    }

    private static String selectClip(PlayerEntityRenderState state, Map<String, Clip> c) {
        if (state.deathTime > 0) return first(c, "death", "hurt", "idle");
        if (state.hurt) return first(c, "hurt", "idle");
        if (state.handSwingProgress > 0.04f) return first(c, "attack", "walk_forward", "idle");
        if (state.isSwimming) return first(c, "swim", "idle");
        if (state.isGliding) return first(c, "fly", "fall", "idle");
        if (state.isUsingItem) return first(c, "eat", "use", "idle");

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && player.getId() == state.id) {
            if (!player.isOnGround()) {
                if (player.getVelocity().y > 0.025) return first(c, "jump", "fall", "idle");
                if (player.getVelocity().y < -0.045) return first(c, "fall", "idle");
            }
        }
        if (state.isInSneakingPose) return first(c, "sneak", "walk_forward", "walk", "idle");
        if (state.limbSwingAmplitude > 0.045f) {
            String dir = direction(state, player);
            boolean running = state.limbSwingAmplitude > 0.52f
                    || player != null && player.getId() == state.id && player.isSprinting();
            return running ? first(c, "run_" + dir, "run_forward", "run", "walk_" + dir, "walk_forward", "walk", "move", "idle")
                : first(c, "walk_" + dir, "walk_forward", "walk", "move", "run_" + dir, "idle");
        }
        return first(c, "idle", "bob");
    }

    private static String direction(PlayerEntityRenderState state, ClientPlayerEntity player) {
        if (player == null || player.getId() != state.id) return "forward";
        Vec3d v = player.getVelocity();
        if (v.horizontalLengthSquared() < 0.0005) return "forward";
        double yaw = Math.toRadians(state.bodyYaw);
        double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw);
        double right = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);
        if (Math.abs(right) > Math.abs(forward) * 1.25) return right > 0 ? "right" : "left";
        return forward < -0.005 ? "backward" : "forward";
    }

    private static String first(Map<String, Clip> c, String... names) {
        for (String candidate : names) if (c.containsKey(candidate)) return candidate;
        return null;
    }

    private static ModelPart part(PlayerEntityModel m, String name) {
        return switch (name) {
            case "head" -> m.head;
            case "body", "waist" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> null;
        };
    }

    private static void copyTransform(ModelPart source, ModelPart target) {
        target.pitch = source.pitch;
        target.yaw = source.yaw;
        target.roll = source.roll;
        target.originX = source.originX;
        target.originY = source.originY;
        target.originZ = source.originZ;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    private static void syncOuterLayers(PlayerEntityModel m) {
        copyTransform(m.rightArm, m.rightSleeve);
        copyTransform(m.leftArm, m.leftSleeve);
        copyTransform(m.rightLeg, m.rightPants);
        copyTransform(m.leftLeg, m.leftPants);
        copyTransform(m.body, m.jacket);
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float v, float low, float high) { return Math.max(low, Math.min(high, v)); }

    private static final class Playback {
        String current;
        String previous;
        float startedAt;
        float changedAt;
        float previousSeconds;
        float lastAge = Float.NEGATIVE_INFINITY;
        void reset() {
            current = null; previous = null;
            startedAt = changedAt = previousSeconds = 0;
        }
    }

    private record Clip(float duration, boolean loop, Map<String, BoneTrack> bones) {
        static Clip parse(JsonObject doc) {
            float duration = doc.has("animation_length") ? doc.get("animation_length").getAsFloat() : 1f;
            if (!Float.isFinite(duration) || duration <= 0 || duration > 120)
                throw new IllegalArgumentException("Invalid animation length");
            boolean loop = doc.has("loop") && doc.get("loop").isJsonPrimitive()
                    && doc.get("loop").getAsJsonPrimitive().isBoolean()
                    && doc.get("loop").getAsBoolean();
            Map<String, BoneTrack> bones = new HashMap<>();
            JsonObject obj = doc.getAsJsonObject("bones");
            if (obj != null) {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject channels = entry.getValue().getAsJsonObject();
                    Track rot = channels.has("rotation") ? Track.parse(channels.get("rotation")) : null;
                    Track pos = channels.has("position") ? Track.parse(channels.get("position")) : null;
                    Track scale = channels.has("scale") ? Track.parse(channels.get("scale")) : null;
                    if (rot != null || pos != null || scale != null)
                        bones.put(normalizeBone(entry.getKey()), new BoneTrack(rot, pos, scale));
                }
            }
            if (bones.isEmpty()) throw new IllegalArgumentException("No usable numeric tracks");
            return new Clip(duration, loop, Map.copyOf(bones));
        }
    }

    private static String normalizeBone(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "rightarm", "right_arm" -> "right_arm";
            case "leftarm", "left_arm" -> "left_arm";
            case "rightleg", "right_leg" -> "right_leg";
            case "leftleg", "left_leg" -> "left_leg";
            default -> name.toLowerCase(java.util.Locale.ROOT);
        };
    }

    private record BoneTrack(Track rotation, Track position, Track scale) {}
    private record Key(float time, float[] pre, float[] post) {}
    private record Track(List<Key> keys) {
        static Track parse(JsonElement root) {
            List<Key> keys = new ArrayList<>();
            if (root.isJsonArray()) {
                float[] v = vec(root);
                if (v != null) keys.add(new Key(0, v, v));
            } else if (root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                    float t;
                    try { t = Float.parseFloat(e.getKey()); }
                    catch (NumberFormatException ignored) { continue; }
                    if (!Float.isFinite(t) || t < 0 || t > 120) continue;
                    float[] pre, post;
                    if (e.getValue().isJsonObject()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        pre = o.has("pre") ? vec(o.get("pre")) : null;
                        post = o.has("post") ? vec(o.get("post")) : null;
                        if (pre == null) pre = post;
                        if (post == null) post = pre;
                    } else {
                        pre = post = vec(e.getValue());
                    }
                    if (pre != null && post != null) keys.add(new Key(t, pre, post));
                }
            }
            if (keys.isEmpty()) return null;
            keys.sort(Comparator.comparingDouble(Key::time));
            return new Track(List.copyOf(keys));
        }

        private static float[] vec(JsonElement root) {
            if (root == null || !root.isJsonArray()) return null;
            JsonArray a = root.getAsJsonArray();
            if (a.size() != 3) return null;
            float[] v = new float[3];
            for (int i = 0; i < 3; i++) {
                try {
                    v[i] = a.get(i).getAsFloat();
                    if (!Float.isFinite(v[i]) || Math.abs(v[i]) > 3600) return null;
                } catch (RuntimeException ignored) { return null; }
            }
            return v;
        }

        float[] sample(float time) {
            if (keys.size() == 1) return keys.get(0).post;
            if (time <= keys.get(0).time) return keys.get(0).pre;
            for (int i = 1; i < keys.size(); i++) {
                Key right = keys.get(i), left = keys.get(i - 1);
                if (time <= right.time) {
                    float u = clamp((time - left.time) / Math.max(0.00001f, right.time - left.time), 0, 1);
                    return new float[] {
                        lerp(left.post[0], right.pre[0], u),
                        lerp(left.post[1], right.pre[1], u),
                        lerp(left.post[2], right.pre[2], u)
                    };
                }
            }
            return keys.get(keys.size() - 1).post;
        }
    }
}
