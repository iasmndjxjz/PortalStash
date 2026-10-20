package android.net;
/** JVM-test-only shim for Config's static URI constant. Never packaged into APK.
 * No URI, provider, Android lifecycle or permission behavior is tested by this shim. */
public final class Uri {
    private Uri() { }
    public static Uri parse(String ignored) { return new Uri(); }
}
