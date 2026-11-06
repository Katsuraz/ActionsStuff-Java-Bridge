package dev.actionsstuff.bridge.studio;

import net.minecraft.client.MinecraftClient;
import dev.actionsstuff.bridge.SourcePlayerMotion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

/**
 * Simple main menu for this EXPERIMENTAL Bedrock/Java bridge.
 *
 * "Import" means the user-provided Bedrock resource data is readable.
 * It does not claim the original Actions & Stuff pack is fully enabled in Java.
 * The legacy animation and 3D debug switches live in Advanced Tests.
 */
public final class BridgeDashboardScreen extends Screen {
    private static final int BG = 0xFF121923;
    private static final int CARD = 0xFF24313D;
    private static final int ACCENT = 0xFF75D5B7;
    private static final int TEXT = 0xFFDFE8EE;
    private static final int MUTED = 0xFFADBAC7;
    private static final int WARNING = 0xFFFFCB83;

    private final Screen previous;
    private boolean scanning;
    private ButtonWidget importButton;
    private ButtonWidget playerMotionButton;
    private String feedback = "Your original Bedrock pack stays private on this computer.";

    public BridgeDashboardScreen(Screen previous) {
        super(Text.literal("Actions & Stuff | Java Bridge"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int left = Math.max(10, (width - Math.min(width - 20, 600)) / 2);
        int right = width - left;
        int buttonWidth = Math.max(90, Math.min(230, right - left - 24));
        int x = left + 12;
        int y = 90;

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
            .dimensions(right - 65, 14, 65, 20).build());

        importButton = addDrawableChild(ButtonWidget.builder(Text.literal("1. Import my .mcpack"), b -> importPack())
            .dimensions(x, y, buttonWidth, 20).build());

        playerMotionButton = addDrawableChild(ButtonWidget.builder(motionLabel(), b -> toggleMotion())
            .dimensions(x, y + 29, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("3. Explore Animations (optional)"), b -> {
            if (client != null) client.setScreen(new BedrockStudioScreen(this));
        }).dimensions(x, y + 58, buttonWidth, 20).build());

        int advancedY = Math.max(y + 88, Math.min(252, height - 61));
        addDrawableChild(ButtonWidget.builder(Text.literal("Advanced Tests / Debugging"), b -> {
            if (client != null) client.setScreen(new BridgeAdvancedScreen(this));
        }).dimensions(x, advancedY, buttonWidth, 20).build());
    }

    private static Text motionLabel() {
        return Text.literal(SourcePlayerMotion.enabled() ? "2. Turn OFF Player Motion"
            : "2. Enable Player Motion");
    }

    private void toggleMotion() {
        if (SourcePlayerMotion.enabled()) {
            SourcePlayerMotion.disable();
            feedback = "Source-driven player motion stopped. Normal vanilla animation restored.";
        } else {
            BedrockPackCatalog pack = BedrockStudioScreen.latestImportedPack();
            if (pack == null) {
                feedback = "First click Import my .mcpack, then enable player motion.";
            } else {
                feedback = SourcePlayerMotion.enable(pack);
            }
        }
        if (playerMotionButton != null) playerMotionButton.setMessage(motionLabel());
    }

    private void importPack() {
        if (scanning) return;
        scanning = true;
        importButton.active = false;
        feedback = "Reading local .mcpack files...";
        Thread importer = new Thread(() -> {
            String result;
            BedrockPackCatalog catalog = null;
            try {
                List<Path> found = BedrockPackCatalog.discover(BedrockStudioScreen.packDirectory());
                if (found.isEmpty()) {
                    result = "No .mcpack found. Put your pack in config/bedrockbridge/packs.";
                } else {
                    catalog = BedrockPackCatalog.open(found.get(0));
                    result = "Imported " + catalog.filename + " for preview. NOT enabled in-game.";
                    if (found.size() > 1) result += " Other packs are selectable in Studio.";
                }
            } catch (Exception ex) {
                result = "Import failed: " + ex.getMessage();
            }
            final String status = result;
            final BedrockPackCatalog imported = catalog;
            MinecraftClient minecraft = MinecraftClient.getInstance();
            minecraft.execute(() -> {
                scanning = false;
                if (importButton != null) importButton.active = true;
                if (imported != null) {
                    SourcePlayerMotion.disable();
                    BedrockStudioScreen.rememberImportedPack(imported);
                }
                if (playerMotionButton != null) playerMotionButton.setMessage(motionLabel());
                feedback = status;
            });
        }, "ActionsStuff-PackImporter");
        importer.setDaemon(true);
        importer.start();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (playerMotionButton != null) playerMotionButton.setMessage(motionLabel());
        ctx.fill(0, 0, width, height, BG);
        int left = Math.max(10, (width - Math.min(width - 20, 600)) / 2);
        int right = width - left;

        int panelEnd = Math.min(height - 54, 268);
        ctx.fill(left, 44, right, Math.max(45, panelEnd), CARD);
        ctx.fill(left, 44, right, 46, ACCENT);

        line(ctx, "ACTIONS & STUFF", left, 16, ACCENT);
        line(ctx, "Java Bridge  |  Simple setup", left + 12, 54, TEXT);
        BedrockPackCatalog imported = BedrockStudioScreen.latestImportedPack();
        line(ctx, imported == null ? "My pack: NOT IMPORTED" :
            compact("My pack: IMPORTED - " + imported.filename, right - left - 24),
            left + 12, 73, imported == null ? WARNING : ACCENT);

        line(ctx, "IN-GAME PLAYER MOTION", left + 12, 183, ACCENT);
        line(ctx, SourcePlayerMotion.enabled()
                ? "SOURCE PLAYER MOTION: ON (" + SourcePlayerMotion.playableClipCount() + " clips)"
                : "SOURCE PLAYER MOTION: OFF",
            left + 12, 200, SourcePlayerMotion.enabled() ? ACCENT : MUTED);
        line(ctx, "Full pack (textures / mobs / accessories): NOT READY",
            left + 12, 215, WARNING);
        if (SourcePlayerMotion.enabled()) {
            line(ctx, compact("Now playing: " + SourcePlayerMotion.recentPhase(),
                right - left - 24), left + 12, 229, MUTED);
        } else {
            line(ctx, "Legacy test poses remain under Advanced (not recommended).",
                left + 12, 229, MUTED);
        }

        line(ctx, compact(feedback, right - left - 10),
             left, height - 29, MUTED);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void line(DrawContext ctx, String text, int x, int y, int color) {
        ctx.drawText(textRenderer, text, x, y, color, false);
    }

    private static String compact(String input, int pixelWidth) {
        int max = Math.max(8, pixelWidth / 6);
        return input.length() <= max ? input : input.substring(0, Math.max(4, max - 3)) + "...";
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        if (client != null) client.setScreen(previous);
    }
}
