package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Local-only read-only inventory of Bedrock geometry, animations, controllers,
 * and entity references. It does not install or execute Bedrock scripts.
 */
public final class BedrockPackCatalog {
    public final String filename;
    public final Map<String, Geometry> geometry;
    public final Map<String, Animation> animations;
    public final Map<String, EntityInfo> entities;
    public final Map<String, Controller> controllers;
    public final Map<String, RenderController> renderControllers;
    public final int decodedDocuments;

    private BedrockPackCatalog(String filename, Map<String, Geometry> geometry,
            Map<String, Animation> animations, Map<String, EntityInfo> entities,
            Map<String, Controller> controllers, Map<String, RenderController> renderControllers,
            int decodedDocuments) {
        this.filename = filename;
        this.geometry = Map.copyOf(geometry);
        this.animations = Map.copyOf(animations);
        this.entities = Map.copyOf(entities);
        this.controllers = Map.copyOf(controllers);
        this.renderControllers = Map.copyOf(renderControllers);
        this.decodedDocuments = decodedDocuments;
    }

    public static List<Path> discover(Path folder) throws IOException {
        Files.createDirectories(folder);
        try (var paths = Files.list(folder)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcpack"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .limit(30).toList();
        }
    }

    public static BedrockPackCatalog open(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > 150L * 1024 * 1024) {
            throw new IOException("File not found or too large (max 150 MB)");
        }
        var geometries = new LinkedHashMap<String, Geometry>();
        var clips = new LinkedHashMap<String, Animation>();
        var entityIndex = new LinkedHashMap<String, EntityInfo>();
        var controllers = new LinkedHashMap<String, Controller>();
        var renderControllers = new LinkedHashMap<String, RenderController>();
        int[] docs = {0};

