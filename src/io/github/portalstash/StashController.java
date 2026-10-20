package io.github.portalstash;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XposedHelpers;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** All window/item state is confined to the main looper. No notification or clipboard monitoring. */
final class StashController {
    private static final Uri CACHE = Uri.parse("content://" + StashProvider.AUTH + "/");
    private Config config = new Config();
    private MiuixStyle ui;
    private android.database.ContentObserver configObserver;
    private boolean restoreReady;
    private boolean splashOffer;
    private WeakReference<LinearLayout> nativeEntry = new WeakReference<>(null);
    private WeakReference<Object> entryOwner = new WeakReference<>(null);
    private boolean dragTouch;
    private Runnable dragRecovery;
    private final Context context;
    private final Context windowContext;
    private final WindowManager wm;
    private final Class<?> serviceClass;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ArrayList<Item> items = new ArrayList<>();
    private final SharedPreferences prefs;
    private WeakReference<Service> service = new WeakReference<>(null);
    private WeakReference<Object> offerOwner = new WeakReference<>(null);
    private View window;
    private WindowManager.LayoutParams params;
    private boolean panel;
    private boolean stopped;
    private boolean receiverRegistered;
    private boolean dragging;
    private int generation;
    private int y;

    private static final class Item {
        String id;
        String text;
        Uri uri;
        long time;
        ClipData clip(Context c) {
            return uri == null ? ClipData.newPlainText("暂存文字", text)
                    : ClipData.newUri(c.getContentResolver(), "暂存图片", uri);
        }
    }

