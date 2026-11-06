package dev.actionsstuff.bridge.studio;

import dev.actionsstuff.bridge.BridgeAnimations;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Session-scoped, safe-first entry screen for Mod Menu and the bridge.
 * Clear separation between legacy six-part pose adapter and the original
 * Bedrock 3D geometry layer; both start OFF unless explicitly activated.
 */
public final class BridgeDashboardScreen extends Screen {
    private static final int BG = 0xFF141B24;
    private static final int CARD = 0xFF222E39;
    private static final int BORDER = 0xFF405564;
    private static final int TEXT = 0xFFE3EBEF;
    private static final int MUTED = 0xFFA5B6C1;
    private static final int ACCENT = 0xFF74D5B8;
    private static final int WARNING = 0xFFFFCC82;

    private final Screen previous;
    private String message = "No renderer changes made. Import the original Bedrock pack in Studio.";

    public BridgeDashboardScreen(Screen previous) {
        super(Text.literal("Bedrock Pack Bridge | Controls"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int left = Math.max(12, (width - Math.min(width - 24, 880)) / 2);
        int right = width - left;
        int contentWidth = right - left;
        int buttonHeight = 20;

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
            .dimensions(right - 74, 15, 74, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Open Animation Studio"), b -> {
            if (client != null) client.setScreen(new BedrockStudioScreen(this));
        }).dimensions(left + 12, 79, Math.min(220, contentWidth - 24), buttonHeight).build());

        addDrawableChild(ButtonWidget.builder(legacyLabel(), b -> {
            if (BridgeAnimations.isEnabled()) {
                BridgeAnimations.disable();
                message = "Legacy animation adapter disabled. This does not disable OTHER installed mods.";
            } else if (BridgeAnimations.loadedClipCount() == 0) {
                message = "No legacy source clips loaded. Open Studio and import your original .mcpack first.";
            } else {
                OriginalRigRuntime.disable();
                BridgeAnimations.enable();
                message = "Legacy six-part source clip adapter enabled for this session.";
            }
            b.setMessage(legacyLabel());
        }).dimensions(left + 12, 160, Math.min(240, contentWidth - 24), buttonHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Disable 3D overlay"), b -> {
            OriginalRigRuntime.disable();
            message = "This bridge's source geometry overlay is now disabled.";
        }).dimensions(left + 12, 210, Math.min(240, contentWidth - 24), buttonHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Controller layers: " +
            (OriginalRigRuntime.controllersEnabled() ? "ON" : "OFF")), b -> {
            boolean on = OriginalRigRuntime.toggleControllers();
            b.setMessage(Text.literal("Controller layers: " + (on ? "ON" : "OFF")));
            message = on ? "Experimental controller layers ON; useful only with an active 3D source rig."
                : "Experimental controller layers OFF.";
        }).dimensions(left + 12, 260, Math.min(240, contentWidth - 24), buttonHeight).build());
    }

    private static Text legacyLabel() {
        return Text.literal("Legacy player poses: " + (BridgeAnimations.isEnabled() ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, BG);
        int left = Math.max(12, (width - Math.min(width - 24, 880)) / 2);
        int right = width - left;
        int w = right - left;
        ctx.fill(left, 45, right, Math.min(height - 30, 315), CARD);
        ctx.fill(left, 45, right, 47, ACCENT);

        label(ctx, "BEDROCK PACK BRIDGE", left, 18, ACCENT);
        label(ctx, "FABRIC 1.21.11  |  SETTINGS & DIAGNOSTICS", left + 12, 55, TEXT);
        label(ctx, "Browse imported geometry, bones, clips and models.", left + 12, 111, MUTED);

        label(ctx, "LEGACY ANIMATION ENGINE", left + 12, 137, ACCENT);
        label(ctx, "Six-part vanilla player posing. Disabled by default to prevent overlapping animations.",
            left + 12, 184, MUTED);

        label(ctx, "SOURCE 3D OVERLAY", left + 12, 194, ACCENT);
        label(ctx, "Active: " + (OriginalRigRuntime.enabled() ? "YES" : "NO"), left + 267, 216,
            OriginalRigRuntime.enabled() ? WARNING : ACCENT);
        label(ctx, "Original attachments may require visibility rules and textures.", left + 12, 233, MUTED);

        label(ctx, "BEDROCK CONTROLLERS (EXPERIMENTAL)", left + 12, 245, ACCENT);
        label(ctx, "Partial MoLang and controller states only; requires a supported source rig.",
            left + 12, 286, MUTED);

        int y = 333;
        label(ctx, "CONFLICT CHECK", left, y, ACCENT);
        List<String> conflicts = findPotentialAnimationMods();
        if (conflicts.isEmpty()) {
            label(ctx, "No other recognizable Actions / animation mod found by name.",
                left, y + 18, MUTED);
        } else {
            label(ctx, "Other candidate animation mods detected (not proof of a conflict):",
                left, y + 18, WARNING);
            int idx = 0;
            for (String name : conflicts) {
                if (++idx > 4) break;
                label(ctx, fit(name, w - 14), left + 10, y + 20 + idx * 15, TEXT);
            }
        }

        label(ctx, "Known issue: older separate test JARs or resource packs can draw artifacts",
            left, Math.max(y + 96, height - 82), WARNING);
        label(ctx, fit(message, w), left, height - 35, MUTED);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private static List<String> findPotentialAnimationMods() {
        List<String> matches = new ArrayList<>();
        for (var installed : FabricLoader.getInstance().getAllMods()) {
            var meta = installed.getMetadata();
            if (meta.getId().equals("actions_stuff_bridge")) continue;
            String value = (meta.getId() + " " + meta.getName()).toLowerCase(Locale.ROOT);
            if (value.contains("actions") && value.contains("stuff")
                || value.contains("punchy")
                || value.contains("playeranim")
                || value.contains("bettercombat")) {
                matches.add(meta.getName() + " [" + meta.getId() + "]");
            }
        }
        return matches;
    }
    private void label(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(textRenderer, fit(s, Math.max(70, width - x - 12)), x, y, color, false);
    }
    private static String fit(String s, int available) {
        int limit = Math.max(6, available / 6);
        return s.length() <= limit ? s : s.substring(0, Math.max(3, limit - 3)) + "...";
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() {
        if (client != null) client.setScreen(previous);
    }
}
