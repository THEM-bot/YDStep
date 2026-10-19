package com.ydstep;

/**
 * The single source of truth for behaviour.
 *
 * Editing these values and reinstalling the APK is the most reliable way to
 * configure the module: it needs no cross-process file access and no root, both
 * of which are unreliable on Android 16 with an exploit-based (non-Magisk) root.
 */
public final class BuildConfig {
    /** Steps recorded per real step. */
    public static final int FACTOR = 20;

    private BuildConfig() { }
}
