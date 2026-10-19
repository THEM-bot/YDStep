@echo off
REM Convenience wrapper. Adjust these three paths to match your machine.

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "SDK_BUILD_TOOLS=%TEMP%\apkbuild\android-14"
set "SDK_PLATFORM=%TEMP%\apkbuild\plat34\android-34\android.jar"

REM Point this at your own keystore to produce properly signed releases.
REM Without it, build.ps1 generates a throwaway key on first run.
REM set "YDSTEP_KEYSTORE=%USERPROFILE%\.ydstep\release.keystore"
REM set "YDSTEP_STOREPASS=your-password"

powershell -ExecutionPolicy Bypass -File "%~dp0build.ps1"
pause
