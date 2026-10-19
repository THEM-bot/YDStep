package com.ydstep;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Injects steps into 悦动圈.
 *
 * Device findings that shaped this design (confirmed via logcat + dex analysis):
 *
 *  1. The app logs "StepDetectorEngine: usage:kAutoStepDetect": it uses software
 *     step detection, not the hardware pedometer, and unregisters the pedometer
 *     within 0.05s. Hooking SensorEvent therefore only sees one baseline tick.
 *
 *  2. ServiceAutoStep.onStepDetectAdd(int step, long st, long et) is the single
 *     funnel that updates the counter, refreshes the UI and schedules upload:
 *
 *         if (!dbHelper.stepInc(step, st, et)) { logWarn("save step fail"); return; }
 *         todayStepCount += step;
 *         notifyUiStepChanged(...);
 *
 *  3. DeamonFootDBHelper.stepInc validates the timestamp pair. It rejects the
 *     sample when st < lastEndTimeMillis (time moving backwards) and when the
 *     implied cadence is impossible.
 *
 * Earlier attempts scaled st backwards by `factor`, producing windows of many
 * minutes that overlapped each other. stepInc then saw st going backwards and
 * rejected most samples, which is why updates only landed occasionally.
 *
 * The fix: leave the timestamps completely alone. They are already plausible for
 * the real steps; only the recorded count changes. The app's validation is about
 * the (st, et) pair, not about step density on its own.
 */
public final class Hook {
    private static final String TAG = "YDStepHook";
    private static final String TARGET_PKG = "com.yuedong.sport";

    private static final String AUTO_STEP_CLASS =
            "com.yuedong.sport.controller.stepdetect.ServiceAutoStep";
    private static final String AUTO_STEP_METHOD = "onStepDetectAdd";

    private Hook() { }

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        log("loading proc=" + lpparam.processName
                + " factor=" + Settings.get().factor());

        Class<?> cls;
        try {
            cls = XposedHelpers.findClassIfExists(AUTO_STEP_CLASS, lpparam.classLoader);
        } catch (Throwable t) {
            cls = null;
        }
        if (cls == null) {
            return;
        }

        try {
            XposedBridge.hookAllMethods(cls, AUTO_STEP_METHOD, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        rewrite(param);
                    } catch (Throwable t) {
                        log("rewrite err: " + t);
                    }
                }
            });
            log("hooked " + AUTO_STEP_CLASS + "." + AUTO_STEP_METHOD);
        } catch (Throwable t) {
            log("hook failed: " + t);
        }
    }

    private static void rewrite(XC_MethodHook.MethodHookParam param) {
        Object[] args = param.args;
        if (args == null || args.length < 3) {
            return;
        }
        if (!(args[0] instanceof Integer)) {
            return;
        }
        int step = (Integer) args[0];
        if (step <= 0) {
            return;
        }

        int factor = Settings.get().factor();
        if (factor <= 1) {
            return;
        }

        int out = step * factor;
        args[0] = out;

        // Timestamps are intentionally untouched: they stay monotonic, so
        // stepInc's st >= lastEndTimeMillis check keeps passing.
        log("onStepDetectAdd " + step + " -> " + out);
    }

    private static void log(String msg) {
        Log.i(TAG, msg);
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
