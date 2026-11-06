package dev.actionsstuff.bridge.studio;

import dev.actionsstuff.bridge.BridgeAnimations;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-game 2D original-bone inspector for local user-provided Bedrock packs.
 *
 * NOT a full 3D Bedrock renderer. Does not emulate MoLang or Bedrock behavior
 * packs. Original third-party assets remain only on the player's computer.
 */
public final class BedrockStudioScreen extends Screen {
    private static final int BG = 0xF3121822;
    private static final int PANEL = 0xEC222C38;
    private static final int TEXT = 0xFFE3E8F0;
    private static final int MUTED = 0xFF91A0B2;
    private static final int ACCENT = 0xFF65D3AC;
    private static final int WARNING = 0xFFE6B96D;
    private final Screen back;
    private BedrockPackCatalog catalog;
    private static volatile BedrockPackCatalog latestImportedPack;
    private ButtonWidget source3dButton;
    private ButtonWidget controllersButton;
    private List<Path> paths = List.of();
    private List<String> entityNames = List.of();
    private List<BedrockPackCatalog.Geometry> geoList = List.of();
    private List<BedrockPackCatalog.AnimationOption> clipList = List.of();
    private String status = "No pack selected";
    private boolean loading;
    private boolean playing = true;
    private boolean useInGame;
    private int fileIndex, entityIndex, geometryIndex, clipIndex, boneIndex;
    private float seconds;
    private float speed = 1.0f;

    public BedrockStudioScreen(Screen back) {
        super(Text.literal("Bedrock Pack Bridge Studio"));
        this.back = back;
    }

