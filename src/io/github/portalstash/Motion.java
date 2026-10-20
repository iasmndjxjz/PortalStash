package io.github.portalstash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import java.util.WeakHashMap;

/** Independent implementation; spring damping/response follows values inspected in OS4 plug-in.
 * Does not load vendor APK classes into the portal or claim to use its complete Folme engine. */
final class Motion {
    private static final WeakHashMap<View, ValueAnimator> running = new WeakHashMap<>();
    static void round(View view, float radius) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) {
                out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.min(radius, Math.min(v.getWidth(), v.getHeight()) / 2f));
            }
        });
        view.setClipToOutline(true); view.invalidateOutline();
    }
    static boolean enabled(boolean choice) {
        return choice && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
    }
    static void cancel(View v) {
        ValueAnimator a = running.remove(v); if (a != null) a.cancel();
        v.animate().withEndAction(null).cancel();
    }
    static void cancelTree(View view) {
        cancel(view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) cancelTree(group.getChildAt(i));
        }
    }
    interface Frame { void apply(float position, float progress); }
    private static void run(View v, boolean enabled, int millis, float damping, float response, Frame frame, Runnable end) {
        cancel(v);
        if (!enabled(enabled)) { frame.apply(1, 1); if (end != null) end.run(); return; }
        ValueAnimator a = ValueAnimator.ofFloat(0, 1); a.setDuration(millis); a.setInterpolator(new LinearInterpolator());
        running.put(v, a); final boolean[] cancelled = {false};
        a.addUpdateListener(anim -> {
            float progress = (Float)anim.getAnimatedValue();
            frame.apply(VisualMath.spring(progress * millis / 1000f, damping, response), progress);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(Animator a) { cancelled[0] = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (running.get(v) == a) running.remove(v);
                if (!cancelled[0]) { frame.apply(1, 1); if (end != null) end.run(); }
            }
        }); frame.apply(0,0); a.start();
    }
    static void enter(View v, boolean expanded, boolean choice, boolean left) {
        v.setPivotX(left ? 0 : v.getWidth()); v.setPivotY(Math.min(v.getHeight()/2f, 28 * v.getResources().getDisplayMetrics().density));
        float distance = 12 * v.getResources().getDisplayMetrics().density * (left ? -1 : 1);
        run(v, choice, expanded ? 460 : 360, expanded ? .82f : .9f, expanded ? .4f : .3f, (p,t) -> {
            float scale = .9f + .1f*p; v.setScaleX(scale); v.setScaleY(scale);
            v.setTranslationX(distance*(1-p)); v.setAlpha(Math.min(1, t*5));
        }, null);
    }
    static void exit(View v, boolean choice, Runnable end) {
        cancel(v);
        if (!enabled(choice)) { end.run(); return; }
        v.animate().alpha(0).scaleX(.94f).scaleY(.94f).setDuration(160)
                .setInterpolator(new PathInterpolator(.4f,0,.2f,1)).withEndAction(end).start();
    }
    static void pulse(View v, boolean choice) {
        run(v, choice, 430, .82f, .4f, (p,t) -> { float scale=.94f+.06f*p; v.setScaleX(scale); v.setScaleY(scale); }, null);
    }
    static void settle(View v, boolean choice) {
        float start=v.getTranslationY(), alpha=v.getAlpha(), scale=v.getScaleX();
        run(v, choice, 400, .9f, .3f, (p,t) -> {
            v.setTranslationY(start*(1-p)); v.setAlpha(Math.min(1,alpha+(1-alpha)*t*3));
            v.setScaleX(scale+(1-scale)*p); v.setScaleY(scale+(1-scale)*p);
        }, null);
    }
    static void remove(View v, boolean choice, Runnable end) {
        cancel(v);
        if (!enabled(choice)) { end.run(); return; }
        v.animate().translationY(-190*v.getResources().getDisplayMetrics().density).alpha(0).scaleX(.92f).scaleY(.92f)
                .setDuration(190).setInterpolator(new PathInterpolator(.2f,0,.2f,1)).withEndAction(end).start();
    }
}
