package dev.actionsstuff.bridge;

import java.util.Map;

/**
 * Null-safe lookup for the previous animation phase.
 * An entity's first rendered frame deliberately has no previous phase.
 */
public final class ClipLookup {
    private ClipLookup() { }

    public static <T> T previousOrNull(Map<String, T> clips, String previousPhase) {
        if (previousPhase == null || previousPhase.isBlank()) {
            return null;
        }
        return clips.get(previousPhase);
    }
}
