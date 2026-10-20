package io.github.portalstash;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.reflect.Method;

/** OS4 View extension calls verified in the supplied plug-in. No shader or screen capture. */
final class GlassCompat {
    private static Method material, glass, blur, background, viewBlur, radius, maxSize;
    private static boolean checked, available;
    private static synchronized boolean resolve() {
        if (checked) return available; checked = true;
        try {
            material=View.class.getMethod("setMiViewMaterialType", int.class);
            glass=View.class.getMethod("setMiGlass", float[].class);
            blur=View.class.getMethod("setMiGlassBlurRadius", int.class, int.class);
            background=View.class.getMethod("setMiBackgroundBlurMode", int.class);
            viewBlur=View.class.getMethod("setMiViewBlurMode", int.class);
            radius=View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            try { maxSize=View.class.getMethod("setMiGlassSdfMaxSize", float.class,float.class); } catch (NoSuchMethodException ignored) { }
            available=true;
        } catch (ReflectiveOperationException e) { available=false; }
        return available;
    }
    static boolean apply(View v, float corner) {
        if (!resolve() || !v.isHardwareAccelerated()) return false;
        Drawable original=v.getBackground();
        try {
            // Bionics=1, Classic=0 confirmed in the supplied MaterialMode class.
            background.invoke(v,1); viewBlur.invoke(v,1); radius.invoke(v,90);
            material.invoke(v,1); blur.invoke(v,90,90);
            // 42-field native schema; conservative static light, no per-frame texture capture.
            float[] params={.67f,.16f,.09f,0,.24f,1.4f,-.02f,.3f,.6f,1,.03f,1,1,1,.1f,.2f,.3f,1,1,
                72,3.8f,80,800,1.2f,1,-.4f,.6f,-.8f,1.4f,.7f,.8f,1.15f,4,2,0,0,0,0,0,0,0,0};
            glass.invoke(v,(Object)params);
            if (maxSize != null) maxSize.invoke(v,(float)v.getWidth(),(float)v.getHeight());
            boolean dark=(v.getResources().getConfiguration().uiMode & 48)==32;
            GradientDrawable tint=new GradientDrawable(); tint.setCornerRadius(corner);
            tint.setColor(dark ? 0x74242424 : 0x58ffffff);
            tint.setStroke(Math.max(1,Math.round(v.getResources().getDisplayMetrics().density*.5f)),dark ? 0x30ffffff : 0x85ffffff);
            v.setBackground(tint); Motion.round(v,corner); return true;
        } catch (Throwable e) {
            clear(v); v.setBackground(original); HookEntry.log("native glass fallback",e); return false;
        }
    }
    static void clear(View v) {
        if (!available) return;
        try { material.invoke(v,0); background.invoke(v,0); viewBlur.invoke(v,0); radius.invoke(v,0); }
        catch (Throwable ignored) { }
    }
}