    public static Path packDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("bedrockbridge").resolve("packs");
    }

    /** Dashboard may be opened directly through Mod Menu, without re-reading assets. */
    public static BedrockPackCatalog latestImportedPack() { return latestImportedPack; }
    public BedrockPackCatalog currentPack() { return catalog; }
    public BedrockPackCatalog.Geometry currentGeometry() { return selectedGeometry(); }

    private String source3dLabel() {
        if (OriginalRigRuntime.enabled()) return "3D: ON";
        if (catalog == null) return "3D: NEED PACK";
        var geo = selectedGeometry();
        if (geo == null) return "3D: NO MODEL";
        return BedrockModelSafety.assess(catalog, geo).worldSafe() ? "3D: OFF" : "3D: BLOCKED";
    }

    private String controllerLabel() {
        if (OriginalRigRuntime.controllersEnabled()) return "Controllers: ON";
        return OriginalRigRuntime.controllersAvailable() ? "Controllers: OFF" : "Controllers: NEED 3D";
    }

    private void refreshToggleLabels() {
        if (source3dButton != null) source3dButton.setMessage(Text.literal(source3dLabel()));
        if (controllersButton != null) controllersButton.setMessage(Text.literal(controllerLabel()));
    }

    @Override
    protected void init() {
        int left = 14, gap = 6, h = 20;
        int full = Math.max(280, width - 28);
        int segment = Math.max(42, (full - gap * 5) / 6);
        // Four compact tool rows; no hard-coded x=600 button overflow.
        addButton("Scan packs", left, 23, segment * 2 + gap, h, this::scan);
        addButton("< Pack", left + (segment + gap) * 2, 23, segment, h, () -> chooseFile(-1));
        addButton("Pack >", left + (segment + gap) * 3, 23, segment, h, () -> chooseFile(1));
        addButton("Settings", left + (segment + gap) * 4, 23, segment, h,
            () -> { if(client != null) client.setScreen(new BridgeDashboardScreen(this)); });
        addButton("Close", left + (segment + gap) * 5, 23, segment, h, this::close);

        addButton("< Entity", left, 49, segment, h, () -> entity(-1));
        addButton("Entity >", left + (segment + gap), 49, segment, h, () -> entity(1));
        addButton("< Model", left + (segment + gap) * 2, 49, segment, h, () -> geometry(-1));
        addButton("Model >", left + (segment + gap) * 3, 49, segment, h, () -> geometry(1));
        addButton("< Clip", left + (segment + gap) * 4, 49, segment, h, () -> clip(-1));
        addButton("Clip >", left + (segment + gap) * 5, 49, segment, h, () -> clip(1));

        addButton("Play / Pause", left, 75, segment, h, () -> playing = !playing);
        addButton("Back 0.2s", left + (segment + gap), 75, segment, h, () -> seek(-0.2f));
        addButton("Next 0.2s", left + (segment + gap) * 2, 75, segment, h, () -> seek(0.2f));
        addButton("Speed -", left + (segment + gap) * 3, 75, segment, h,
            () -> speed = Math.max(0.25f, speed / 2));
        addButton("Speed +", left + (segment + gap) * 4, 75, segment, h,
            () -> speed = Math.min(4f, speed * 2));
        addButton("Bone +", left + (segment + gap) * 5, 75, segment, h, () -> boneIndex++);

        int third = Math.max(60, (full - gap * 2) / 3);
        addButton("Legacy player poses (toggle)", left, 101, third, h, this::toggleLegacy);
        source3dButton = addButton("3D: OFF", left + third + gap, 101, third, h, this::toggle3d);
        controllersButton = addButton("Controllers: NEED 3D", left + (third + gap) * 2, 101, third, h,
            this::toggleControllers);
        refreshToggleLabels();

        if (catalog == null && !loading) scan();
    }

    private ButtonWidget addButton(String label, int x, int y, int w, int h, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, w, h).build());
    }

    private void toggleControllers() {
        if (!OriginalRigRuntime.controllersAvailable()) {
            status = OriginalRigRuntime.controllerBlockReason();
            refreshToggleLabels();
            return;
        }
        boolean enabled = OriginalRigRuntime.toggleControllers();
        status = "Partial source controller playback " + (enabled ? "ON" : "OFF");
        refreshToggleLabels();
    }

    private void toggleLegacy() {
        if (BridgeAnimations.isEnabled()) {
            BridgeAnimations.disable();
            useInGame = false;
            status = "Legacy vanilla-biped poses OFF (original 3D layer unchanged)";
        } else {
            enablePlayer();
        }
    }

    private void scan() {
        if (loading) return;
        loading = true;
        status = "Scanning config/bedrockbridge/packs...";
        Thread worker = new Thread(() -> {
            try {
                List<Path> found = BedrockPackCatalog.discover(packDirectory());
                if (found.isEmpty()) {
                    MinecraftClient.getInstance().execute(() -> {
                        paths = found;
                        catalog = null;
                        loading = false;
                        status = "Place your .mcpack in " + packDirectory().toAbsolutePath();
                    });
                } else {
                    int ix = Math.floorMod(fileIndex, found.size());
                    Path selected = found.get(ix);
                    BedrockPackCatalog imported = BedrockPackCatalog.open(selected);
                    MinecraftClient.getInstance().execute(() -> accept(found, ix, imported));
                }
            } catch (Exception ex) {
                MinecraftClient.getInstance().execute(() -> {
                    loading = false;
                    status = "Import failed: " + ex.getMessage();
                });
            }
        }, "BedrockStudio-Importer");
        worker.setDaemon(true);
        worker.start();
    }

    private void chooseFile(int step) {
        if (loading || paths.isEmpty()) return;
        fileIndex = Math.floorMod(fileIndex + step, paths.size());
        scan();
    }

    private void accept(List<Path> found, int ix, BedrockPackCatalog imported) {
        loading = false;
        paths = List.copyOf(found);
        fileIndex = ix;
        catalog = imported;
        latestImportedPack = imported;
        entityNames = catalog.entityNames();
        entityIndex = geometryIndex = clipIndex = boneIndex = 0;
        seconds = 0;
        chooseEntity();
        status = "Read " + imported.decodedDocuments + " Bedrock JSON files; no assets were uploaded";
    }

    private void chooseEntity() {
        if (catalog == null || entityNames.isEmpty()) return;
        String id = entityNames.get(Math.floorMod(entityIndex, entityNames.size()));
        geoList = catalog.geometriesFor(id);
        clipList = catalog.animationsFor(id);
        geometryIndex = boneIndex = clipIndex = 0;
        seconds = 0;
        refreshToggleLabels();
        for (int i = 0; i < clipList.size(); i++) {
            if (clipList.get(i).alias().equals("walk")) {
                clipIndex = i;
                break;
            }
        }
    }

    private void entity(int step) {
        if (entityNames.isEmpty()) return;
        entityIndex = Math.floorMod(entityIndex + step, entityNames.size());
        chooseEntity();
    }
    private void geometry(int step) {
        if (geoList.isEmpty()) return;
        geometryIndex = Math.floorMod(geometryIndex + step, geoList.size());
        boneIndex = 0;
        refreshToggleLabels();
    }
    private void clip(int step) {
        if (clipList.isEmpty()) return;
        clipIndex = Math.floorMod(clipIndex + step, clipList.size());
        seconds = 0;
    }
    private void seek(float s) {
        seconds = Math.max(0f, seconds + s);
        BedrockPackCatalog.AnimationOption chosen = clipOption();
        if (chosen != null) seconds = Math.min(seconds, chosen.animation().seconds());
    }

    private void enablePlayer() {
        if (catalog == null) {
            status = "Import a pack first";
            return;
        }
        // Keep experimental old and new render paths isolated for diagnosis.
        OriginalRigRuntime.disable();
        int loaded = BridgeAnimations.loadBedrockPlayer(catalog);
        useInGame = loaded > 0;
        refreshToggleLabels();
        status = useInGame ? "Loaded " + loaded + " source-derived clips into player renderer"
            : "No usable numeric player clips found; expressions may require MoLang";
    }

    private void toggle3d() {
        if (OriginalRigRuntime.enabled()) {
            OriginalRigRuntime.disable();
            status = "Source 3D overlay OFF; vanilla character is still rendered";
            refreshToggleLabels();
            return;
        }
        if (catalog == null) {
            status = "Import your private .mcpack first; Studio cannot activate an unloaded model";
            refreshToggleLabels();
            return;
        }
        BedrockPackCatalog.Geometry selected = selectedGeometry();
        // Do not switch the user out of the legacy adapter unless the 3D model
        // has ACTUALLY passed all safety/geometry checks.
        status = OriginalRigRuntime.enable(catalog, selected);
        if (OriginalRigRuntime.enabled()) {
            BridgeAnimations.disable();
            useInGame = false;
            status += " | experimental geometry; texture/controller fidelity incomplete";
        }
        refreshToggleLabels();
    }

    private BedrockPackCatalog.AnimationOption clipOption() {
        return clipList.isEmpty() ? null : clipList.get(Math.floorMod(clipIndex, clipList.size()));
    }
    private BedrockPackCatalog.Geometry selectedGeometry() {
        return geoList.isEmpty() ? null : geoList.get(Math.floorMod(geometryIndex, geoList.size()));
    }

    @Override
    public void tick() {
        if (!playing) return;
        BedrockPackCatalog.AnimationOption selected = clipOption();
        if (selected == null) return;
        var animation = selected.animation();
        seconds += 0.05f * speed;
        if (seconds > animation.seconds()) {
            seconds = animation.looping() ? seconds % animation.seconds() : animation.seconds();
            if (!animation.looping()) playing = false;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        // Minecraft 1.21.11 permits screen background blur only once per frame.
        // The studio draws an opaque panel below, so do not request blur here.
        int top = 150, bottom = Math.max(top + 100, height - 50);
        int left = 14;
        int column = Math.max(220, width / 3);
        int detailsX = left + column + 10;
        int rightX = Math.max(detailsX + 210, width * 2 / 3);
        ctx.fill(0, 0, width, height, BG);
        ctx.fill(left, top, detailsX - 5, bottom, PANEL);
        ctx.fill(detailsX, top, Math.max(detailsX + 190, rightX - 6), bottom, PANEL);
        ctx.fill(rightX, top, width - 14, bottom, PANEL);
        line(ctx, "BEDROCK PACK BRIDGE / STUDIO", 14, 8, ACCENT);
        refreshToggleLabels();
        line(ctx, compact(status, width - 35), 14, height - 25, loading ? WARNING : MUTED);
        if (catalog != null && !OriginalRigRuntime.enabled()) {
            var selected = selectedGeometry();
            if (selected != null) {
                var safety = BedrockModelSafety.assess(catalog, selected);
                if (!safety.worldSafe()) {
                    line(ctx, compact("3D BLOCKED: " + safety.reason(), width - 35),
                        14, height - 39, WARNING);
                }
            }
        }
        if (catalog == null) {
            line(ctx, "No pack imported. Copy a .mcpack into:", 28, top + 20, TEXT);
            line(ctx, compact(packDirectory().toString(), column - 20), 28, top + 36, WARNING);
            line(ctx, "Press Scan / Reload.", 28, top + 56, ACCENT);
            super.render(ctx, mouseX, mouseY, deltaTicks);
            return;
        }

        String entityId = entityNames.isEmpty() ? "none" : entityNames.get(entityIndex);
        line(ctx, compact("LOCAL PACK  |  " + catalog.filename, width - 30), 15, 134, MUTED);
        line(ctx, "Entity: " + entityId, left + 8, top + 9, TEXT);
        line(ctx, "Models " + geoList.size() + " | Clips " + clipList.size(), left + 8, top + 23, MUTED);
        line(ctx, "Bones (top 27, + to inspect next)", left + 8, top + 40, ACCENT);

        BedrockPackCatalog.Geometry geo = selectedGeometry();
        if (geo != null) {
            line(ctx, compact(geo.id(), column - 28), left + 8, top + 54, MUTED);
            line(ctx, "Bones " + geo.bones().size() + " | Cubes " + geo.cubes(), left + 8, top + 68, MUTED);
            var assessment = BedrockModelSafety.assess(catalog, geo);
            line(ctx, compact("Role: " + assessment.kind(), column - 30),
                left + 8, top + 83, assessment.worldSafe() ? ACCENT : WARNING);
            int limit = Math.max(3, Math.min(24, (bottom - top - 102) / 11));
            List<BedrockPackCatalog.Bone> bones = geo.bones();
            for (int i = 0; i < Math.min(limit, bones.size()); i++) {
                int at = (Math.floorMod(boneIndex, Math.max(1, bones.size())) + i) % bones.size();
                var bone = bones.get(at);
                String label = (i == 0 ? "» " : "  ") + bone.name();
                line(ctx, compact(label, column - 30), left + 10, top + 98 + 11 * i, i == 0 ? ACCENT : TEXT);
            }
        } else {
            line(ctx, "No matching geometry.", left + 8, top + 58, WARNING);
        }

        var chosen = clipOption();
        line(ctx, "Animation preview (numeric tracks)", detailsX + 8, top + 9, ACCENT);
        if (chosen != null) {
            var a = chosen.animation();
            line(ctx, compact(chosen.alias(), rightX - detailsX - 24), detailsX + 8, top + 25, TEXT);
            line(ctx, compact(a.id(), rightX - detailsX - 24), detailsX + 8, top + 39, MUTED);
            line(ctx, String.format(Locale.ROOT, "%.2f / %.2f s | %.2fx", seconds, a.seconds(), speed),
                detailsX + 8, top + 54, TEXT);
            line(ctx, "Bones " + a.bones().size() + " | Expressions " + a.expressionChannels(),
                detailsX + 8, top + 68, a.expressionChannels() > 0 ? WARNING : MUTED);
            int tx = detailsX + 8, tw = Math.max(20, rightX - detailsX - 24), ty = top + 92;
            ctx.fill(tx, ty, tx + tw, ty + 4, 0xFF4C5563);
            ctx.fill(tx, ty, tx + Math.round(tw * Math.max(0f, Math.min(1f, seconds / a.seconds()))),
                ty + 4, ACCENT);
            if (geo != null && !geo.bones().isEmpty()) {
                var bone = geo.bones().get(Math.floorMod(boneIndex, geo.bones().size()));
                float[] rotation = a.rotation(bone.name(), seconds);
                line(ctx, compact("Selected bone: " + bone.name(), rightX - detailsX - 18),
                    detailsX + 8, ty + 16, TEXT);
                if (rotation != null) {
                    line(ctx, String.format(Locale.ROOT, "Rot XYZ: %.1f / %.1f / %.1f", rotation[0],rotation[1],rotation[2]),
                        detailsX + 8, ty + 30, ACCENT);
                } else line(ctx, "No numeric rotation for this bone", detailsX + 8, ty + 30, WARNING);
                line(ctx, "Pivot: " + bone.x() + ", " + bone.y() + ", " + bone.z(),
                    detailsX + 8, ty + 44, MUTED);
            }
        } else {
            line(ctx, "No matching animation clips.", detailsX + 8, top + 36, WARNING);
        }
        line(ctx, "Animated 2D bone pivots", rightX + 8, top + 9, ACCENT);
        drawBones(ctx, geo, chosen, rightX + 9, top + 34,
                Math.max(40, width - rightX - 27), Math.max(45, bottom - top - 125));
        // Display discrete, non-overlapping status lines. These switches are
        // independent, and the legacy adapter is OFF until the user enables it.
        line(ctx, "SOURCE GEOMETRY PREVIEW | 2D", rightX + 8, bottom - 77, MUTED);
        line(ctx, "Source UV / materials: incomplete", rightX + 8, bottom - 64, WARNING);
        line(ctx, "Legacy poses: " + (BridgeAnimations.isEnabled() ? "ON" : "OFF"),
            rightX + 8, bottom - 48, BridgeAnimations.isEnabled() ? WARNING : ACCENT);
        line(ctx, "3D geometry overlay: " + (OriginalRigRuntime.enabled() ? "ON" : "OFF"),
            rightX + 8, bottom - 34, OriginalRigRuntime.enabled() ? WARNING : MUTED);
        line(ctx, "Controller layers: " + (OriginalRigRuntime.controllersEnabled() ? "ON" : "OFF"),
            rightX + 8, bottom - 20, OriginalRigRuntime.controllersEnabled() ? WARNING : MUTED);
        super.render(ctx, mouseX, mouseY, deltaTicks);
    }

    private record Joint(float x, float y, float angle) { }

    /**
     * Project the source Bedrock pivot hierarchy into 2D while applying
     * original numeric animation rotations around parent pivots.
     * Debug wireframe only, NOT textured or fully 3D model rendering.
     */
    private void drawBones(DrawContext ctx, BedrockPackCatalog.Geometry model,
                           BedrockPackCatalog.AnimationOption option, int x, int y, int w, int h) {
        if (model == null || model.bones().isEmpty()) return;
        List<BedrockPackCatalog.Bone> bones = model.bones();
        int max = Math.min(360, bones.size());
        Map<String, BedrockPackCatalog.Bone> lookup = new HashMap<>();
        Map<String, Joint> solved = new HashMap<>();
        Map<String, Boolean> visiting = new HashMap<>();
        var animation = option == null ? null : option.animation();
        for (var b : bones) lookup.putIfAbsent(b.name(), b);

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < max; i++) {
            var point = resolve(bones.get(i), lookup, solved, visiting, animation, 0);
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        if (!Float.isFinite(minX) || !Float.isFinite(maxX) ||
            !Float.isFinite(minY) || !Float.isFinite(maxY)) return;

        float spanX = Math.max(1f, maxX - minX);
        float spanY = Math.max(1f, maxY - minY);
        float scale = Math.min((w - 12f) / spanX, (h - 12f) / spanY);
        scale = Math.min(9f, Math.max(0.05f, scale));
        float middleX = (minX + maxX) / 2f;
        float middleY = (minY + maxY) / 2f;

        for (int i = 0; i < max; i++) {
            var b = bones.get(i);
            var point = solved.get(b.name());
            if (point == null) continue;
            int bx = x + w/2 + Math.round((point.x - middleX) * scale);
            int by = y + h/2 - Math.round((point.y - middleY) * scale);
            Joint parent = solved.get(b.parent());
            if (parent != null) {
                int px = x + w/2 + Math.round((parent.x - middleX) * scale);
                int py = y + h/2 - Math.round((parent.y - middleY) * scale);
                drawThinLine(ctx, px, py, bx, by, 0xFF687787);
            }
            boolean selected = i == Math.floorMod(boneIndex, bones.size());
            int color = selected ? 0xFFF6D06B : b.cubes() > 0 ? ACCENT : 0xFFACB6C2;
            ctx.fill(bx - (selected ? 2 : 1), by - (selected ? 2 : 1),
                bx + (selected ? 3 : 2), by + (selected ? 3 : 2), color);
        }
    }

    private Joint resolve(BedrockPackCatalog.Bone bone,
                          Map<String, BedrockPackCatalog.Bone> lookup,
                          Map<String, Joint> solved, Map<String, Boolean> visiting,
                          BedrockPackCatalog.Animation anim, int depth) {
        Joint cached = solved.get(bone.name());
        if (cached != null) return cached;
        if (depth > 96 || visiting.putIfAbsent(bone.name(), true) != null)
            return new Joint(bone.x(), bone.y(), 0);
        BedrockPackCatalog.Bone parent = lookup.get(bone.parent());
        Joint parentJoint = parent == null ? null
            : resolve(parent, lookup, solved, visiting, anim, depth + 1);
        float angle = parentJoint == null ? 0 : parentJoint.angle;
        float x, y;
        if (parentJoint == null) {
            x = bone.x();
            y = bone.y();
        } else {
            float dx = bone.x() - parent.x();
            float dy = bone.y() - parent.y();
            double ca = Math.cos(angle), sa = Math.sin(angle);
            x = parentJoint.x + (float)(dx * ca - dy * sa);
            y = parentJoint.y + (float)(dx * sa + dy * ca);
        }
        if (anim != null) {
            var channel = anim.bones().get(bone.name());
            if (channel != null && channel.position() != null) {
                float[] move = channel.position().sample(seconds);
                if (move != null) { x += move[0]; y += move[1]; }
            }
            float[] rot = anim.rotation(bone.name(), seconds);
            if (rot != null && Float.isFinite(rot[2])) {
                angle += Math.toRadians(rot[2]);
            }
        }
        Joint result = new Joint(x,y,angle);
        visiting.remove(bone.name());
        solved.put(bone.name(), result);
        return result;
    }

    private static void drawThinLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps > 400) return;
        for (int i = 0; i <= steps; i++) {
            float f = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * f), y = Math.round(y0 + (y1 - y0) * f);
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }
    private void line(DrawContext context, String str, int x, int y, int color) {
        context.drawText(this.textRenderer, str, x, y, color, false);
    }
    private static String compact(String v, int widthPixels) {
        int count = Math.max(5, widthPixels / 6);
        return v.length() <= count ? v : v.substring(0, Math.max(2, count - 3)) + "...";
    }
    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(back);
    }
}