    StashController(Context c, Class<?> clazz) {
        context = c.getApplicationContext();
        serviceClass = clazz;
        android.hardware.display.DisplayManager displays = (android.hardware.display.DisplayManager)
                context.getSystemService(Context.DISPLAY_SERVICE);
        android.view.Display display = displays.getDisplay(android.view.Display.DEFAULT_DISPLAY);
        Context displayContext = display == null ? context : context.createDisplayContext(display);
        windowContext = android.os.Build.VERSION.SDK_INT >= 30
                ? displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) : displayContext;
        wm = (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
        prefs = context.getSharedPreferences("portal_stash_v1", Context.MODE_PRIVATE);
        y = prefs.getInt("y", dp(180));
        refreshConfig();
        restore();
        restoreReady = true;
        configObserver = new android.database.ContentObserver(main) {
            @Override public void onChange(boolean selfChange) {
                refreshConfig();
                LinearLayout entry = nativeEntry.get();
                if (entry != null) entry.setVisibility(config.enabled ? View.VISIBLE : View.GONE);
                for (int i = items.size() - 1; i >= 0; i--) {
                    if (System.currentTimeMillis() - items.get(i).time >= config.ttl()) removeItem(items.get(i), false);
                }
                save();
                if (!dragging) show(panel);
            }
        };
        context.getContentResolver().registerContentObserver(Config.URI, true, configObserver);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_USER_BACKGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(screen, f);
        receiverRegistered = true;
        main.postDelayed(expire, 60000L);
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_USER_PRESENT.equals(i.getAction())) {
                if (!items.isEmpty()) show(false);
            } else {
                offerOwner.clear();
                dragging = false;
                removeWindow();
                if (Intent.ACTION_USER_BACKGROUND.equals(i.getAction())) clear();
            }
        }
    };
    private final Runnable expire = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            for (int i = items.size() - 1; i >= 0; i--) {
                if (System.currentTimeMillis() - items.get(i).time >= config.ttl()) removeItem(items.get(i), false);
            }
            save();
            if (window != null && !dragging) show(panel);
            main.postDelayed(this, 60000L);
        }
    };

    boolean hasItems() { return !items.isEmpty() || busy.get(); }

    void offer(Object owner, View root, Service host) {
        main.post(() -> {
            if (stopped || root == null) return;
            service = new WeakReference<>(host);
            offerOwner = new WeakReference<>(owner);
            splashOffer = false;
            root.post(() -> {
                if (offerOwner.get() != owner || stopped || !config.enabled) return;
                try {
                    LinearLayout recognition = (LinearLayout) XposedHelpers.getObjectField(owner, "mRecognitionCard");
                    TextView template = (TextView) XposedHelpers.getObjectField(recognition, "mTextSwitcher");
                    LinearLayout bar = (LinearLayout) template.getParent();
                    if (bar.findViewWithTag("portal_stash_native_action") != null) return;
                    TextView save = NativePortalUi.text(template.getContext(), "暂存");
                    save.setTag("portal_stash_native_action");
                    save.setGravity(Gravity.CENTER);
                    android.graphics.drawable.Drawable bg = template.getBackground();
                    if (bg != null && bg.getConstantState() != null) save.setBackground(bg.getConstantState().newDrawable(template.getResources()).mutate());
                    save.setOnClickListener(v -> capture());
                    bar.addView(save, new LinearLayout.LayoutParams(0, template.getLayoutParams().height, 1));
                    for (int n = 0; n < bar.getChildCount(); n++) {
                        View child = bar.getChildAt(n);
                        if (child instanceof TextView) {
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(child.getLayoutParams());
                            lp.width = 0; lp.weight = 1; child.setLayoutParams(lp);
                        }
                    }
                    removeWindow();
                } catch (Throwable e) { HookEntry.log("native expanded action", e); }
            });
        });
    }
    void attachOriginalEntry(Object owner) {
        if (stopped || !config.enabled) return;
        try {
            View original = (View) XposedHelpers.getObjectField(owner, "mRecognitionCard");
            if (!(original.getParent() instanceof android.widget.RelativeLayout)) return;
            android.widget.RelativeLayout parent = (android.widget.RelativeLayout) original.getParent();
            if (parent.findViewWithTag("portal_stash_native_entry") != null) return;
            Service host = (Service) XposedHelpers.getObjectField(owner, "mService");
            service = new WeakReference<>(host); offerOwner = new WeakReference<>(owner);
            entryOwner = new WeakReference<>(owner); splashOffer = true;
            LinearLayout extra = NativePortalUi.entry(original.getContext(), "暂存", "ic_storage", this::capture);
            extra.setTag("portal_stash_native_entry");
            android.widget.RelativeLayout.LayoutParams lp = new android.widget.RelativeLayout.LayoutParams(original.getLayoutParams());
            int secondId = NativePortalUi.id(original.getContext(), "id", "recognition_card_2");
            View second = parent.findViewById(secondId);
            int anchor = second != null && second.getVisibility() != View.GONE ? secondId : original.getId();
            lp.addRule(android.widget.RelativeLayout.BELOW, anchor);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
            lp.topMargin = dp(8); extra.setAlpha(1); extra.setVisibility(View.VISIBLE);
            parent.addView(extra, lp); nativeEntry = new WeakReference<>(extra);
            View recommend = parent.findViewById(NativePortalUi.id(original.getContext(), "id", "recommend_group"));
            if (recommend != null && recommend.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams r = (android.widget.RelativeLayout.LayoutParams) recommend.getLayoutParams();
                r.addRule(android.widget.RelativeLayout.BELOW, extra.getId()); recommend.setLayoutParams(r);
            }
            removeWindow();
        } catch (Throwable e) { HookEntry.log("native short entry", e); }
    }

    void endOffer(Object owner) {
        if (entryOwner.get() == owner) { nativeEntry.clear(); entryOwner.clear(); }
        if (offerOwner.get() == owner) {
            offerOwner.clear();
            main.post(() -> show(false));
        }
    }

    private void refreshConfig() {
        try {
            Config next = Config.remote(context);
            long seen = prefs.getLong("clearToken", 0);
            config = next;
            if (restoreReady && next.clearToken > seen) clear();
            if (restoreReady) prefs.edit().putLong("clearToken", next.clearToken).apply();
        } catch (RuntimeException e) { HookEntry.log("settings", e); }
        ui = new MiuixStyle(windowContext, config);
    }

    private boolean locked() {
        KeyguardManager k = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return k != null && k.isKeyguardLocked();
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(context, s, Toast.LENGTH_SHORT).show(); }
    private TextView label(String value, int size) {
        TextView t = ui.text(value, size, ui.text);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        return t;
    }
    private TextView button(String title, Runnable action) {
        return ui.action(title, () -> {
            try { action.run(); } catch (Throwable e) { HookEntry.log("action", e); toast("操作失败，请检查模块日志"); }
        });
    }
    private void removeWindow() {
        View v = window; window = null;
        if (v != null) { Motion.cancelTree(v); GlassCompat.clear(v);
            try { wm.removeView(v); } catch (RuntimeException e) { HookEntry.log("remove window", e); }
        }
    }

    private void transition(boolean expanded) {
        View outgoing=window;
        if (outgoing==null || !Motion.enabled(config.animations)) { show(expanded); return; }
        outgoing.setEnabled(false);
        Motion.exit(outgoing, config.animations, "left".equals(config.side), () -> {
            if (window==outgoing && !stopped && !dragging) show(expanded);
        });
    }

    private void show(boolean expanded) {
        if (stopped || dragging) return;
        removeWindow();
        if (!config.enabled || locked() || items.isEmpty()) return;
        LinearLayout existing = nativeEntry.get();
        if (!expanded && existing != null && existing.isAttachedToWindow()) return;
        if (!Settings.canDrawOverlays(context)) return;
        panel = expanded;
        // Every surface is inflated/constructed using the original app resources, not module styling.
        LinearLayout box = new LinearLayout(windowContext);
        box.setOrientation(LinearLayout.VERTICAL);
        if (!expanded) {
            LinearLayout capsule = NativePortalUi.entry(windowContext, "暂存栏", "icon_recognition_image_save", () -> openStored());
            box.addView(capsule, new LinearLayout.LayoutParams(-1, NativePortalUi.dim(windowContext, "shrink_card_height")));
        } else {
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(windowContext.getResources().getColor(NativePortalUi.id(windowContext,"color","color_card_bg"),windowContext.getTheme()));
            surface.setCornerRadius(dp(config.corner)); box.setBackground(surface);
            Motion.round(box,dp(config.corner));
            box.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout heading = new LinearLayout(windowContext); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = NativePortalUi.text(windowContext, "暂存栏  " + items.size());
            title.setPadding(dp(8), dp(8), dp(8), dp(8));
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = nativeAction("收起", () -> transition(false)); heading.addView(close);
            box.addView(heading);
            TextView hint = NativePortalUi.text(windowContext, "长按拖出 · 上滑移除");
            hint.setAlpha(0.5f); hint.setTextSize(11); hint.setPadding(dp(8), 0, dp(8), dp(10)); box.addView(hint);
            // Horizontal scroller reserves vertical gestures exclusively for swipe-up removal.
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(windowContext);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout list = new LinearLayout(windowContext);
            for (Item item : new ArrayList<>(items)) {
                LinearLayout tile = new LinearLayout(windowContext); tile.setOrientation(LinearLayout.VERTICAL);
                tile.setBackground(NativePortalUi.drawable(windowContext, "card_bg_recognition_image"));
                Motion.round(tile,dp(Math.max(12,config.corner-6))); tile.setPadding(dp(8), dp(8), dp(8), dp(5));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), -2); lp.setMargins(dp(4), 0, dp(4), 0);
                View content;
                if (item.uri == null) {
                    TextView text = NativePortalUi.text(windowContext, item.text);
                    text.setTextSize(14); text.setMaxLines(6); text.setGravity(Gravity.CENTER_VERTICAL);
                    text.setPadding(dp(8), dp(6), dp(8), dp(6)); content = text;
                } else {
                    ImageView image = new ImageView(windowContext); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    content = image; loadThumbnail(image, item.uri);
                }
                Motion.round(content,dp(Math.max(8,config.corner-12)));
                tile.addView(content, new LinearLayout.LayoutParams(-1, dp(150)));
                bindItemGestures(content, tile, item);
                LinearLayout actions = new LinearLayout(windowContext);
                actions.addView(nativeAction("分享", () -> share(item)));
                if (item.uri == null) actions.addView(nativeAction("复制", () -> {
                    ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(item.clip(context)); toast("已复制");
                }));
                TextView remove = nativeAction("移除", () -> Motion.remove(tile,config.animations,() -> {
                    if (items.contains(item)) removeItem(item,true); show(!items.isEmpty());
                }));
                actions.addView(remove); tile.addView(actions); list.addView(tile, lp);
            }
            scroll.addView(list); box.addView(scroll);
        }
        box.setOnDragListener((v, e) -> {
            if (!(e.getLocalState() instanceof Item)) return false;
            if (e.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                dragging = false; dragTouch = false;
                if (dragRecovery != null) main.removeCallbacks(dragRecovery);
                main.post(() -> show(false));
            }
            return true;
        });
        int width = expanded ? Math.min(dp(310), context.getResources().getDisplayMetrics().widthPixels - dp(24))
                : NativePortalUi.dim(windowContext, "shrink_card_width");
        params = new WindowManager.LayoutParams(width, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | ("left".equals(config.side) ? Gravity.LEFT : Gravity.RIGHT);
        int maxY = Math.max(dp(48), context.getResources().getDisplayMetrics().heightPixels - dp(expanded ? 350 : 90));
        params.y = Math.max(dp(48), Math.min(y, maxY)); params.x = dp(8); params.setTitle("PortalStashNative");
        try {
            wm.addView(box, params); window = box;
            box.post(() -> {
                if (window != box || stopped) return;
                if (expanded && config.stashGlass && !GlassCompat.apply(box,dp(config.corner)))
                    toast("原生玻璃暂不可用，已保留普通背景");
                Motion.enter(box,expanded,config.animations,"left".equals(config.side));
            });
        }
        catch (RuntimeException e) { HookEntry.log("native stash window", e); }
    }

    private TextView nativeAction(String label, Runnable action) {
        TextView text = NativePortalUi.text(windowContext, label);
        text.setTextSize(12); text.setGravity(Gravity.CENTER); text.setMinHeight(dp(42));
        text.setPadding(dp(9), dp(8), dp(9), dp(8)); text.setOnClickListener(v -> action.run()); return text;
    }
    private void openStored() {
        Object current = offerOwner.get();
        offerOwner.clear();
        if (current != null) try { XposedHelpers.callMethod(XposedHelpers.getObjectField(current, "mService"), "cancelTask"); }
        catch (Throwable e) { HookEntry.log("close recognition", e); }
        transition(true);
    }
    private void loadThumbnail(ImageView image, Uri uri) {
        if (io.isShutdown()) return;
        io.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.getContentResolver(), uri),
                        (decoder, info, src) -> {
                            float factor = Math.min(1f, 640f / Math.max(info.getSize().getWidth(), info.getSize().getHeight()));
                            decoder.setTargetSize(Math.max(1, (int)(info.getSize().getWidth() * factor)), Math.max(1, (int)(info.getSize().getHeight() * factor)));
                            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
            } catch (Exception e) { HookEntry.log("thumbnail", e); }
            Bitmap ready = bitmap;
            main.post(() -> { if (image.isAttachedToWindow()) image.setImageBitmap(ready); else if (ready != null) ready.recycle(); });
        });
    }
    private void bindItemGestures(View content, View tile, Item item) {
        content.setContentDescription(item.uri == null ? "暂存文字；长按拖出，上滑移除" : "暂存图片；长按拖出，上滑移除");
        content.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; boolean swipe, moved; Runnable hold;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        Motion.cancel(tile);
                        downX = e.getRawX(); downY = e.getRawY(); swipe = false; moved = false; dragTouch = false;
                        hold = () -> { if (!moved && !swipe && view.isAttachedToWindow()) { dragTouch = true; startDrag(view, item); } };
                        main.postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (dragTouch) return true;
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(9) || Math.abs(dy) > dp(9)) { moved = true; main.removeCallbacks(hold); }
                        if (dy < -dp(10) && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                            swipe = true; view.getParent().requestDisallowInterceptTouchEvent(true);
                            tile.setTranslationY(Math.max(-dp(170), dy)); tile.setAlpha(Math.max(0.2f, 1f + dy / dp(210)));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        main.removeCallbacks(hold);
                        if (swipe && e.getRawY() - downY < -dp(60)) {
                            Motion.remove(tile,config.animations,() -> {
                                if (items.contains(item)) removeItem(item,true); show(!items.isEmpty());
                            });
                        } else if (!dragTouch) Motion.settle(tile,config.animations);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (hold != null) main.removeCallbacks(hold);
                        if (!dragTouch) Motion.settle(tile,config.animations);
                        return true;
                    default: return true;
                }
            }
        });
    }

    private void capture() {
        if (busy.get()) { toast("正在保存，请稍候"); return; }
        Object owner = offerOwner.get();
        if (owner == null) { show(true); return; }
        if (items.size() >= config.limit) { toast("暂存已满，请先删除部分内容"); show(true); return; }
        try {
            Object card = XposedHelpers.getObjectField(owner, "mRecognitionCard");
            boolean textMode = splashOffer ? (Boolean) XposedHelpers.callStaticMethod(serviceClass, "isTextMode")
                    : (Boolean) XposedHelpers.callMethod(card, "isCurrentInTextMode");
            if (textMode) {
                String text = null;
                try {
                    Object adapter = XposedHelpers.getObjectField(card, "mRecognitionAdapter");
                    Object textCard = XposedHelpers.callMethod(adapter, "getItem", 0);
                    Object segments = XposedHelpers.getObjectField(textCard, "mSegmentAdapter");
                    text = (String) XposedHelpers.callMethod(segments, "getSelectedWords");
                } catch (Throwable ignored) { }
                if (text == null || text.trim().isEmpty()) text = (String) XposedHelpers.getStaticObjectField(serviceClass, "sContent");
                if (text == null || text.trim().isEmpty()) { toast("还没有识别出文字，请选择文字后再试"); return; }
                if (text.length() > 100000) { toast("文字过长，请分段暂存"); return; }
                addText(text); finishCapture(owner);
            } else {
                Bitmap source = (Bitmap) XposedHelpers.callStaticMethod(serviceClass, "getBitmap");
                if (source == null || source.isRecycled()) { toast("没有可用图片，请在传送门切到图片页重试"); return; }
                if ((long)source.getWidth() * source.getHeight() > 12000000L) {
                    toast("图片超过1200万像素，请先裁剪后暂存"); return;
                }
                // Portal cancelTask recycles sBitmap. Own a copy before the worker or dismissal.
                Bitmap owned = source.copy(Bitmap.Config.ARGB_8888, false);
                if (owned == null) { toast("内存不足，未暂存"); return; }
                busy.set(true);
                int epoch = generation;
                toast("正在暂存图片…");
                io.execute(() -> {
                    Uri created = null;
                    try {
                        created = context.getContentResolver().insert(CACHE, new ContentValues());
                        if (created == null) throw new IllegalStateException("Cache unavailable");
                        try (OutputStream out = context.getContentResolver().openOutputStream(created, "wt")) {
                            if (out == null || !owned.compress(Bitmap.CompressFormat.PNG, 100, out))
                                throw new IllegalStateException("PNG write failed");
                        }
                        Uri ready = created;
                        main.post(() -> {
                            busy.set(false);
                            if (stopped || epoch != generation) { deleteCache(ready); return; }
                            Item item = new Item(); item.id = UUID.randomUUID().toString(); item.uri = ready;
                            item.time = System.currentTimeMillis(); items.add(item); save();
                            finishCapture(owner);
                        });
                    } catch (Throwable e) {
                        if (created != null) deleteCache(created);
                        HookEntry.log("image cache", e);
                        main.post(() -> { busy.set(false); toast("图片暂存失败；请确认模块已安装且未被冻结"); resumeStopJob(); });
                    } finally { owned.recycle(); }
                });
            }
        } catch (Throwable e) { HookEntry.log("capture", e); toast("暂存失败；当前页面可能尚未识别完成"); }
    }
    private void addText(String text) {
        Item item = new Item(); item.id = UUID.randomUUID().toString(); item.text = text;
        item.time = System.currentTimeMillis(); items.add(item); save();
    }
    private void finishCapture(Object owner) {
        try {
            Service host = service.get();
            if (host != null) XposedHelpers.callMethod(host, "cancelStopSelfJob");
            LinearLayout entry = nativeEntry.get();
            if (splashOffer && entryOwner.get() == owner && entry != null && entry.isAttachedToWindow()) {
                NativePortalUi.update(entry, "暂存栏", "icon_recognition_image_save");
                entry.setOnClickListener(v -> openStored());
                Motion.pulse(entry,config.animations);
                toast("已暂存，点击暂存栏查看");
                return;
            }
            if (offerOwner.get() == owner) {
                offerOwner.clear();
                Object s = XposedHelpers.getObjectField(owner, "mService");
                XposedHelpers.callMethod(s, "cancelTask");
            }
        } catch (Throwable e) { HookEntry.log("dismiss portal", e); }
        show(false); toast("已暂存，点击暂存栏查看");
    }

    private boolean startDrag(View source, Item item) {
        try {
            ClipData clip = item.clip(context);
            int flags = View.DRAG_FLAG_GLOBAL;
            if (item.uri != null) flags |= View.DRAG_FLAG_GLOBAL_URI_READ;
            boolean ok = source.startDragAndDrop(clip, new View.DragShadowBuilder(source), item, flags);
            if (!ok) { dragTouch = false; toast("系统没有启动拖放，可改用分享"); return true; }
            dragging = true;
            // Keep this window attached to receive DRAG_ENDED, but only leave a tiny edge
            // target so it cannot intercept the destination application's drop area.
            if (window != null) {
                window.setAlpha(0.15f);
                params.width = dp(40); params.height = dp(40);
                wm.updateViewLayout(window, params);
            }
        } catch (Throwable e) { dragging = false; HookEntry.log("drag", e); toast("拖放失败，可使用系统分享"); show(false); }
        return true;
    }
    private void share(Item item) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            if (item.uri == null) { send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT, item.text); }
            else {
                send.setType("image/png"); send.putExtra(Intent.EXTRA_STREAM, item.uri);
                send.setClipData(item.clip(context)); send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            Intent chooser = Intent.createChooser(send, "分享暂存内容").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (item.uri != null) { chooser.setClipData(send.getClipData()); chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            context.startActivity(chooser); show(false);
        } catch (RuntimeException e) { HookEntry.log("share", e); toast("系统分享不可用，暂存内容仍保留"); }
    }
    private void deleteCache(Uri uri) {
        try { context.getContentResolver().delete(uri, null, null); }
        catch (RuntimeException e) { HookEntry.log("delete cache", e); }
    }
    private void removeItem(Item item, boolean persist) {
        items.remove(item);
        if (item.uri != null) deleteCache(item.uri);
        if (persist) save();
        resumeStopJob();
    }
    private void clear() {
        generation++;
        for (Item item : new ArrayList<>(items)) removeItem(item, false);
        save(); resumeStopJob();
    }
    private void resumeStopJob() {
        if (hasItems()) return;
        Service host = service.get();
        if (host != null) try { XposedHelpers.callMethod(host, "scheduleStopSelfJob"); }
        catch (Throwable e) { HookEntry.log("resume service lifecycle", e); }
    }
    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Item i : items) {
                JSONObject o = new JSONObject(); o.put("id", i.id); o.put("time", i.time);
                if (i.uri != null) o.put("uri", i.uri.toString()); else o.put("text", i.text);
                array.put(o);
            }
            prefs.edit().putString("items", array.toString()).apply();
        } catch (Exception e) { HookEntry.log("save", e); }
    }
    private void restore() {
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            boolean clearPending = config.clearToken > prefs.getLong("clearToken", 0);
            for (int n = 0; n < array.length() && items.size() < 24; n++) {
                JSONObject o = array.getJSONObject(n);
                Item i = new Item(); i.id = o.getString("id"); i.time = o.getLong("time");
                if (o.has("uri")) i.uri = Uri.parse(o.getString("uri")); else i.text = o.getString("text");
                long age = System.currentTimeMillis() - i.time;
                if (clearPending || age < 0 || age >= config.ttl()) { if (i.uri != null) deleteCache(i.uri); continue; }
                if (i.uri != null) {
                    Bundle b = context.getContentResolver().call(CACHE, "claim", i.uri.toString(), null);
                    if (b == null || !b.getBoolean("ok")) continue;
                }
                items.add(i);
            }
            prefs.edit().putLong("clearToken", config.clearToken).apply();
            save();
            if (!items.isEmpty()) main.post(() -> show(false));
        } catch (Exception e) { HookEntry.log("restore", e); }
    }
    void shutdown() {
        stopped = true; generation++; dragging = false;
        main.removeCallbacks(expire); removeWindow(); save();
        if (configObserver != null) context.getContentResolver().unregisterContentObserver(configObserver);
        if (dragRecovery != null) main.removeCallbacks(dragRecovery);
        if (receiverRegistered) {
            try { context.unregisterReceiver(screen); } catch (RuntimeException ignored) { }
            receiverRegistered = false;
        }
        io.shutdown();
    }
}
