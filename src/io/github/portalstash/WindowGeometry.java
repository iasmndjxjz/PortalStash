package io.github.portalstash;

/** Pure geometry shared by window gestures and JVM tests. */
final class WindowGeometry {
    static int clamp(int value,int low,int high) { return Math.max(low,Math.min(value,Math.max(low,high))); }
    static int x(int value,int screen,int width) { return clamp(value,0,screen-width); }
    static int y(int value,int screen,int height,int inset) { return clamp(value,inset,screen-height-inset); }
    static boolean left(int x,int width,int screen) { return x+width/2 < screen/2; }
}
