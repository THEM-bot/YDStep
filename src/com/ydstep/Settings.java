package com.ydstep;

/**
 * Runtime settings with a compiled-in fallback.
 *
 * The hook executes inside 悦动圈's process, which cannot read this app's private
 * preferences. It tries /data/local/tmp/ydstep.conf first (only creatable with
 * root) and otherwise falls back to the BuildConfig constant baked into the APK,
 * so behaviour is always predictable.
 *
 * File format, one `key=value` per line:
 *   factor=20
 */
public final class Settings {
    private static final String PATH = "/data/local/tmp/ydstep.conf";
    private static final String KEY_FACTOR = "factor";

    private static Settings sInstance;
    private static long sStamp = -1;

    private int mFactor = Math.max(1, BuildConfig.FACTOR);

    private Settings() { }

    static synchronized Settings get() {
        if (sInstance == null) {
            sInstance = new Settings();
        }
        sInstance.refresh();
        return sInstance;
    }

    synchronized int factor() {
        return mFactor;
    }

    private void refresh() {
        java.io.File f = new java.io.File(PATH);
        if (!f.exists()) {
            return;
        }
        long stamp = f.lastModified();
        if (stamp == sStamp) {
            return;
        }
        sStamp = stamp;
        java.util.Properties p = new java.util.Properties();
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            p.load(in);
            String v = p.getProperty(KEY_FACTOR);
            if (v != null) {
                int n = Integer.parseInt(v.trim());
                mFactor = Math.max(1, Math.min(100, n));
            }
        } catch (Throwable ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
