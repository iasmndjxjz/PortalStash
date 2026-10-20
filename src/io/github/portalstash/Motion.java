package io.github.portalstash;

import android.animation.TimeInterpolator;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;

/** Mirrors the portal's own FloatViewAnimUtil: straight edge slide, no overshoot.
 * In: PathInterpolator(0.4, 0, 0.2, 1) 350ms; out: AccelerateDecelerate 200ms. */
final class Motion {
    private static final TimeInterpolator IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private static final TimeInterpolator OUT = new AccelerateDecelerateInterpolator();
    static void round(View view, float radius) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) {
                out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.min(radius, Math.min(v.getWidth(), v.getHeight()) / 2f));
            }
        });
        view.setClipToOutline(true); view.invalidateOutline();
    }
    static boolean enabled(boolean choice) {
        return choice && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled());
    }
    static void cancel(View v) { v.animate().setListener(null).withEndAction(null).cancel(); }
    static void cancelTree(View view) {
        cancel(view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) cancelTree(group.getChildAt(i));
        }
    }
    private static float edge(View v, boolean left) {
        float d = v.getWidth() + 8 * v.getResources().getDisplayMetrics().density;
        return left ? -d : d;
    }
    static void enter(View v, boolean expanded, boolean choice, boolean left) {
        cancel(v);
        v.setScaleX(1); v.setScaleY(1); v.setAlpha(1);
        if (!enabled(choice)) { v.setTranslationX(0); return; }
        v.setTranslationX(edge(v, left));
        v.animate().translationX(0).setInterpolator(IN).setDuration(350).start();
    }
    static void exit(View v, boolean choice, boolean left, Runnable end) {
        cancel(v);
        if (!enabled(choice)) { end.run(); return; }
        v.animate().translationX(edge(v, left)).setInterpolator(OUT).setDuration(200).withEndAction(end).start();
    }
    static void pulse(View v, boolean choice) {
        cancel(v);
        if (!enabled(choice)) return;
        v.setScaleX(.96f); v.setScaleY(.96f);
        v.animate().scaleX(1).scaleY(1).setInterpolator(IN).setDuration(200).start();
    }
    static void settle(View v, boolean choice) {
        cancel(v);
        if (!enabled(choice)) { v.setTranslationY(0); v.setAlpha(1); return; }
        v.animate().translationY(0).alpha(1).setInterpolator(IN).setDuration(200).start();
    }
    static void remove(View v, boolean choice, Runnable end) {
        cancel(v);
        if (!enabled(choice)) { end.run(); return; }
        v.animate().translationY(-190*v.getResources().getDisplayMetrics().density).alpha(0)
                .setInterpolator(OUT).setDuration(200).withEndAction(end).start();
    }
}
