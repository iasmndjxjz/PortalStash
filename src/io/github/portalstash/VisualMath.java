package io.github.portalstash;

/** Platform-independent geometry and damped-spring math. */
final class VisualMath {
    static int radius(int value) { return Math.max(12, Math.min(36, value)); }
    static int glassMode(int value) { return value == 1 || value == 2 ? value : 0; }
    static float spring(float time, float damping, float response) {
        if (time <= 0) return 0;
        double omega = 2 * Math.PI / response;
        double wd = omega * Math.sqrt(1 - damping * damping);
        return (float)(1 - Math.exp(-damping * omega * time)
                * (Math.cos(wd * time) + damping * omega / wd * Math.sin(wd * time)));
    }
}
