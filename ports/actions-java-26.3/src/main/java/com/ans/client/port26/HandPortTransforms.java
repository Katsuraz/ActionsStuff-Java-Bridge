package com.ans.client.port26;

/**
 * First-person movement state interpolation for Minecraft 26.3.
 *
 * Keeps the original 26.2 binary out of this module; the source API adapter
 * is compiled against the new FirstPersonHandsAndItemsRenderState.
 *
 * Experimental hand-bob subset while the source custom-geometry renderer is
 * being ported. Bounds avoid camera jumps when importing invalid source state.
 */
public final class HandPortTransforms {
    private HandPortTransforms() { }

    public record Offset(float x, float y, float z) { }

    public static Offset evaluate(float partialTicks, float cameraBob,
                                  float mainHandSwapScale, float viewPitch) {
        float bob = finite(cameraBob,-15f,15f);
        float swap = finite(mainHandSwapScale,0f,1f);
        float pitch = finite(viewPitch,-90f,90f);
        float t = finite(partialTicks,0f,1f);
        float x = clamp((float)Math.sin(bob*0.32f+t*0.16f)*0.006f,-0.008f,0.008f);
        float y = clamp((float)Math.cos(bob*0.27f+t*0.11f)*0.004f
            -(1f-swap)*0.013f,-0.018f,0.008f);
        float z = clamp(pitch/90f*0.005f,-0.006f,0.006f);
        return new Offset(x,y,z);
    }

    private static float finite(float n,float low,float high) {
        return Float.isFinite(n) ? clamp(n,low,high) : 0f;
    }
    private static float clamp(float value,float low,float high) {
        return Math.max(low,Math.min(high,value));
    }
}
