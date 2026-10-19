# YDStep — 悦动圈步数倍率模块

一个 LSPosed 模块，通过 hook 悦动圈的步数累加入口来放大步数。

**当前发布版本：20 倍**（`release/YDStep.apk`）

---

## 快速开始

```
1. 安装 release/YDStep.apk
2. LSPosed → 模块 → 启用「步数注入」→ 作用域勾选「悦动圈」
3. 强制停止悦动圈，重新打开
4. 正常走路即可看到 20 倍增长
```

> `release/YDStep.apk` 用的是作者本人的签名密钥，只用于直接体验。
> 自己构建出来的 APK 签名不同，**无法覆盖安装**，需要先卸载再装。

改倍率：编辑 `src/com/ydstep/BuildConfig.java` 里的 `FACTOR`，重新构建安装。

---

## 目录结构

```
YDStep/
├── AndroidManifest.xml         模块清单（含 xposed 元数据 + 作用域）
├── assets/xposed_init          入口类名
├── res/values/arrays.xml       作用域声明
├── libs/api-82.jar             Xposed API stub（编译期用）
├── src/com/ydstep/
│   ├── MainHook.java           IXposedHookLoadPackage 入口
│   ├── Hook.java               核心 hook 逻辑
│   ├── BuildConfig.java        倍率常量（改这里）
│   ├── Settings.java           可选的文件配置覆盖
│   └── SettingsActivity.java   生成 root 配置命令的界面
├── scripts/
│   ├── build.ps1               无 Gradle 构建脚本
│   └── build.bat               Windows 快捷入口
└── release/
    └── YDStep.apk          可直接安装的发布包
```

---

## 构建

需要 JDK 17 + Android build-tools 34 + platform-34 的 android.jar。

```powershell
# 设置路径
$env:JAVA_HOME      = "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
$env:SDK_BUILD_TOOLS = "C:\android\build-tools\34.0.0"
$env:SDK_PLATFORM    = "C:\android\platforms\android-34\android.jar"

powershell -ExecutionPolicy Bypass -File scripts\build.ps1
```

或直接双击 `scripts\build.bat`（内含作者本机路径，请按需修改）。

输出：`release/YDStep.apk`

**签名**：首次构建时如果没有 keystore，脚本会生成一个一次性密钥。如果你要发布
二进制版本，请把自己的 keystore 放在仓库**之外**，通过环境变量指定：

```powershell
$env:YDSTEP_KEYSTORE  = "$env:USERPROFILE\.ydstep\release.keystore"
$env:YDSTEP_STOREPASS = "your-password"
$env:YDSTEP_KEYPASS   = "your-password"
$env:YDSTEP_KEYALIAS  = "ydstep"
```

> 仓库不包含任何签名私钥。`.gitignore` 已排除 `keystore/`、`*.keystore`、`*.jks`。

> 注意：必须用 **JDK 17**。用 JDK 21 时 build-tools 34 的 `d8` 会抛
> `NullPointerException: Cannot invoke "String.length()"`。

---

## 这个模块为什么这样写

逆向和实机调试花了很久，以下结论都是 logcat / sensorservice / dex 反编译验证过的，
改代码前建议先读，否则很容易重复踩坑。

### 1. 悦动圈不用硬件计步器

启动日志：

```
StepDetectorEngine: isSupportXiaomiStep:false, usage:kAutoStepDetect
```

它走的是 `kAutoStepDetect`（加速度计软件计步）。`sensorservice` 显示它对
`pedometer`（`TYPE_STEP_COUNTER`）的注册和注销发生在同一个 0.05 秒内：

```
21:22:49 + pedometer ... HardwareStepDetector     ← 注册
21:22:49 - pedometer ... HardwareStepDetector     ← 立刻注销
```

**所以 hook `SensorEvent` / `HardwareStepDetector.onSensorChanged` 完全无效**，
只能拿到一次基线值。这是第一版失败的原因。

### 2. 步数的唯一汇聚点

```
ServiceAutoStep.onStepDetectAdd(int step, long st, long et)
    ↓
    if (!dbHelper.stepInc(step, st, et)) {
        logWarn("save step fail");
        return;                    // ← 计数和界面都不更新
    }
    todayStepCount += step;
    notifyUiStepChanged(...);
    tryReportStep();               // 上报服务器
```

`onStepDetectAdd` 是**所有**步数（软/硬计步、微信同步、健康平台）的唯一入口，
且它同时负责计数、刷新界面、上报。**这是正确的 hook 点。**

### 3. 只 hook 数据库层会失败

`DeamonFootDBHelper.stepInc` 只是把步数累加进一个 `AtomicInteger`，
返回的是"这次有没有触发落盘"的布尔值，**不是写入成功**。

只 hook 这一层会看到 hook 打了日志（`stepInc 5 -> 500`），
但数据被丢弃，界面不动。这是第二版失败的原因。

### 4. 不能只放大步数

`stepInc` 会校验 `(st, et)` 推出的步速。只把步数乘 100，
时间窗口还是几秒，悦动圈算出每秒几百步 → 判定异常 → 丢数据。

### 5. 也不能放大时间窗口

曾经试过 `newSt = et - span * factor`，结果窗口被拉长到 16 分钟，
相邻样本时间倒退，触发 `stepInc` 里 `if (st < lastEndTimeMillis) return 0;`，
于是大量样本被拒，表现为"隔很久才加一次"。

### 最终方案

**只改 `onStepDetectAdd` 的 `args[0]`（步数），完全不动时间戳。**

时间戳本来就符合真实走路节奏，保持单调递增即可通过所有校验。

```java
args[0] = step * factor;
```

---

## 注意事项

- **不需要 root 就能改步数**，但 LSPosed 本身需要 root 才能加载模块。
- 本机 root 是 Dirty Frag 漏洞提权（非 Magisk/KernelSU），**重启后失效**，
  需重新提权，否则模块停止工作。
- 该 root 没有提供通用 `su`，所以 App 无法写 `/data/local/tmp` 之外的系统文件；
  倍率因此写死在 APK 里，改倍率需重新构建。
- 倍率过高容易触发风控（悦动圈有单日上限和步速异常检测），建议 ≤ 20 倍。
- 数据库内存的是加密数据（Base64 + MD5 校验），**直接改 SQLite 无效**。

---

## 已验证的环境

| 项目 | 值 |
|---|---|
| 设备 | vivo V2520A / iQOO Neo11，Android 16 (SDK 36) |
| 悦动圈 | 5.17.2.4.2 (`com.yuedong.sport`) |
| Root | Dirty Frag 临时提权 |
| Zygisk | Zygisk Next + LSPosed 2.0.0 |
| 构建 | JDK 17 + build-tools 34 + platform-34 |
