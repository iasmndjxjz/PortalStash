package io.github.portalstash;

import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.UUID;

/** Private image cache, explicitly granted URIs only; never exposes arbitrary paths. */
public final class StashProvider extends ContentProvider {
    public static final String AUTH = "io.github.portalstash.cache";
    public static final String HOST = "com.miui.contentextension";
    private static final int GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private File root;

    @Override public boolean onCreate() {
        root = new File(getContext().getFilesDir(), "images");
        return root.isDirectory() || root.mkdirs();
    }

    private boolean isHost() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return true;
        String[] names = getContext().getPackageManager().getPackagesForUid(uid);
        if (names != null) for (String name : names) if (HOST.equals(name)) return true;
        return false;
    }

    private void requireHost() {
        if (!isHost()) throw new SecurityException("Only the scoped portal app may write the cache");
    }

    private File file(Uri uri) {
        if (!AUTH.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException("Invalid cache URI");
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png"))
            throw new IllegalArgumentException("Invalid cache key");
        return new File(root, name);
    }

    private void requireRead(Uri uri) {
        if (!isHost() && getContext().checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("URI grant required");
    }

    private void grantHost(Uri uri) {
        long token = Binder.clearCallingIdentity();
        try { getContext().grantUriPermission(HOST, uri, GRANTS); }
        finally { Binder.restoreCallingIdentity(token); }
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        requireHost();
        if (!AUTH.equals(uri.getAuthority()) || !"/".equals(uri.getPath()))
            throw new IllegalArgumentException("Insert at cache root only");
        prune();
        Uri item = Uri.parse("content://" + AUTH + "/" + UUID.randomUUID() + ".png");
        try { if (!file(item).createNewFile()) throw new IllegalStateException("Cache collision"); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
        grantHost(item);
        return item;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("visualSettings".equals(method)) {
            int uid=Binder.getCallingUid();
            boolean allowed=uid==android.os.Process.myUid();
            String[] packages=getContext().getPackageManager().getPackagesForUid(uid);
            if (packages!=null) for (String pkg:packages) {
                if (SystemPluginHook.SYSTEMUI.equals(pkg) || SystemPluginHook.PLUGIN.equals(pkg)) allowed=true;
            }
            if (!allowed) throw new SecurityException("Visual settings caller not allowed");
            Bundle visual=new Bundle(); visual.putInt("pluginGlassMode", Config.local(getContext()).pluginGlassMode);
            return visual; // Never grant system plug-in access to text/image caches or full preferences.
        }
        requireHost();
        if ("settings".equals(method)) return Config.local(getContext()).bundle();
        if ("clearImages".equals(method)) {
            if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException("Module only");
            File[] files = root.listFiles();
            if (files != null) for (File f : files) {
                revoke(Uri.parse("content://" + AUTH + "/" + f.getName()));
                f.delete();
            }
            return new Bundle();
        }
        if ("status".equals(method)) {
            if (extras != null) getContext().getSharedPreferences("diagnostics", 0).edit()
                    .putString("status", extras.getString("status", "unknown"))
                    .putLong("time", System.currentTimeMillis()).apply();
            return new Bundle();
        }
        if (!"claim".equals(method) || arg == null) throw new IllegalArgumentException("Unknown method");
        Uri uri = Uri.parse(arg);
        File f = file(uri);
        Bundle result = new Bundle();
        if (f.isFile() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < 86400000L) {
            grantHost(uri);
            result.putBoolean("ok", true);
        }
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if ("r".equals(mode)) {
            requireRead(uri);
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if ("w".equals(mode) || "wt".equals(mode)) {
            requireHost();
            if (!f.isFile()) throw new FileNotFoundException("No such cache item");
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        throw new SecurityException("Only read or truncate-write supported");
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        requireRead(uri);
        File f = file(uri);
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = "portal-image.png";
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        if (f.isFile()) c.addRow(row);
        return c;
    }

    @Override public String getType(Uri uri) { file(uri); return "image/png"; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) {
        requireHost();
        File f = file(uri);
        revoke(uri);
        return f.delete() ? 1 : 0;
    }
    private void revoke(Uri uri) {
        long token = Binder.clearCallingIdentity();
        try { getContext().revokeUriPermission(uri, GRANTS); }
        finally { Binder.restoreCallingIdentity(token); }
    }
    private void prune() {
        File[] files = root.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        long total = 0;
        for (File f : files) total += f.length();
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int count = files.length;
        for (File f : files) {
            if (now - f.lastModified() > 86400000L || count >= 32 || total > 128L * 1024 * 1024) {
                total -= f.length(); count--;
                revoke(Uri.parse("content://" + AUTH + "/" + f.getName()));
                f.delete();
            }
        }
    }
}
