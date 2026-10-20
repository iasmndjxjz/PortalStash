package io.github.portalstash;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.XposedHelpers;

/** Reuses the host APK's class, layout, background, font, text colors and icons at runtime. */
final class NativePortalUi {
    static int id(Context c, String type, String name) {
        int value = c.getResources().getIdentifier(name, type, "com.miui.contentextension");
        if (value == 0) throw new IllegalStateException("Required native resource missing");
        return value;
    }
    static int dim(Context c, String name) {
        return c.getResources().getDimensionPixelSize(id(c, "dimen", name));
    }
    static Drawable drawable(Context c, String name) {
        return c.getResources().getDrawable(id(c, "drawable", name), c.getTheme());
    }
    static LinearLayout entry(Context c, String label, String icon, Runnable click) {
        Class<?> clazz = XposedHelpers.findClass("com.miui.contentextension.text.cardview.TaplusRecognitionShrinkCard", c.getClassLoader());
        LinearLayout card = (LinearLayout) XposedHelpers.newInstance(clazz, c, (AttributeSet)null);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(drawable(c, "card_bg_taplus_shrink_all_corner"));
        card.setElevation(dim(c, "shrink_card_elevation"));
        int pad = dim(c, "dp_8");
        card.setPadding(pad, pad, dim(c, "dp_14"), pad);
        card.setMinimumHeight(dim(c, "shrink_card_height"));
        card.setId(View.generateViewId());
        update(card, label, icon);
        card.setContentDescription(label);
        card.setOnClickListener(v -> click.run());
        // Original touch animation is scoped to this card, not a global gesture replacement.
        XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.miui.contentextension.utils.FolmeUtil", c.getClassLoader()),
                "doScaleOnTouch", (Object) new View[]{card});
        return card;
    }
    static void update(LinearLayout card, String label, String icon) {
        TextView text = (TextView) XposedHelpers.callMethod(card, "getRecognitionText");
        ImageView image = (ImageView) XposedHelpers.callMethod(card, "getRecognitionIcon");
        text.setText(label); image.setImageResource(id(card.getContext(), "drawable", icon));
        card.setContentDescription(label);
    }
    static TextView text(Context c, String value) {
        LinearLayout nativeCard = entry(c, value, "icon_recognition_image_save", () -> { });
        TextView source = (TextView) XposedHelpers.callMethod(nativeCard, "getRecognitionText");
        TextView text = new TextView(c);
        text.setText(value); text.setTextColor(source.getTextColors());
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, source.getTextSize());
        text.setTypeface(source.getTypeface()); text.setIncludeFontPadding(source.getIncludeFontPadding());
        return text;
    }
}
