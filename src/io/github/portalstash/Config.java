package io.github.portalstash;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

/** Persisted in the module app. Host reads through the permission-checked provider. */
final class Config {
    static final Uri URI = Uri.parse("content://io.github.portalstash.cache/settings");
    boolean enabled = true;
    int minutes = 60;
    int limit = 12;
    String theme = "system";
    String side = "right";
    long clearToken;
    boolean animations = true;
    boolean stashGlass = false;
    int corner = 24;
    int pluginGlassMode = 0; // 0 follows system, 1 glass, 2 classic; never force device capability.

    static Config local(Context c) {
        SharedPreferences p = c.getSharedPreferences("settings", 0);
        Config v = new Config();
        v.enabled = p.getBoolean("enabled", true);
        v.minutes = validMinutes(p.getInt("minutes", 60));
        v.limit = validLimit(p.getInt("limit", 12));
        v.theme = validTheme(p.getString("theme", "system"));
        v.side = "left".equals(p.getString("side", "right")) ? "left" : "right";
        v.clearToken = p.getLong("clearToken", 0);
        v.animations = p.getBoolean("animations", true); v.stashGlass = p.getBoolean("stashGlass", false);
        v.corner = VisualMath.radius(p.getInt("corner", 24));
        v.pluginGlassMode = VisualMath.glassMode(p.getInt("pluginGlassMode", 0));
        return v;
    }
    static Config remote(Context c) {
        Bundle b = c.getContentResolver().call(URI, "settings", null, null);
        if (b == null) throw new IllegalStateException("Settings unavailable");
        Config v = new Config();
        v.enabled = b.getBoolean("enabled", true);
        v.minutes = validMinutes(b.getInt("minutes", 60));
        v.limit = validLimit(b.getInt("limit", 12));
        v.theme = validTheme(b.getString("theme", "system"));
        v.side = "left".equals(b.getString("side")) ? "left" : "right";
        v.clearToken = b.getLong("clearToken", 0);
        v.animations = b.getBoolean("animations", true); v.stashGlass = b.getBoolean("stashGlass", false);
        v.corner = VisualMath.radius(b.getInt("corner", 24));
        v.pluginGlassMode = VisualMath.glassMode(b.getInt("pluginGlassMode", 0));
        return v;
    }
    void save(Context c) {
        c.getSharedPreferences("settings", 0).edit().putBoolean("enabled", enabled)
                .putInt("minutes", validMinutes(minutes)).putInt("limit", validLimit(limit))
                .putString("theme", validTheme(theme)).putString("side", "left".equals(side) ? "left" : "right")
                .putLong("clearToken", clearToken).putBoolean("animations", animations).putBoolean("stashGlass", stashGlass)
                .putInt("corner", VisualMath.radius(corner)).putInt("pluginGlassMode", VisualMath.glassMode(pluginGlassMode)).apply();
        c.getContentResolver().notifyChange(URI, null);
    }
    Bundle bundle() {
        Bundle b = new Bundle();
        b.putBoolean("enabled", enabled); b.putInt("minutes", minutes); b.putInt("limit", limit);
        b.putString("theme", theme); b.putString("side", side); b.putLong("clearToken", clearToken);
        b.putBoolean("animations", animations); b.putBoolean("stashGlass", stashGlass);
        b.putInt("corner", corner); b.putInt("pluginGlassMode", pluginGlassMode);
        return b;
    }
    long ttl() { return minutes * 60000L; }
    String durationLabel() { return minutes == 15 ? "15 分钟" : minutes == 360 ? "6 小时" : "1 小时"; }
    String themeLabel() { return "dark".equals(theme) ? "深色" : "light".equals(theme) ? "浅色" : "跟随系统"; }
    static int validMinutes(int x) { return x == 15 || x == 360 ? x : 60; }
    static int validLimit(int x) { return x == 6 || x == 24 ? x : 12; }
    static String validTheme(String x) { return "light".equals(x) || "dark".equals(x) ? x : "system"; }
}
