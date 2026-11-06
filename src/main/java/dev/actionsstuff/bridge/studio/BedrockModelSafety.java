package dev.actionsstuff.bridge.studio;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bedrock render-controller visibility gate for the EXPERIMENTAL in-world 3D
 * player layer. Head/face/accessory geometries must never be rendered as
 * independent full-body models with every cube enabled.
 *
 * Read-only Studio inspection remains available for every source geometry.
 */
public final class BedrockModelSafety {
    private BedrockModelSafety() {}

    public record Assessment(boolean worldSafe, String kind, String reason,
                             String controllerId, String condition) { }

    public static Assessment assess(BedrockPackCatalog catalog, BedrockPackCatalog.Geometry geo) {
        if (catalog == null || geo == null)
            return new Assessment(false, "missing", "Choose a local player model first", "", "");
        BedrockPackCatalog.EntityInfo player = catalog.entities.get("minecraft:player");
        if (player == null)
            return new Assessment(false, "unknown", "No source player entity definition", "", "");

        String alias = null;
        for (var e : player.geometry().entrySet()) {
            if (e.getValue().equals(geo.id())) { alias = e.getKey(); break; }
        }
        if (alias == null)
            return new Assessment(false, "unmapped", "Geometry is not mapped to the player", "", "");

        String controllerId = "";
        String condition = "";
        for (var binding : player.renderBindings()) {
            var rule = catalog.renderControllers.get(binding.controllerId());
            if (rule == null) continue;
            if (rule.geometryRef().equalsIgnoreCase("Geometry." + alias)) {
                controllerId = binding.controllerId();
                condition = binding.condition();
                // Any source render controller may use conditional textures and
                // materials; until those are implemented, it cannot be treated
                // as an unconditional vanilla-skin mesh.
                break;
            }
        }

        Set<String> names = new HashSet<>();
        Set<String> cubeBones = new HashSet<>();
        for (var bone : geo.bones()) {
            String normalized = bone.name().toLowerCase(Locale.ROOT).replace("_", "");
            names.add(normalized);
            if (bone.cubes() > 0) cubeBones.add(normalized);
        }
        boolean hasArms = names.contains("leftarm") && names.contains("rightarm");
        boolean hasLegs = names.contains("leftleg") && names.contains("rightleg");
        boolean headAndBody = names.contains("head") && names.contains("body");
        boolean solidAvatar = hasArms && hasLegs && headAndBody
                && (cubeBones.contains("body") || cubeBones.contains("torso"))
                && cubeBones.contains("head")
                && geo.cubes() >= 6;

        if (!solidAvatar) {
            String kind = headAndBody && !hasArms && !hasLegs ? "head / face accessory"
                : "conditional accessory / partial geometry";
            String note = controllerId.isEmpty() ? "" : " (controlled by " + controllerId + ")";
            return new Assessment(false, kind,
                "Hidden: source is " + kind + note + "; not a complete player mesh",
                controllerId, condition);
        }
        if (!controllerId.isEmpty()) {
            String detail = condition.isEmpty() ? "" : " when " + condition;
            return new Assessment(false, "controller-gated body mesh",
                "Hidden until source render-controller materials/visibility are supported: "
                    + controllerId + detail,
                controllerId, condition);
        }
        return new Assessment(true, "standalone full-body mesh",
            "Full-body cube mesh allowed in experimental overlay", "", "");
    }
}
