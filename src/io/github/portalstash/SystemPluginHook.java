package io.github.portalstash;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the supplied plug-in's material selector. Never hooks launcher or global View APIs. */
final class SystemPluginHook {
    static final String PLUGIN="miui.systemui.plugin";
    static final String SYSTEMUI="com.android.systemui";
    private volatile int mode;
    private final AtomicBoolean installed=new AtomicBoolean();
    private final AtomicBoolean bootstrapped=new AtomicBoolean();
    private XC_MethodHook.Unhook loaderHook;
    private ContentObserver observer;

    void start(XC_LoadPackage.LoadPackageParam p) {
        if (!(SYSTEMUI.equals(p.packageName) && SYSTEMUI.equals(p.processName))
                && !(PLUGIN.equals(p.packageName) && PLUGIN.equals(p.processName))) return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!bootstrapped.compareAndSet(false,true)) return;
                try {
                    Context c=((Context)param.args[0]).getApplicationContext();
                    if (c==null) c=(Context)param.args[0];
                    if (android.os.Build.VERSION.SDK_INT<36 || c.getPackageManager().getPackageInfo(PLUGIN,0).getLongVersionCode()!=183022200L) {
                        XposedBridge.log("PortalStash: plugin version mismatch, material hook skipped"); return;
                    }
                    final Context app=c;
                    read(app);
                    observer=new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override public void onChange(boolean self) { read(app); }
                    };
                    app.getContentResolver().registerContentObserver(Config.URI,true,observer);
                    Class<?> ready=XposedHelpers.findClassIfExists("miui.systemui.util.MiBackgroundStyle",app.getClassLoader());
                    if (ready!=null) { install(ready); return; }
                    // Plug-in uses a separate loader inside SystemUI. Watch only until exact class is found.
                    loaderHook=XposedHelpers.findAndHookMethod(ClassLoader.class,"loadClass",String.class,boolean.class,new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam event) {
                            if (!"miui.systemui.util.MiBackgroundStyle".equals(event.args[0]) || event.hasThrowable()) return;
                            if (event.getResult() instanceof Class) install((Class<?>)event.getResult());
                        }
                    });
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (loaderHook!=null) { loaderHook.unhook(); loaderHook=null; }
                    },45000L); // No permanent all-class load interception if plug-in never loads.
                } catch (Throwable e) { HookEntry.log("plugin bootstrap",e); }
            }
        });
    }
    private void read(Context c) {
        try {
            android.os.Bundle b=c.getContentResolver().call(Config.URI,"visualSettings",null,null);
            mode=b==null ? 0 : VisualMath.glassMode(b.getInt("pluginGlassMode",0));
        } catch (Throwable e) { mode=0; HookEntry.log("plugin visual settings",e); }
    }
    private void install(Class<?> style) {
        if (!installed.compareAndSet(false,true)) return;
        try {
            ClassLoader loader=style.getClassLoader();
            Class<?> bionics=XposedHelpers.findClass("miui.systemui.util.MaterialMode$Bionics",loader);
            Class<?> classic=XposedHelpers.findClass("miui.systemui.util.MaterialMode$Classic",loader);
            // Existence check, no forced device support checks or framework setting changes.
            android.view.View.class.getMethod("setMiGlass",float[].class);
            android.view.View.class.getMethod("setMiViewMaterialType",int.class);
            XposedHelpers.findAndHookMethod(style,"getMaterialMode",new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    int selected=mode;
                    if (selected==0 || param.hasThrowable() || param.getResult()==null) return;
                    try {
                        if (((Number)XposedHelpers.callMethod(param.getResult(),"getValue")).intValue()<0) return;
                        param.setResult(XposedHelpers.getStaticObjectField(selected==1 ? bionics : classic,"INSTANCE")); }
                    catch (Throwable e) { HookEntry.log("plugin selector",e); }
                }
            });
            if (loaderHook!=null) { loaderHook.unhook(); loaderHook=null; }
            XposedBridge.log("PortalStash: exact plugin material selector installed; default follows system");
        } catch (Throwable e) {
            // Stop watching after one failed exact-class attempt; do not retry arbitrary classes.
            if (loaderHook!=null) { loaderHook.unhook(); loaderHook=null; }
            HookEntry.log("plugin exact selector",e);
        }
    }
}
