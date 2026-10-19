# Build YDStep without Gradle
#
# Requires:
#   - JDK 17 (build-tools 34's d8 is Java-version sensitive; JDK 21 crashes it)
#   - Android build-tools 34 and platform-34 (android.jar)
#   - Xposed API stub at libs/api-82.jar
#
# Environment variables consumed here:
#   SDK_BUILD_TOOLS   path to build-tools/34.x.x
#   SDK_PLATFORM      path to platform-34/android.jar
#   YDSTEP_KEYSTORE   path to a keystore (default: keystore/ydstep.keystore)
#   YDSTEP_STOREPASS  keystore password     (default: android)
#   YDSTEP_KEYPASS    key password          (default: same as store password)
#   YDSTEP_KEYALIAS   key alias             (default: ydstep)
#
# If the keystore does not exist it is generated with a throwaway key, which is
# fine for personal builds. For releases you intend to publish, keep your own
# keystore OUTSIDE this repository and point YDSTEP_KEYSTORE at it.
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\build.ps1

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Src  = Join-Path $Root "src"
$Out  = Join-Path $Root "build"
$Classes = Join-Path $Out "classes"
$Dex     = Join-Path $Out "dex"
$Apk     = Join-Path $Out "apk"

$BuildTools  = $env:SDK_BUILD_TOOLS
$PlatformJar = $env:SDK_PLATFORM
$XposedJar   = Join-Path $Root "libs\api-82.jar"

$Keystore    = if ($env:YDSTEP_KEYSTORE)  { $env:YDSTEP_KEYSTORE }  else { Join-Path $Root "keystore\ydstep.keystore" }
$StorePass   = if ($env:YDSTEP_STOREPASS) { $env:YDSTEP_STOREPASS } else { "android" }
$KeyPass     = if ($env:YDSTEP_KEYPASS)   { $env:YDSTEP_KEYPASS }   else { $StorePass }
$KeyAlias    = if ($env:YDSTEP_KEYALIAS)  { $env:YDSTEP_KEYALIAS }  else { "ydstep" }

if (-not $BuildTools)  { throw "SDK_BUILD_TOOLS is not set (path to build-tools/34.x.x)" }
if (-not $PlatformJar) { throw "SDK_PLATFORM is not set (path to platform-34/android.jar)" }
if (-not (Test-Path $PlatformJar)) { throw "android.jar not found: $PlatformJar" }
if (-not (Test-Path $XposedJar))   { throw "api-82.jar not found: $XposedJar" }

$Javac   = Join-Path $env:JAVA_HOME "bin\javac.exe"
$JarExe  = Join-Path $env:JAVA_HOME "bin\jar.exe"
$Keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path $Javac)) { throw "javac not found; set JAVA_HOME to a JDK 17" }

Remove-Item -Recurse -Force $Out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes, $Dex, $Apk | Out-Null

# 1. compile
$sources = Get-ChildItem -Recurse $Src -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "Compiling..."
& $Javac -encoding UTF-8 -g -source 8 -target 8 `
    -bootclasspath $PlatformJar `
    -classpath $XposedJar `
    -d $Classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 2. dex
Write-Host "Dexing..."
& $JarExe cf (Join-Path $Out "classes.jar") -C $Classes .
& (Join-Path $BuildTools "d8.bat") --lib $PlatformJar --min-api 24 `
    --output $Dex (Join-Path $Out "classes.jar")
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 3. resources + manifest
Write-Host "Packaging..."
& (Join-Path $BuildTools "aapt2.exe") compile --dir (Join-Path $Root "res") `
    -o (Join-Path $Apk "res.zip")
& (Join-Path $BuildTools "aapt2.exe") link -o (Join-Path $Apk "base.apk") `
    -I $PlatformJar `
    --manifest (Join-Path $Root "AndroidManifest.xml") `
    -A (Join-Path $Root "assets") `
    --min-sdk-version 24 --target-sdk-version 34 `
    (Join-Path $Apk "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Copy-Item (Join-Path $Dex "classes.dex") (Join-Path $Apk "classes.dex") -Force
Push-Location $Apk
& (Join-Path $BuildTools "aapt.exe") add base.apk classes.dex | Out-Null
Pop-Location

# 4. align + sign
& (Join-Path $BuildTools "zipalign.exe") -f -p 4 `
    (Join-Path $Apk "base.apk") (Join-Path $Apk "aligned.apk")

if (-not (Test-Path $Keystore)) {
    Write-Host "No keystore at $Keystore - generating a throwaway one."
    New-Item -ItemType Directory -Force -Path (Split-Path $Keystore) | Out-Null
    & $Keytool -genkeypair -keystore $Keystore -storepass $StorePass `
        -keypass $KeyPass -alias $KeyAlias -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=YDStep, OU=Dev, O=Dev, L=NA, ST=NA, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

New-Item -ItemType Directory -Force -Path (Join-Path $Root "release") | Out-Null
& (Join-Path $BuildTools "apksigner.bat") sign `
    --ks $Keystore --ks-pass "pass:$StorePass" --key-pass "pass:$KeyPass" `
    --ks-key-alias $KeyAlias `
    --out (Join-Path $Root "release\YDStep.apk") `
    (Join-Path $Apk "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host ""
Write-Host "Built: $(Join-Path $Root 'release\YDStep.apk')"
Write-Host "Signed with: $Keystore"
