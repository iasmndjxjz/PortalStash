package io.github.portalstash;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Vendor rendering on owned overlays only. No capture, custom shader or global View hook. */
final class GlassCompat {
    private static Method material,glass,blur,background,viewBlur,radius,clip,maxSize;
    private static boolean checked,available;
    private static String failure;
    private static final WeakHashMap<View,Drawable> originals=new WeakHashMap<>();
    private static synchronized boolean resolve() {
        if (checked) return available;
        checked=true;
        try {
            material=View.class.getMethod("setMiViewMaterialType",int.class);
            glass=View.class.getMethod("setMiGlass",float[].class);
            blur=View.class.getMethod("setMiGlassBlurRadius",int.class,int.class);
            background=View.class.getMethod("setMiBackgroundBlurMode",int.class);
            viewBlur=View.class.getMethod("setMiViewBlurMode",int.class);
            radius=View.class.getMethod("setMiBackgroundBlurRadius",int.class);
            try { clip=View.class.getMethod("setMiGlassClip",float.class,float.class,float.class,float.class); } catch(NoSuchMethodException ignored) { }
            try { maxSize=View.class.getMethod("setMiGlassSdfMaxSize",float.class,float.class); } catch(NoSuchMethodException ignored) { }
            available=true;
        } catch(ReflectiveOperationException e) { failure=e.getClass().getSimpleName(); }
        return available;
    }
    private static void invoke(Method method,View view,Object... args) throws Exception {
        Object result=method.invoke(view,args);
        if (Boolean.FALSE.equals(result)) throw new IllegalStateException("Native API returned false");
    }
    static String applyDetailed(View v,float corner) {
        if (!resolve()) return "接口缺失："+failure+"；已回退普通背景";
        if (!v.isHardwareAccelerated()) return "窗口未硬件加速；已回退普通背景";
        if (v.getWidth()<=0 || v.getHeight()<=0) return "视图未完成测量；未调用材质";
        Drawable original=v.getBackground(); originals.put(v,original);
        try {
            invoke(background,v,1); invoke(viewBlur,v,1); invoke(radius,v,90);
            invoke(material,v,1);
            if (clip!=null) invoke(clip,v,0f,0f,(float)v.getWidth(),(float)v.getHeight());
            if (maxSize!=null) invoke(maxSize,v,(float)v.getWidth(),(float)v.getHeight());
            invoke(blur,v,110,110);
            float[] params={.67f,.16f,.09f,0,.24f,1.4f,-.02f,.3f,.6f,1,.03f,1,1,1,.1f,.2f,.3f,1,1,
                    72,3.8f,80,800,1.2f,1,-.4f,.6f,-.8f,1.4f,.7f,.8f,1.15f,4,2,0,0,0,0,0,0,0,0};
            invoke(glass,v,(Object)params);
            GradientDrawable tint=new GradientDrawable(); tint.setCornerRadius(corner);
            tint.setColor(0x08ffffff); tint.setStroke(Math.max(1,Math.round(v.getResources().getDisplayMetrics().density*.5f)),0x38ffffff);
            v.setBackground(tint); Motion.round(v,corner); v.invalidate();
            return "已调用原生材质、裁切与双模糊接口；视觉效果待你确认";
        } catch(Throwable e) {
            clear(v); v.setBackground(original); HookEntry.log("native glass fallback",e);
            Throwable cause=e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            return "材质调用失败："+(cause==null ? "unknown" : cause.getClass().getSimpleName())+"；已回退普通背景";
        }
    }
    static void clear(View v) {
        if (!originals.containsKey(v)) return;
        Drawable original=originals.remove(v);
        try {
            material.invoke(v,0); glass.invoke(v,(Object)new float[42]); blur.invoke(v,0,0);
            if(clip!=null) clip.invoke(v,0f,0f,0f,0f);
            background.invoke(v,0); viewBlur.invoke(v,0); radius.invoke(v,0);
        } catch(Throwable ignored) { }
        v.setBackground(original);
    }
}
