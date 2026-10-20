package io.github.portalstash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class InfoActivity extends Activity {
    private Config cfg;
    private MiuixStyle ui;
    private LinearLayout body;
    private int scrollY;
    private ScrollView scroll;
    @Override public void onCreate(Bundle b) { super.onCreate(b); render(); }
    @Override public void onResume() { super.onResume(); render(); }

    private void render() {
        if (scroll != null) scrollY = scroll.getScrollY();
        cfg = Config.local(this); ui = new MiuixStyle(this, cfg);
        getWindow().setStatusBarColor(ui.bg); getWindow().setNavigationBarColor(ui.bg);
        getWindow().getDecorView().setSystemUiVisibility(ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(ui.bg);
        body = ui.column(); body.setPadding(ui.dp(16), ui.dp(24), ui.dp(16), ui.dp(32));
        body.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()); top = bars.top; bottom = bars.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            v.setPadding(ui.dp(16), ui.dp(24) + top, ui.dp(16), ui.dp(32) + bottom);
            return insets;
        });
        TextView title = ui.text("传送门暂存", 32, ui.text); ui.bold(title);
        title.setPadding(ui.dp(12), ui.dp(12), 0, ui.dp(8)); body.addView(title);
        TextView sub = ui.text("内容随手放，分享更从容", 14, ui.secondary);
        sub.setPadding(ui.dp(12), 0, 0, ui.dp(24)); body.addView(sub);

        LinearLayout status = group();
        long last = getSharedPreferences("diagnostics", 0).getLong("time", 0);
        String headline = last == 0 ? "尚未确认注入" : "已收到注入记录";
        row(status, headline, last == 0 ? "仅支持传送门 5.1.5 · 请在框架内启用" :
                "历史记录：" + android.text.format.DateFormat.format("MM-dd HH:mm", last) + "，不代表当前在线", "查看", () -> about(false));
        TextView nativeNote = ui.text("长按后新增“暂存”，保存后变为“暂存栏”\n入口复用传送门原生卡片、字体和图标", 12, ui.secondary);
        nativeNote.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(18)); status.addView(nativeNote);

        section("功能"); LinearLayout features = group();
        LinearLayout enabled = row(features, "暂存侧栏", "在传送门识别后提供暂存入口", null, null);
        Switch sw = new Switch(this); sw.setChecked(cfg.enabled); sw.setShowText(false);
        sw.setThumbTintList(ColorStateList.valueOf(0xffffffff));
        sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ui.blue, ui.dark ? 0xff555555 : 0xffdddddf}));
        sw.setOnCheckedChangeListener((v, checked) -> { cfg.enabled = checked; cfg.save(this); });
        enabled.addView(sw);
        row(features, "保存时长", null, cfg.durationLabel(), () -> choose("保存时长", new String[]{"15 分钟", "1 小时", "6 小时"},
                cfg.minutes == 15 ? 0 : cfg.minutes == 360 ? 2 : 1, i -> { cfg.minutes = new int[]{15, 60, 360}[i]; changed(); }));
        row(features, "暂存上限", "调低不会立即删除已有内容", cfg.limit + " 条", () -> choose("暂存上限", new String[]{"6 条", "12 条", "24 条"},
                cfg.limit == 6 ? 0 : cfg.limit == 24 ? 2 : 1, i -> { cfg.limit = new int[]{6, 12, 24}[i]; changed(); }));

        section("外观"); LinearLayout appearance = group();
        row(appearance, "设置页主题", "传送门内入口使用原应用主题",  cfg.themeLabel(), () -> choose("主题", new String[]{"跟随系统", "浅色", "深色"},
                "light".equals(cfg.theme) ? 1 : "dark".equals(cfg.theme) ? 2 : 0,
                i -> { cfg.theme = new String[]{"system", "light", "dark"}[i]; changed(); }));
        row(appearance, "侧栏位置", null, "left".equals(cfg.side) ? "左侧" : "右侧", () -> choose("侧栏位置", new String[]{"左侧", "右侧"},
                "left".equals(cfg.side) ? 0 : 1, i -> { cfg.side = i == 0 ? "left" : "right"; changed(); }));

        section("圆角与动效"); LinearLayout motion = group();
        row(motion, "内容圆角", "暂存栏四角、卡片与图片分别裁切", cfg.corner + " dp", () -> choose("内容圆角", new String[]{"18 dp", "24 dp", "30 dp"},
                cfg.corner == 18 ? 0 : cfg.corner == 30 ? 2 : 1, i -> { cfg.corner = new int[]{18,24,30}[i]; changed(); }));
        toggle(motion, "原生平移动画", "与传送门一致：从屏幕边缘直接平移进出，无回弹；遵循系统关闭动画选项", cfg.animations,
                value -> { cfg.animations=value; cfg.save(this); });
        toggle(motion, "暂存栏原生柔光玻璃", "实验功能；原生接口不可用时保留普通背景", cfg.stashGlass,
                value -> { cfg.stashGlass=value; cfg.save(this); });
        section("澎湃 4 · 系统界面组件"); LinearLayout plugin = group();
        row(plugin, "组件材质", "仅限 18.3.2.22.0；不改桌面、通知或锁屏", new String[]{"跟随系统", "柔光玻璃", "经典材质"}[cfg.pluginGlassMode], () ->
                choose("组件材质（实验）", new String[]{"跟随系统 · 默认", "柔光玻璃", "经典材质"}, cfg.pluginGlassMode, i -> {
                    if (i==0) { cfg.pluginGlassMode=0; changed(); return; }
                    new AlertDialog.Builder(this).setTitle("启用系统组件材质 Hook？")
                        .setMessage("仅分析过指定原始 APK，尚未实机验证。可能改变控制中心、音量等组件的材质。\n\n需在框架额外勾选系统界面与系统界面组件，首次由你手动重启加载。不会强制设备支持，也不会自动结束系统进程。\n\n与 HyperLight 等材质模块同时启用可能冲突。设置回跟随系统后，在面板重新创建时恢复。")
                        .setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->{ cfg.pluginGlassMode=i; changed(); }).show();
                }));
        section("作用域管理"); LinearLayout manage = group();
        String version = "未检测到";
        try { version = getPackageManager().getPackageInfo(StashProvider.HOST, 0).versionName; } catch (Exception ignored) { }
        row(manage, "传送门", "com.miui.contentextension", version, () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + StashProvider.HOST))));
        LinearLayout restart = row(manage, "重启传送门", "当前环境不允许结束系统应用", "不可用", null);
        restart.setAlpha(0.45f);
        row(manage, "打开传送门设置", null, "", () -> open(new Intent("com.miui.contentextension.action.TAPLUS_SETTINGS").setPackage(StashProvider.HOST)));
        row(manage, "清空暂存", "删除图片缓存，并通知已注入进程清空列表", "", () -> new AlertDialog.Builder(this)
                .setTitle("清空暂存？").setMessage("已分享出去的副本不受影响。若传送门进程不在线，文字列表将在下次载入时清空。")
                .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> {
                    cfg.clearToken = Math.max(System.currentTimeMillis(), cfg.clearToken + 1); cfg.save(this);
                    getContentResolver().call(Config.URI, "clearImages", null, null);
                    Toast.makeText(this, "已清理缓存并发出清空请求", 0).show();
                }).show());
        section("关于"); LinearLayout about = group();
        row(about, "使用说明与兼容性", "仅操作用户主动识别的文字和图片", "", () -> about(false));
        row(about, "界面与版本", "Miuix 风格 · 原生 Views 轻量实现", "0.4.0", () -> about(true));
        TextView footer = ui.text("本地暂存 · 不联网 · 不自动发送\n设置保存后通知传送门，无需为设置变化重启手机", 12, ui.secondary);
        footer.setGravity(Gravity.CENTER); footer.setLineSpacing(ui.dp(5), 1); footer.setPadding(0, ui.dp(22), 0, ui.dp(10)); body.addView(footer);
        scroll.addView(body); setContentView(scroll); body.requestApplyInsets();
        scroll.post(() -> scroll.scrollTo(0, scrollY));
    }
    private void toggle(LinearLayout group, String title, String detail, boolean value, java.util.function.Consumer<Boolean> change) {
        LinearLayout line=row(group,title,detail,null,null);
        Switch control=new Switch(this); control.setChecked(value);
        control.setOnCheckedChangeListener((button,checked)->change.accept(checked)); line.addView(control);
    }
    private void changed() { cfg.save(this); render(); }
    private interface Selection { void pick(int i); }
    private void choose(String title, String[] options, int selected, Selection result) {
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options, selected, (d, i) -> {
            d.dismiss(); result.pick(i);
        }).setNegativeButton("取消", null).show();
    }
    private void section(String value) {
        TextView t = ui.text(value, 13, ui.secondary); t.setPadding(ui.dp(16), ui.dp(10), 0, ui.dp(9)); body.addView(t);
    }
    private LinearLayout group() {
        LinearLayout box = ui.column(); box.setBackground(ui.shape(ui.surface, 24)); box.setClipToOutline(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = ui.dp(12); body.addView(box, p); return box;
    }
    private LinearLayout row(LinearLayout parent, String title, String summary, String value, Runnable action) {
        if (parent.getChildCount() > 0) {
            View divider = new View(this); divider.setBackgroundColor(ui.divider);
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, ui.dp(0.5f)); line.leftMargin = ui.dp(20); line.rightMargin = ui.dp(20); parent.addView(divider, line);
        }
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(20), ui.dp(17), ui.dp(20), ui.dp(17)); row.setMinimumHeight(ui.dp(62));
        LinearLayout texts = ui.column(); TextView main = ui.text(title, 16, ui.text); texts.addView(main);
        if (summary != null) { TextView s = ui.text(summary, 12, ui.secondary); s.setPadding(0, ui.dp(5), ui.dp(8), 0); texts.addView(s); }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null) {
            TextView trailing = ui.text(value + (action != null ? "  ›" : ""), 14, ui.secondary); row.addView(trailing);
        }
        if (action != null) {
            row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(ui.dark ? 0x20ffffff : 0x10000000), null, ui.shape(0xffffffff, 0)));
            row.setOnClickListener(v -> action.run());
        }
        parent.addView(row); return row;
    }
    private void open(Intent intent) {
        try { startActivity(intent); } catch (RuntimeException e) { Toast.makeText(this, "暂时无法打开，请在系统设置中搜索传送门", 0).show(); }
    }
    private void preview() {
        LinearLayout box = ui.column(); box.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
        TextView hint = ui.text("示例内容 · 不是你的真实暂存", 12, ui.secondary); box.addView(hint);
        TextView sample = ui.text("周末见面时，记得把这份旅行计划分享给朋友。", 16, ui.text);
        sample.setPadding(0, ui.dp(22), 0, ui.dp(20)); box.addView(sample);
        TextView image = ui.text("图片缩略图", 14, ui.secondary); image.setGravity(Gravity.CENTER);
        image.setBackground(ui.shape(ui.bg, 16)); box.addView(image, new LinearLayout.LayoutParams(-1, ui.dp(116)));
        TextView tip = ui.text("实际侧栏支持长按拖出、分享、复制和删除", 12, ui.secondary); tip.setPadding(0, ui.dp(18), 0, 0); box.addView(tip);
        new AlertDialog.Builder(this).setTitle("暂存 · 外观预览").setView(box).setPositiveButton("知道了", null).show();
    }
    private void about(boolean style) {
        new AlertDialog.Builder(this).setTitle(style ? "界面与版本" : "使用说明")
                .setMessage(style ? "0.4.1 澎湃 4 版\n\n新增三层独立圆角、与传送门一致的边缘平移动效、可选暂存栏原生玻璃，以及限定系统界面组件 18.3.2.22.0 的材质选择 Hook。两项玻璃功能默认关闭／跟随系统。系统组件重新创建后应用材质，不修改系统设置、不改桌面。\n\n长按入口复用原应用的 TaplusRecognitionShrinkCard、背景、字体和保存图标；原识别功能不被替换。暂存内容横向排列，长按拖出，上滑移除。模块设置页仍为原生 Views，并未集成 Compose Miuix 组件库。\n\n已构建不等于已完成实机验证。" :
                "1. 普通暂存仅勾选传送门。系统组件材质功能才额外勾选系统界面与系统界面组件。\n2. 首次注入需要传送门进程重新载入模块。\n3. 双指按压展开传送门，点侧边暂存入口。\n4. 展开暂存列表，长按内容拖出，或使用系统分享。\n\n跨应用拖放取决于目标应用支持；不读取通知、短信或聊天数据库，不自动发送。图片须先经传送门识别。\n\n锁屏时隐藏。过期清理在进程运行或再次启动时执行，不保证后台精确擦除。")
                .setPositiveButton("知道了", null).show();
    }
}
