package dev.actionsstuff.bridge.studio;

import net.minecraft.client.MinecraftClient;
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

        addDrawableChild(ButtonWidget.builder(Text.literal("2. Open Animation Studio"), b -> {
            if (client != null) client.setScreen(new BedrockStudioScreen(this));
        }).dimensions(x, y + 28, buttonWidth, 20).build());

        int advancedY = Math.min(229, Math.max(y + 56, height - 64));
        addDrawableChild(ButtonWidget.builder(Text.literal("Advanced Tests / Debugging"), b -> {
            if (client != null) client.setScreen(new BridgeAdvancedScreen(this));
        }).dimensions(x, advancedY, buttonWidth, 20).build());
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
                if (imported != null) BedrockStudioScreen.rememberImportedPack(imported);
                feedback = status;
            });
        }, "ActionsStuff-PackImporter");
        importer.setDaemon(true);
        importer.start();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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

        line(ctx, "IN-GAME STATUS", left + 12, 158, ACCENT);
        line(ctx, "Full Actions & Stuff port: NOT READY", left + 12, 177, WARNING);
        line(ctx, compact("Importing lets you inspect animations; it does not enable",
                    right - left - 24), left + 12, 194, TEXT);
        line(ctx, "the complete Bedrock pack in Minecraft Java.", left + 12, 207, TEXT);

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
