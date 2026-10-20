package io.github.portalstash;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    public static final String HOST = "com.miui.contentextension";
    private static final String SERVICE = HOST + ".services.TextContentExtensionService";
    private static final String FLOAT = HOST + ".text.floatview.TaplusMainFloatView";
    private StashController controller;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (SystemPluginHook.SYSTEMUI.equals(p.packageName) || SystemPluginHook.PLUGIN.equals(p.packageName)) {
            new SystemPluginHook().start(p); return;
        }
        if (!HOST.equals(p.packageName) || !HOST.equals(p.processName)) return;
        XposedHelpers.findAndHookMethod(HOST + ".App", p.classLoader, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Context c = (Context) param.thisObject;
                    long version = c.getPackageManager().getPackageInfo(HOST, 0).getLongVersionCode();
                    if (version != 50015L) {
                        XposedBridge.log("PortalStash: unsupported portal version " + version + "; no hooks installed");
                        return;
                    }
                    if (controller != null) return;
                    ClassLoader loader = c.getClassLoader();
                    Class<?> service = XposedHelpers.findClass(SERVICE, loader);
                    Class<?> floating = XposedHelpers.findClass(FLOAT, loader);
                    // Verify exact methods before changing anything. Do not guess obfuscated names.
                    floating.getDeclaredMethod("showMainFloat", boolean.class);
                    floating.getDeclaredMethod("onDestroy");
                    service.getDeclaredMethod("scheduleStopSelfJob");
                    service.getDeclaredMethod("cancelTask");
                    service.getDeclaredField("sContent");
                    service.getDeclaredMethod("getBitmap");
                    controller = new StashController(c, service);
                    install(service, floating);
                    Class<?> splash = XposedHelpers.findClass(HOST + ".text.floatview.TaplusSplashFloatView", loader);
                    XposedHelpers.findAndHookMethod(splash, "initRecognitionCards", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try { controller.attachOriginalEntry(p.thisObject); }
                            catch (Throwable e) { log("native entry", e); }
                        }
                    });
                    XposedHelpers.findAndHookMethod(splash, "onDestroy", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            controller.endOffer(p.thisObject);
                        }
                    });
                    Bundle report = new Bundle();
                    report.putString("status", "传送门 5.1.5 Hook 已装载（不等于功能已实测）");
                    c.getContentResolver().call(android.net.Uri.parse("content://" + StashProvider.AUTH),
                            "status", null, report);
                    XposedBridge.log("PortalStash: hooks installed for portal 5.1.5");
                } catch (Throwable e) { log("initialization", e); }
            }
        });
    }

    private void install(Class<?> service, Class<?> floating) {
        XposedHelpers.findAndHookMethod(floating, "showMainFloat", boolean.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View root = (View) XposedHelpers.getObjectField(p.thisObject, "mMainView");
                    Service host = (Service) XposedHelpers.getObjectField(p.thisObject, "mService");
                    controller.offer(p.thisObject, root, host);
                } catch (Throwable e) { log("offer", e); }
            }
        });
        XposedHelpers.findAndHookMethod(floating, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try { controller.endOffer(p.thisObject); } catch (Throwable e) { log("end offer", e); }
            }
        });
        // Original service schedules its own stop after cancelTask(). Keep it alive only
        // while user-owned pending items exist; clear/expiry restores the normal stop job.
        XposedHelpers.findAndHookMethod(service, "scheduleStopSelfJob", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (controller.hasItems()) p.setResult(null);
            }
        });
        XposedHelpers.findAndHookMethod(service, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try { controller.shutdown(); } catch (Throwable e) { log("shutdown", e); }
            }
        });
    }

    static void log(String stage, Throwable e) {
        // Never log recognized content, file paths, URIs or exception messages containing them.
        XposedBridge.log("PortalStash: " + stage + " failed (" + e.getClass().getSimpleName() + ")");
    }
}