        try (ZipFile zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int checked = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (++checked > 15_000) throw new IOException("Too many ZIP entries");
                String name = entry.getName();
                try { BrArchiveReader.validateName(name); }
                catch (IllegalArgumentException bad) { throw new IOException("Unsafe pack path", bad); }
                // Main resource pack only; alternate SP0/SP1/SP2 subpacks are a separate mode.
                if (name.startsWith("__brarchive/") && name.endsWith(".brarchive")) {
                    String category = name.substring("__brarchive/".length(), name.length() - ".brarchive".length());
                    if (!isRelevant(category)) continue;
                    if (entry.getSize() > 80L * 1024 * 1024) {
                        throw new IOException("Oversized BRARCHIVE");
                    }
                    byte[] raw = readLimited(zip, entry, 80 * 1024 * 1024);
                    try {
                        BrArchiveReader.read(raw, (member, data) -> {
                            if (member.endsWith(".json")) {
                                index(category, data, geometries, clips, entityIndex, controllers, renderControllers, docs);
                            }
                        });
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Invalid " + name + ": " + ex.getMessage(), ex);
                    }
                } else if (name.endsWith(".json")) {
                    for (String category : List.of("animations", "animation_controllers", "render_controllers", "models/entity", "entity")) {
                        if (name.startsWith(category + "/")) {
                            index(category, readLimited(zip, entry, 24 * 1024 * 1024),
                                geometries, clips, entityIndex, controllers, renderControllers, docs);
                            break;
                        }
                    }
                }
            }
        }
        if (geometries.isEmpty() && clips.isEmpty()) {
            throw new IOException("No supported Bedrock animation/geometry files found");
        }
        return new BedrockPackCatalog(source.getFileName().toString(),
                geometries, clips, entityIndex, controllers, renderControllers, docs[0]);
    }

    private static boolean isRelevant(String category) {
        return category.equals("animations") || category.equals("render_controllers") || category.equals("animation_controllers")
                || category.equals("models/entity") || category.equals("entity");
    }

    private static byte[] readLimited(ZipFile zip, ZipEntry e, int max) throws IOException {
        if (e.getSize() > max) throw new IOException("Entry exceeds size limit");
        try (var input = zip.getInputStream(e);
             var out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > max) throw new IOException("Decompressed entry too large");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void index(String category, byte[] bytes,
            Map<String, Geometry> geos, Map<String, Animation> clips,
            Map<String, EntityInfo> entities, Map<String, Controller> controllers,
            Map<String, RenderController> renderControllers, int[] docs) {
        if (bytes.length > 24 * 1024 * 1024 || ++docs[0] > 15_000) return;
        JsonObject root;
        try {
            root = JsonParser.parseReader(new java.io.InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException bad) {
            return; // Some vendor files contain non-JSON payloads.
        }
        switch (category) {
            case "models/entity" -> {
                JsonArray objects = array(root, "minecraft:geometry");
                if (objects == null) return;
                for (JsonElement obj : objects) {
                    if (!obj.isJsonObject()) continue;
                    Geometry geo = Geometry.parse(obj.getAsJsonObject());
                    if (geo != null && geos.size() < 9_000) geos.putIfAbsent(geo.id, geo);
                }
            }
            case "animations" -> {
                JsonObject animations = object(root, "animations");
                if (animations == null) return;
                for (var e : animations.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    if (clips.size() > 8_000) break;
                    try { clips.putIfAbsent(e.getKey(), Animation.parse(e.getKey(), e.getValue().getAsJsonObject())); }
                    catch (RuntimeException ignored) { }
                }
            }
            case "entity" -> {
                JsonObject entity = object(root, "minecraft:client_entity");
                JsonObject desc = entity == null ? null : object(entity, "description");
                if (desc == null) return;
                String id = string(desc, "identifier", "");
                if (!id.isEmpty()) {
                    entities.putIfAbsent(id, new EntityInfo(id, strings(object(desc, "geometry")),
                            strings(object(desc, "animations")), renderBindings(array(desc, "render_controllers"))));
                }
            }
            case "render_controllers" -> {
                JsonObject map = object(root, "render_controllers");
                if (map == null) return;
                for (var e : map.entrySet()) {
                    if (!e.getValue().isJsonObject() || renderControllers.size() >= 4_000) continue;
                    JsonObject rule = e.getValue().getAsJsonObject();
                    renderControllers.putIfAbsent(e.getKey(), new RenderController(e.getKey(),
                        string(rule,"geometry",""), rule.deepCopy()));
                }
            }
            case "animation_controllers" -> {
                JsonObject map = object(root, "animation_controllers");
                if (map == null) return;
                for (var e : map.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject states = object(e.getValue().getAsJsonObject(), "states");
                    controllers.putIfAbsent(e.getKey(), new Controller(e.getKey(),
                        states == null ? List.of() : List.copyOf(states.keySet()),
                        e.getValue().getAsJsonObject().deepCopy()));
                }
            }
            default -> { }
        }
    }

    public List<String> entityNames() {
        ArrayList<String> names = new ArrayList<>(entities.keySet());
        Collections.sort(names);
        if (names.remove("minecraft:player")) names.add(0, "minecraft:player");
        return names;
    }

    public List<Geometry> geometriesFor(String entityId) {
        EntityInfo info = entities.get(entityId);
        if (info == null) return List.of();
        ArrayList<Geometry> list = new ArrayList<>();
        for (String id : info.geometry.values()) {
            Geometry geo = geometry.get(id);
            if (geo != null && !list.contains(geo)) list.add(geo);
        }
        // Prefer humanoid geometry with recognizable limbs over attached props.
        list.sort(Comparator.comparingInt((Geometry g) -> -humanoidScore(g)).thenComparing(Geometry::id));
        return list;
    }

    private static int humanoidScore(Geometry geo) {
        int score = 0;
        for (Bone bone : geo.bones()) {
            String n = bone.name().toLowerCase(Locale.ROOT).replace("_", "");
            if (n.equals("root") || n.equals("body") || n.equals("waist") || n.equals("head")) score += 12;
            if (n.equals("leftarm") || n.equals("rightarm")
                    || n.equals("leftleg") || n.equals("rightleg")) score += 18;
        }
        return score + Math.min(geo.cubes(), 100) / 10;
    }

    public List<AnimationOption> animationsFor(String entityId) {
        EntityInfo info = entities.get(entityId);
        if (info == null) return List.of();
        ArrayList<AnimationOption> result = new ArrayList<>();
        for (var alias : info.animations.entrySet()) {
            Animation a = animations.get(alias.getValue());
            if (a != null) result.add(new AnimationOption(alias.getKey(), a));
        }
        result.sort(Comparator.comparing(AnimationOption::alias));
        return result;
    }

    public record EntityInfo(String id, Map<String, String> geometry,
                             Map<String, String> animations, List<RenderBinding> renderBindings) { }
    public record RenderBinding(String controllerId, String condition) { }
    public record RenderController(String id, String geometryRef, JsonObject originalSource) { }
    public record Controller(String id, List<String> states, JsonObject originalSource) { }
    public record AnimationOption(String alias, Animation animation) { }
    public record Bone(String name, String parent, float x, float y, float z, int cubes) { }
    public record Geometry(String id, List<Bone> bones, int cubes, JsonObject originalSource) {
        static Geometry parse(JsonObject doc) {
            JsonObject desc = object(doc, "description");
            if (desc == null) return null;
            String id = string(desc, "identifier", "");
            JsonArray array = array(doc, "bones");
            if (id.isEmpty() || array == null) return null;
            List<Bone> bones = new ArrayList<>();
            int cubeCount = 0;
            for (JsonElement element : array) {
                if (!element.isJsonObject() || bones.size() > 2_000) continue;
                JsonObject bone = element.getAsJsonObject();
                String name = string(bone, "name", "");
                if (name.isEmpty()) continue;
                float[] pivot = xyz(bone.get("pivot"));
                if (pivot == null) pivot = new float[3];
                JsonArray cubes = array(bone, "cubes");
                int n = cubes == null ? 0 : cubes.size();
                cubeCount += n;
                bones.add(new Bone(name, string(bone, "parent", ""),
                    pivot[0], pivot[1], pivot[2], n));
            }
            return new Geometry(id, List.copyOf(bones), cubeCount, doc.deepCopy());
        }
    }

    public record Animation(String id, float seconds, boolean looping, Map<String, Channels> bones,
                            int expressionChannels, JsonObject originalSource) {
        static Animation parse(String id, JsonObject doc) {
            float duration = number(doc.get("animation_length"), 1f);
            if (duration <= 0 || duration > 180 || !Float.isFinite(duration)) duration = 1;
            boolean loop = doc.has("loop") && doc.get("loop").isJsonPrimitive()
                    && doc.get("loop").getAsJsonPrimitive().isBoolean()
                    && doc.get("loop").getAsBoolean();
            Map<String, Channels> bones = new LinkedHashMap<>();
            int skipped = 0;
            JsonObject object = object(doc, "bones");
            if (object != null) {
                for (var e : object.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject tracks = e.getValue().getAsJsonObject();
                    Track r = Track.parse(tracks.get("rotation"));
                    Track p = Track.parse(tracks.get("position"));
                    Track s = Track.parse(tracks.get("scale"));
                    for (String channel : List.of("rotation", "position", "scale")) {
                        if (tracks.has(channel)) {
                            Track t = channel.equals("rotation") ? r : channel.equals("position") ? p : s;
                            if (t == null) skipped++;
                        }
                    }
                    bones.put(e.getKey(), new Channels(r, p, s));
                }
            }
            return new Animation(id, duration, loop, Map.copyOf(bones), skipped, doc.deepCopy());
        }
        public float[] rotation(String boneName, float sec) {
            Channels ch = bones.get(boneName);
            return ch == null || ch.rotation == null ? null : ch.rotation.sample(sec);
        }
    }
    public record Channels(Track rotation, Track position, Track scale) { }
    public record Key(float time, float[] pre, float[] post) { }

    public record Track(List<Key> keys) {
        static Track parse(JsonElement raw) {
            if (raw == null) return null;
            List<Key> result = new ArrayList<>();
            if (raw.isJsonArray()) {
                float[] v = xyz(raw);
                if (v != null) result.add(new Key(0f, v, v));
            } else if (raw.isJsonObject()) {
                JsonObject obj = raw.getAsJsonObject();
                for (var e : obj.entrySet()) {
                    float time;
                    try { time = Float.parseFloat(e.getKey()); }
                    catch (NumberFormatException ex) { continue; }
                    if (!Float.isFinite(time) || time < 0 || time > 180) continue;
                    JsonElement value = e.getValue();
                    float[] pre, post;
                    if (value.isJsonObject()) {
                        JsonObject k = value.getAsJsonObject();
                        pre = xyz(k.get("pre"));
                        post = xyz(k.get("post"));
                        if (pre == null) pre = post;
                        if (post == null) post = pre;
                    } else pre = post = xyz(value);
                    if (pre != null && post != null) result.add(new Key(time, pre, post));
                }
            }
            if (result.isEmpty()) return null;
            result.sort(Comparator.comparingDouble(Key::time));
            return new Track(List.copyOf(result));
        }
        public float[] sample(float t) {
            if (keys.isEmpty()) return null;
            if (keys.size() == 1 || t <= keys.get(0).time) return keys.get(0).pre;
            for (int i = 1; i < keys.size(); i++) {
                Key a = keys.get(i - 1), b = keys.get(i);
                if (t <= b.time) {
                    float mix = Math.max(0, Math.min(1, (t - a.time) / Math.max(0.00001f, b.time - a.time)));
                    return new float[] {
                        a.post[0] + (b.pre[0] - a.post[0]) * mix,
                        a.post[1] + (b.pre[1] - a.post[1]) * mix,
                        a.post[2] + (b.pre[2] - a.post[2]) * mix
                    };
                }
            }
            return keys.get(keys.size() - 1).post;
        }
    }

    private static List<RenderBinding> renderBindings(JsonArray raw) {
        if (raw == null) return List.of();
        List<RenderBinding> result = new ArrayList<>();
        for (JsonElement element : raw) {
            if (result.size() >= 256) break;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                result.add(new RenderBinding(element.getAsString(), ""));
            } else if (element.isJsonObject()) {
                for (var entry : element.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) continue;
                    result.add(new RenderBinding(entry.getKey(), entry.getValue().getAsString()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, String> strings(JsonObject raw) {
        if (raw == null) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (var e : raw.entrySet()) if (e.getValue().isJsonPrimitive()
                && e.getValue().getAsJsonPrimitive().isString())
            map.put(e.getKey(), e.getValue().getAsString());
        return Map.copyOf(map);
    }
    private static JsonArray array(JsonObject doc, String key) {
        JsonElement value = doc == null ? null : doc.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
    private static JsonObject object(JsonObject doc, String key) {
        JsonElement value = doc == null ? null : doc.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
    private static String string(JsonObject doc, String key, String fallback) {
        JsonElement value = doc == null ? null : doc.get(key);
        try { return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback; }
        catch (RuntimeException e) { return fallback; }
    }
    private static float number(JsonElement value, float fallback) {
        try { return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback; }
        catch (RuntimeException e) { return fallback; }
    }
    private static float[] xyz(JsonElement raw) {
        if (raw == null || !raw.isJsonArray()) return null;
        JsonArray list = raw.getAsJsonArray();
        if (list.size() != 3) return null;
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            try {
                result[i] = list.get(i).getAsFloat();
                if (!Float.isFinite(result[i]) || Math.abs(result[i]) > 10000) return null;
            } catch (RuntimeException e) { return null; }
        }
        return result;
    }
}
