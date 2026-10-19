package com.ydstep;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log("YDStepHook: entered handleLoadPackage pkg=" + lpparam.packageName
                    + " proc=" + lpparam.processName);
        } catch (Throwable ignored) {
        }
        Hook.handleLoadPackage(lpparam);
    }
}
