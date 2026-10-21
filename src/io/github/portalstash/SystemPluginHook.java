package io.github.portalstash;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Version-locked plug-in selector and exact native-call observer; no global View hook. */
final class SystemPluginHook {
    static final String PLUGIN="miui.systemui.plugin",SYSTEMUI="com.android.systemui";
    private static final SystemPluginHook INSTANCE=new SystemPluginHook();
    private final AtomicBoolean started=new AtomicBoolean(),bootstrapped=new AtomicBoolean(),installed=new AtomicBoolean();
    private volatile int mode;
    private Context app;
    private XC_MethodHook.Unhook loaderHook;
    private ContentObserver observer;
    private boolean nativeSeen,disabledSeen;
    static void start(XC_LoadPackage.LoadPackageParam p) { INSTANCE.begin(p); }
    private void begin(XC_LoadPackage.LoadPackageParam p) {
        if (!(SYSTEMUI.equals(p.processName) || PLUGIN.equals(p.processName))) return;
        if (!started.compareAndSet(false,true)) return;
        // A plug-in package event may arrive after Application.attach. Handle both timings.
        Application current=null;
        try { current=(Application)XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread",null),"currentApplication"); }
        catch(Throwable ignored) { }
        if(current!=null) { bootstrap(current); return; }
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) { bootstrap((Context)param.args[0]); }
        });
    }
    private void status(String value) {
        if(app==null) return;
        try {
            Bundle b=new Bundle(); b.putString("key","pluginGlass"); b.putString("status",value);
            app.getContentResolver().call(Config.URI,"visualStatus",null,b);
        } catch(Throwable e) { HookEntry.log("plugin status",e); }
    }
    private void bootstrap(Context c) {
        if(!bootstrapped.compareAndSet(false,true)) return;
        app=c.getApplicationContext()==null ? c : c.getApplicationContext();
        try {
            if(android.os.Build.VERSION.SDK_INT<36 || app.getPackageManager().getPackageInfo(PLUGIN,0).getLongVersionCode()!=183022200L) {
                status("系统组件版本不匹配；未安装材质 Hook"); return;
            }
            read();
            observer=new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean self) { read(); }
            };
            app.getContentResolver().registerContentObserver(Config.URI,true,observer);
            Class<?> ready=XposedHelpers.findClassIfExists("miui.systemui.util.MiBackgroundStyle",app.getClassLoader());
            if(ready!=null) { install(ready); return; }
            status("已进入系统进程；等待组件独立 ClassLoader（最多45秒）");
            loaderHook=XposedHelpers.findAndHookMethod(ClassLoader.class,"loadClass",String.class,boolean.class,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam event) {
                    if("miui.systemui.util.MiBackgroundStyle".equals(event.args[0]) && !event.hasThrowable() && event.getResult() instanceof Class)
                        install((Class<?>)event.getResult());
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if(loaderHook!=null) { loaderHook.unhook(); loaderHook=null; status("45秒内未发现组件类；材质 Hook 未安装，需重新加载系统进程"); }
            },45000L);
        } catch(Throwable e) { status("组件初始化失败："+e.getClass().getSimpleName()); HookEntry.log("plugin bootstrap",e); }
    }
    private void read() {
        try {
            Bundle b=app.getContentResolver().call(Config.URI,"visualSettings",null,null);
            mode=b==null ? 0 : VisualMath.glassMode(b.getInt("pluginGlassMode",0));
            nativeSeen=false; disabledSeen=false;
            if(installed.get()) status(mode==0 ? "Hook已安装；跟随系统，不覆盖材质" : "Hook已安装；等待组件重新应用材质");
        } catch(Throwable e) { mode=0; status("偏好读取失败；已退回跟随系统"); HookEntry.log("plugin settings",e); }
    }
    private void install(Class<?> style) {
        if(!installed.compareAndSet(false,true)) return;
        try {
            ClassLoader loader=style.getClassLoader();
            Class<?> bionics=XposedHelpers.findClass("miui.systemui.util.MaterialMode$Bionics",loader);
            Class<?> classic=XposedHelpers.findClass("miui.systemui.util.MaterialMode$Classic",loader);
            android.view.View.class.getMethod("setMiGlass",float[].class);
            android.view.View.class.getMethod("setMiViewMaterialType",int.class);
            // HyperLight observes/changes this exact compat call too. Here only observe it:
            // vendor-provided tokens remain unmodified; no copied third-party renderer.
            XposedHelpers.findAndHookMethod(style,"setMiGlassCompat",android.view.View.class,float[].class,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if(mode==1 && !nativeSeen && !p.hasThrowable()) {
                        nativeSeen=true; status("已命中组件 setMiGlassCompat 调用链；不等于已验证画面生效");
                    }
                }
            });
            XposedHelpers.findAndHookMethod(style,"getMaterialMode",new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    int selected=mode;
                    if(selected==0 || p.hasThrowable() || p.getResult()==null) return;
                    try {
                        if(((Number)XposedHelpers.callMethod(p.getResult(),"getValue")).intValue()<0) {
                            if(!disabledSeen) { disabledSeen=true; status("组件返回 Disabled；保留系统禁用状态，不强制开启"); }
                            return;
                        }
                        p.setResult(XposedHelpers.getStaticObjectField(selected==1 ? bionics : classic,"INSTANCE"));
                    } catch(Throwable e) { HookEntry.log("plugin selector",e); }
                }
            });
            status(mode==0 ? "Hook已安装；跟随系统，不覆盖材质" : "Hook已安装；等待组件重新应用材质");
        } catch(Throwable e) { status("精确材质 Hook 安装失败："+e.getClass().getSimpleName()); HookEntry.log("plugin exact selector",e); }
        finally { if(loaderHook!=null) { loaderHook.unhook(); loaderHook=null; } }
    }
}
