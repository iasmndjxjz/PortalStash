package io.github.portalstash;
/** Pure JVM checks only. Does not emulate Android, SharedPreferences, provider or hooks. */
public final class ConfigTest {
    static int count;
    static void check(boolean condition) { count++; if (!condition) throw new AssertionError("check " + count); }
    public static void main(String[] args) {
        Config c = new Config();
        check(c.enabled && c.minutes == 60 && c.limit == 12);
        check(c.ttl() == 3600000L);
        c.minutes = 15; check(c.ttl() == 900000L);
        c.minutes = 360; check(c.ttl() == 21600000L);
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 16, 61, Integer.MAX_VALUE}) check(Config.validMinutes(value) == 60);
        check(Config.validMinutes(15) == 15); check(Config.validMinutes(360) == 360);
        check(Config.validLimit(6) == 6); check(Config.validLimit(24) == 24);
        for (int value : new int[]{-1, 0, 7, 25, Integer.MAX_VALUE}) check(Config.validLimit(value) == 12);
        check("light".equals(Config.validTheme("light")));
        check("dark".equals(Config.validTheme("dark")));
        check("system".equals(Config.validTheme(null)));
        check("system".equals(Config.validTheme("unknown")));
        check(c.animations && !c.stashGlass && c.corner == 24 && c.pluginGlassMode == 0);
        check(VisualMath.radius(-100) == 12); check(VisualMath.radius(99) == 36); check(VisualMath.radius(24) == 24);
        check(VisualMath.glassMode(0) == 0); check(VisualMath.glassMode(1) == 1); check(VisualMath.glassMode(2) == 2);
        check(VisualMath.glassMode(99) == 0); check(VisualMath.glassMode(-1) == 0);
        check(VisualMath.spring(0,.82f,.4f) == 0);
        for (float damping : new float[]{.82f,.9f}) {
            for (int i=0;i<=100;i++) {
                float value=VisualMath.spring(i/100f,damping,.4f);
                check(!Float.isNaN(value) && !Float.isInfinite(value) && value >= 0 && value < 1.05f);
            }
            check(Math.abs(VisualMath.spring(2,damping,.4f)-1)<.0001f);
        }
        System.out.println("PASS " + count + " pure JVM configuration checks; Android behavior NOT tested.");
    }
}
