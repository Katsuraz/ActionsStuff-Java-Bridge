package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a genuine Minecraft ModelPart cuboid mesh from the user's locally
 * loaded Bedrock geometry: parent-child bone tree, pivots and cube dimensions.
 *
 * Experimental UV coverage: box UV and face UV starting positions, but Bedrock's
 * independent per-face UV sizes, mesh polygons and materials need later work.
 * No source assets are embedded in the JAR or published to GitHub.
 */
public final class BedrockCubeModel {
    private static final int MAX_BONES = 512;
    private static final int MAX_CUBES = 1_500;
    public final ModelPart root;
    public final Map<String, ModelPart> parts;
    public final Map<String, float[]> defaultRotations;
    public final int cubeCount;
    public final String geometryId;
    public final int textureWidth;
    public final int textureHeight;

    private BedrockCubeModel(ModelPart root, Map<String, ModelPart> parts,
                             Map<String, float[]> rotations, int cubes,
                             String id, int width, int height) {
        this.root = root;
        this.parts = Map.copyOf(parts);
        this.defaultRotations = Map.copyOf(rotations);
        this.cubeCount = cubes;
        this.geometryId = id;
        this.textureWidth = width;
        this.textureHeight = height;
    }

    public static BedrockPackCatalog.Geometry choosePlayerGeometry(BedrockPackCatalog catalog) {
        var candidates = catalog.geometriesFor("minecraft:player");
        BedrockPackCatalog.Geometry best = null;
        int bestScore = Integer.MIN_VALUE;
        for (var geo : candidates) {
            // Never auto-select cosmetic overlays as whole body models:
            // original Bedrock render controllers hide/change their geometry.
            if (!BedrockModelSafety.assess(catalog, geo).worldSafe()) continue;
            if (geo.cubes() < 4 || geo.cubes() > MAX_CUBES || geo.bones().size() > MAX_BONES)
                continue;
            JsonObject desc = object(geo.originalSource(), "description");
            int width = integer(desc, "texture_width", 64);
            int height = integer(desc, "texture_height", 64);
            if (width <= 0 || height <= 0 || width > 2048 || height > 2048) continue;
            int score = 0;
            for (var bone : geo.bones()) {
                String name = bone.name().toLowerCase(Locale.ROOT).replace("_", "");
                if (name.equals("root")) score += 10;
                if (name.equals("waist") || name.equals("body")) score += 10;
                if (name.equals("head")) score += 10;
                if (name.equals("leftarm") || name.equals("rightarm")) score += 7;
                if (name.equals("leftleg") || name.equals("rightleg")) score += 7;
            }
            if (width == 64 && height == 64) score += 80;
            score += Math.min(geo.cubes(), 130) / 10;
            if (score > bestScore) { bestScore = score; best = geo; }
        }
        return best;
    }

    public static BedrockCubeModel build(BedrockPackCatalog.Geometry geometry) {
        JsonObject source = geometry.originalSource();
        JsonObject description = object(source, "description");
        int tw = integer(description, "texture_width", 64);
        int th = integer(description, "texture_height", 64);
        if (tw < 1 || th < 1 || tw > 2048 || th > 2048) throw new IllegalArgumentException("Invalid texture dimensions");
        JsonArray raw = array(source, "bones");
        if (raw == null || raw.size() > MAX_BONES) throw new IllegalArgumentException("Invalid bone count");

        Map<String, Node> nodes = new LinkedHashMap<>();
        int cubes = 0;
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject b = element.getAsJsonObject();
            String name = str(b, "name", "");
            if (name.isEmpty() || nodes.containsKey(name)) continue;
            String parent = str(b, "parent", "");
            float[] pivot = vector(b.get("pivot"), new float[] {0,0,0});
            float[] rotation = vector(b.get("rotation"), new float[] {0,0,0});
            JsonArray list = array(b, "cubes");
            if (list != null) cubes += list.size();
            if (cubes > MAX_CUBES) throw new IllegalArgumentException("Too many cubes in custom model");
            nodes.put(name, new Node(name, parent, pivot, rotation, list));
        }
        if (nodes.isEmpty()) throw new IllegalArgumentException("No valid bones");

