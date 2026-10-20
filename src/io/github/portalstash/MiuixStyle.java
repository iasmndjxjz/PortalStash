package io.github.portalstash;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight Android Views styling inspired by Miuix; NOT the Compose Miuix library. */
final class MiuixStyle {
    final Context c;
    final boolean dark;
    final int bg, surface, text, secondary, divider;
    final int blue = 0xff3482ff;
    MiuixStyle(Context context, Config cfg) {
        c = context;
        dark = "dark".equals(cfg.theme) || ("system".equals(cfg.theme)
                && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        bg = dark ? 0xff101010 : 0xfff7f7f7;
        surface = dark ? 0xff232323 : 0xffffffff;
        text = dark ? 0xfff3f3f3 : 0xff161616;
        secondary = dark ? 0xffaaaaaa : 0xff85858b;
        divider = dark ? 0xff353535 : 0xffeeeeee;
    }
    int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView text(String value, int sp, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false); return t;
    }
    TextView action(String title, Runnable run) {
        TextView t = text(title, 14, blue);
        t.setGravity(Gravity.CENTER); t.setMinHeight(dp(44));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x33ffffff : 0x18000000), shape(surface, 14), null));
        t.setOnClickListener(v -> run.run());
        return t;
    }
    LinearLayout column() { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    void bold(TextView t) { t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
}
