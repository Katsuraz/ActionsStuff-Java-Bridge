package dev.actionsstuff.bridge;

/** Pure, deterministic Bedrock-source player timing for both tests and runtime. */
public final class SourceMotionTiming {
    private SourceMotionTiming() { }

    public static float rate(float previous, float horizontalSpeed, boolean sprinting, float deltaTicks) {
        if (!Float.isFinite(previous)) previous = 1f;
        if (!Float.isFinite(horizontalSpeed)) horizontalSpeed = 0f;
        float target = clamp(horizontalSpeed / (sprinting ? 0.18f : 0.11f), 0.7f, 1.7f);
        return lerp(previous, target, clamp(deltaTicks * 0.14f,0f,0.4f));
    }

    public static float advance(float previousSeconds, float deltaTicks, float rate) {
        if (!Float.isFinite(previousSeconds)) previousSeconds = 0f;
        if (!Float.isFinite(rate)) rate = 1f;
        return previousSeconds + clamp(deltaTicks,0f,3f) / 20f * clamp(rate,0.5f,2f);
    }

    public static float crossfade(float now,float changedAt,float ticks) {
        if (!Float.isFinite(now) || !Float.isFinite(changedAt) || ticks <= 0f) return 1f;
        float t=clamp((now-changedAt)/ticks,0f,1f);
        return t*t*(3f-2f*t);
    }

    private static float lerp(float a,float b,float t) { return a+(b-a)*t; }
    private static float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }
}