        Map<String, List<Node>> children = new HashMap<>();
        List<Node> roots = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (nodes.containsKey(n.parent) && !n.parent.equals(n.name))
                children.computeIfAbsent(n.parent, k -> new ArrayList<>()).add(n);
            else roots.add(n);
        }
        ModelPartData base = new ModelData().getRoot();
        Set<String> added = new HashSet<>();
        Map<String,float[]> defaultRotations = new HashMap<>();
        for (Node root : roots) {
            buildNode(base, null, root, children, added, defaultRotations, 0);
        }
        for (Node node : nodes.values()) {
            if (!added.contains(node.name)) buildNode(base, null, node, children, added, defaultRotations, 0);
        }
        ModelPart model = base.createPart(tw, th);
        Map<String,ModelPart> parts = new HashMap<>();
        linkParts(base, model, parts, "");
        return new BedrockCubeModel(model, parts, defaultRotations, cubes, geometry.id(), tw, th);
    }

    private static void buildNode(ModelPartData destination, Node parent, Node node,
                                  Map<String,List<Node>> tree, Set<String> visited,
                                  Map<String,float[]> defaults, int depth) {
        if (depth > 96 || !visited.add(node.name)) return;
        float x = node.pivot[0] - (parent == null ? 0 : parent.pivot[0]);
        float y = (parent == null ? 24 : parent.pivot[1]) - node.pivot[1];
        float z = (parent == null ? 0 : parent.pivot[2]) - node.pivot[2];
        if (!valid(x) || !valid(y) || !valid(z)) return;
        ModelPartBuilder builder = ModelPartBuilder.create();
        if (node.cubes != null) {
            int i = 0;
            for (JsonElement element : node.cubes) {
                if (!element.isJsonObject()) continue;
                JsonObject cube = element.getAsJsonObject();
                float[] origin = vector(cube.get("origin"), null);
                float[] size = vector(cube.get("size"), null);
                if (origin == null || size == null) continue;
                if (Math.max(Math.max(size[0], size[1]), size[2]) > 96
                    || Math.min(Math.min(size[0], size[1]), size[2]) < 0) continue;
                // Bedrock coordinates: upward positive, unlike the Java model
                // coordinates where head height is y=0 and feet are y=24.
                float cx = origin[0] - node.pivot[0];
                float cy = node.pivot[1] - origin[1] - size[1];
                float cz = node.pivot[2] - origin[2] - size[2];
                int[] uv = uv(cube.get("uv"));
                builder.uv(uv[0],uv[1]).cuboid(node.name+"_"+i++,cx,cy,cz,
                    size[0],size[1],size[2]);
            }
        }
        // Bedrock model bone rotation is in degrees. Build all levels from
        // source parentage, not as a hard-coded vanilla six-part skeleton.
        ModelTransform rest = ModelTransform.of(x,y,z,
            radians(node.rotation[0]),radians(-node.rotation[1]),radians(-node.rotation[2]));
        ModelPartData child = destination.addChild(node.name,builder,rest);
        defaults.put(node.name, node.rotation.clone());
        for (Node descendant : tree.getOrDefault(node.name, List.of())) {
            buildNode(child,node,descendant,tree,visited,defaults,depth+1);
        }
    }

    private static void linkParts(ModelPartData data, ModelPart actual,
                                  Map<String,ModelPart> parts, String parent) {
        for (var entry : data.getChildren()) {
            String name = entry.getKey();
            if (actual.hasChild(name)) {
                ModelPart part = actual.getChild(name);
                parts.put(name,part);
                linkParts(entry.getValue(),part,parts,name);
            }
        }
    }

    public void reset() {
        for (ModelPart part : parts.values()) part.resetTransform();
    }

    private static int[] uv(JsonElement element) {
        if (element != null && element.isJsonArray() && element.getAsJsonArray().size() >= 2) {
            JsonArray array = element.getAsJsonArray();
            try { return new int[] {Math.round(array.get(0).getAsFloat()),Math.round(array.get(1).getAsFloat())}; }
            catch (RuntimeException ignored) { }
        }
        if (element != null && element.isJsonObject()) {
            JsonObject faces = element.getAsJsonObject();
            for (String side : List.of("north", "south", "east", "west", "up", "down")) {
                JsonObject face = object(faces,side);
                if (face != null) return uv(face.get("uv"));
            }
        }
        return new int[] {0,0};
    }

    private static JsonObject object(JsonObject j,String key) {
        JsonElement e = j==null?null:j.get(key);
        return e!=null && e.isJsonObject()?e.getAsJsonObject():null;
    }
    private static JsonArray array(JsonObject j,String key) {
        JsonElement e = j==null?null:j.get(key);
        return e!=null && e.isJsonArray()?e.getAsJsonArray():null;
    }
    private static String str(JsonObject j,String key,String fallback) {
        JsonElement e=j.get(key);
        try { return e!=null && e.isJsonPrimitive()?e.getAsString():fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
    private static int integer(JsonObject j,String key,int fallback) {
        try {return j!=null&&j.has(key)?j.get(key).getAsInt():fallback;}
        catch(RuntimeException ex){return fallback;}
    }
    private static float[] vector(JsonElement value,float[] fallback) {
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()!=3)return fallback;
        JsonArray arr=value.getAsJsonArray();
        float[] xyz=new float[3];
        for(int i=0;i<3;i++){
            try{xyz[i]=arr.get(i).getAsFloat();if(!valid(xyz[i]))return fallback;}
            catch(RuntimeException ex){return fallback;}
        }
        return xyz;
    }
    private static boolean valid(float value){return Float.isFinite(value)&&Math.abs(value)<100000;}
    private static float radians(float v){return (float)Math.toRadians(v);}
    private record Node(String name,String parent,float[] pivot,float[] rotation,JsonArray cubes) { }
}
