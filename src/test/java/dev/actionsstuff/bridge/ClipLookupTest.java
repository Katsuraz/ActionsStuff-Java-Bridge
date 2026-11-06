package dev.actionsstuff.bridge;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClipLookupTest {
    @Test
    void firstRenderWithNullPreviousPhaseDoesNotThrow() {
        var clips = Map.copyOf(Map.of("idle", "idleClip"));
        assertNull(assertDoesNotThrow(() -> ClipLookup.previousOrNull(clips, null)));
    }

    @Test
    void existingPreviousPhaseIsReturned() {
        var clips = Map.copyOf(Map.of("idle", "idleClip"));
        assertEquals("idleClip", ClipLookup.previousOrNull(clips, "idle"));
    }

    @Test
    void missingPreviousPhaseReturnsNull() {
        var clips = Map.copyOf(Map.of("idle", "idleClip"));
        assertNull(ClipLookup.previousOrNull(clips, "nonexistent"));
        assertNull(ClipLookup.previousOrNull(clips, ""));
    }
}
