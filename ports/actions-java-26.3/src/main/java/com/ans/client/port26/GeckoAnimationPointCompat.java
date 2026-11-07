package com.ans.client.port26;

import com.geckolib.animation.state.AnimationPoint;

/**
 * Source-side GeckoLib 5.5.7 compatibility for the former
 * AnimationPoint.createNext(double) call in the 26.2 Actions & Java
 * EntityRuntime animation player.
 *
 * GeckoLib 5.5.7 replaces this with createNext(double, EasingType).
 * Forward the current easing override to preserve the old call's
 * existing animation easing, rather than silently clearing it.
 *
 * NOT a binary patch: EntityRuntime needs to be recompiled to call this
 * method, or its original bytecode must be rewritten privately.
 */
public final class GeckoAnimationPointCompat {
    private GeckoAnimationPointCompat() {}

    public static AnimationPoint createNext(AnimationPoint previous, double animationTime) {
        if (previous == null)
            throw new IllegalArgumentException("previous animation point cannot be null");

        return previous.createNext(animationTime, previous.easingOverride());
    }
}
