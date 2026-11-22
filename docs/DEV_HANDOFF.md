# LumaSR 开发交接文档

> 版本：`0.6.0-lab`（versionCode 15）  
> 更新日期：2026-11-22  
> 适用对象：后续接手的开发者

---

## 1. 项目是什么

LumaSR 是一个 Android 本地图片超分 App：

- UI：Jetpack Compose + Material 3，单 Activity
- 引擎：Mihon Upscale 97 的预编译 `libwaifu2x-jni.so`
- 后端：NCNN Vulkan（通用）+ Qualcomm QNN NPU（骁龙 HTP）
- 模型：9 个家族，包含 Real-CUGAN、Real-ESRGAN、Waifu2x、AnimeJaNai、sudo、SPAN 等
- 主要流程：SAF 选文件夹 → 递归扫描 → 网格筛选/全选 → 统一参数批处理 → 输出到 `源文件夹/LumaSR_Output` 或统一导出目录 → 任务/结果/对比
- 单图流程保留：Photo Picker → 推理 → 对比 → 保存相册
- 实验室：模型对比测试（同一张图跑多个模型/后端，输出 `LumaSR_Test/<时间戳>` + `results.csv`）
- 模型说明页：9 个模型的区别、适用场景、NPU 支持

当前 APK：

- 包名：`io.lumasr.app`
- 版本：`0.6.0-lab`（versionCode 15）
- ABI：仅 `arm64-v8a`
- minSdk 29 / targetSdk 35
- 约 223 MB（主要是 QNN Prepare 79 MB + 5 个 HTP skel 约 84 MB + 模型资产）
- Release 关闭 R8；lint 也关闭（避免小内存环境 OOM）

---

## 2. 环境与构建

### 必需

- JDK 17
- Android SDK 35
- 不需要 NDK / CMake（引擎是预编译 `.so`）
- 首次构建需要网络下载 Gradle 8.9 和依赖

### 快速命令

```bash
# 本地增量 Release，产物复制到 dist/ 并输出 SHA
scripts/build.sh

# 打源码交接包（本文件 + 全部源码/资产）
scripts/package-source.sh
```

### 手动构建

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleRelease
```

### 重要约束

- **不要随意 `clean`**：当前构建开了 Gradle configuration cache、build cache，增量构建通常几十秒。
- `local.properties` 是机器相关文件，源码包里不包含；接手后自己创建。
- Release `lint` 已在 `app/build.gradle.kts` 关闭；需要时单独运行 `./gradlew :app:lintRelease`。
- `engine97` 的 Kotlin 类必须保持原包名 `eu.kanade.tachiyomi.util.*`，JNI 是静态注册；不要重命名 `Waifu2x`、`QnnModelContexts`、`QualcommHtp`、`MediaTekNpu`。
- `useLegacyPackaging = true` 与官方 APK 一致；QNN 需要 `nativeLibraryDir` 下实际存在 skel，不要随便改成压缩。
- 高通 QNN 运行时（`libQnn*.so`）和 `qnn-models` 不在仓库中，需按 QAIRT 2.49.0 自行放入对应目录再构建 NPU 版本。
- Release 当前使用本机 debug keystore 签名，方便直接安装；正式分发要换签名。

---

## 3. 目录结构

```text
LumaSR/
├── app/                                  # 应用 UI 与业务
│   └── src/main/java/io/lumasr/app/
│       ├── MainActivity.kt
│       ├── LumaSrApp.kt
│       ├── AppViewModel.kt               # 核心状态、导航、单图/批量/诊断
│       ├── engine/
│       │   ├── Engine97.kt               # 引擎门面、NPU 诊断/错误
│       │   └── EngineModels.kt           # 9 个模型与 NPU 支持表
│       ├── folder/
│       │   ├── FolderModels.kt           # FolderImage / FolderSnapshot / OutputRecord
│       │   ├── FolderScanner.kt          # SAF 扫描、输出写入、已处理判断
│       │   └── OutputIndex.kt            # 源→输出清单（持久化）
│       ├── batch/
│       │   ├── BatchModels.kt            # QueueItem / QueueOptions / QueueState
│       │   ├── BatchStore.kt             # JSON 队列持久化（Mutex）
│       │   ├── BatchRepository.kt        # 状态流、入队、暂停、重试
│       │   ├── BatchWorker.kt            # WorkManager 前台批处理
│       │   └── BatchNotifications.kt
│       ├── data/
│       │   ├── AppPrefs.kt               # SharedPreferences 配置
│       │   ├── ImageLoader.kt            # ImageDecoder 解码/降采样
│       │   └── ImageSaver.kt             # 保存相册
│       ├── model/UiModels.kt             # UpscaleConfig / Dest / CompareInfo
│       └── ui/
│           ├── AppRoot.kt                # 导航 + 底部栏
│           ├── home/                     # 首页
│           ├── folder/                   # 文件夹概览 + 网格选择
│           ├── config/                   # 单图/批量参数
│           ├── batch/TaskScreen.kt       # 任务与结果
│           ├── compare/CompareScreen.kt  # 对比
│           ├── settings/                 # 设置 + 模型管理 + NPU 诊断
│           └── components/               # 通用组件
├── engine97/                             # 97 引擎（预编译 .so + QNN + 模型资产）
│   ├── src/main/jniLibs/arm64-v8a/       # libwaifu2x-jni / QNN / HTP skel / stub
│   ├── src/main/assets/qnn-models/       # DLC + manifest.json
│   ├── src/main/assets/*-models/         # ncnn .param/.bin
│   └── src/main/java/eu/kanade/tachiyomi/util/
│       ├── waifu2x/Waifu2x.kt
│       ├── qnn/QnnModelContexts.kt
│       ├── qnn/QualcommHtp.kt
│       ├── npu/NpuModelStatus.kt
│       └── mediatek/MediaTekNpu.kt
├── scripts/                              # build.sh / package-source.sh
├── dist/                                 # 构建产物（gitignore，不进源码包）
├── docs/                                 # 本文件、MODELS
├── gradle/wrapper/                       # Gradle 8.9 wrapper
├── README.md
└── build.gradle.kts / settings.gradle.kts / gradle.properties
```

---

## 4. 关键流程

### 4.1 文件夹扫描与批处理

1. `AppViewModel.openFolder(treeUri)`
2. `FolderScanner.scan(...)`
   - 用 `DocumentsContract` 每个目录一次游标查询（name / mime / size）
   - 递归时排除根目录下的 `LumaSR_Output`，以及用户指定的导出目录（避免把结果当输入）
   - 读取 `OutputIndex`，计算每张图的 `alreadyProcessed`
3. UI：`FolderOverviewScreen` → `GridSelectScreen`
4. `AppViewModel.startBatch()`
   - 用 `FolderScanner.uniqueOutputPath` 生成输出相对路径
   - `skipProcessed` 时把已处理项标为 `SKIPPED`
   - 创建 `QueueState`，保存并 `BatchRepository.enqueue`
5. `BatchWorker`
   - 前台服务 + 通知
   - `PARTIAL_WAKE_LOCK`（6 小时上限，`finally` 释放）：锁屏后 CPU 不休眠
   - 固定输出根：`源根/LumaSR_Output`
   - `OutputWriter` 按相对路径写入（保留子目录/平铺）
   - 完成后把 `sourcePath -> outputPath` 写入 `OutputIndex`
6. 完成后 `AppViewModel.refreshProcessed()` 只刷新输出目录，不重扫输入

> 单图和实验室没有后台服务，也不持唤醒锁，锁屏会停。
> 厂商电池管理（如把应用设为「限制后台」）仍可能冻结批处理，需要用户放行。

### 4.2 已处理判断

- 优先看 `OutputIndex`：`sourcePath -> OutputRecord(outputPath, format, ...)`
- 平铺模式下，只要 index 非空就完全信任 index，避免两个同名源文件互相误判
- index 不存在时退回文件名判断：`expectedOutputPath(...) in outputFiles`
- 0 字节输出不算已处理
- `skipProcessed=true` 时，`startBatch` 会把现有输出路径加入保留集合，避免新文件覆盖旧结果

### 4.3 NPU

入口：`Engine97`

1. `isQualcommNpuAvailable()`：HTP 架构 + QNN runtime
2. `prepareNpu(...)`：`QnnModelContexts.prepare` 在设备上把 DLC 编译成 context
3. `init(..., backend=QUALCOMM_NPU)`：`Waifu2x.init*` + `initializeBackend`
4. `process(...)`：NCNN/QNN 推理
5. 失败时批处理/单图会回退 Vulkan，并在任务页显示原因

NPU 支持范围（`EngineModels.npuSupports`）：

- Real-CUGAN SE：2x
- Real-CUGAN Pro：2x / 3x
- Real-ESRGAN：2x（动漫/照片）
- SPAN NomosUni Photo：2x
- 其余模型只能用 Vulkan

---

### 4.4 实验室：模型对比测试

入口：设置 → 实验室 → 模型对比测试。

- 选 1 张图，勾选多个模型/倍数，选择 Vulkan / NPU / 两者
- 输出到 `所选文件夹/LumaSR_Test/<yyyyMMdd_HHmmss>/`
- 文件名：`模型_倍数_降噪_风格_后端[_精度].png`
- 同时写 `results.csv`（准备/初始化/解码/推理/编码/总时间、输出尺寸、实际后端、错误）
  和 `device.txt`（SoC/HTP/QNN/FP16/页大小）
- 测试目录 `LumaSR_Test` 会像 `LumaSR_Output` 一样从普通文件夹扫描中排除
- 跑分不能与正式批处理同时进行；测试期间保持 App 前台

模型说明页：设置 → 实验室 → 模型说明，或在对比页点“模型说明”。

### 4.5 性能日志

入口：设置 → 实验室 → 性能日志。

- 每次单图/批量结束自动记录，保留最近 20 次，存在 `filesDir/perf/`
- 按阶段记录 prepare / init / decode / infer / encode / wait / total
- 批量额外记录吞吐、中位、P90、推理与编码合计
- 批量实现了「编码与下一张推理重叠」；超过 8MP 的输出退回串行
- 暂停/取消不会被记成失败，对应项会恢复为未处理
- 页面可「复制全部」，用于反馈或对比改动前后

### 4.6 超大图直接复制

入口：批量参数 → 跳过超分。

- 预设：关闭 / 1080p（1920）/ 2K（2560）/ 4K（3840），按**输入长边**判断
- 命中阈值的图片不进入推理，直接按原文件名、原格式复制到输出目录
- 扫描时用 `BitmapFactory` 只读文件头拿宽高，不解码像素
- 复制项在 `QueueItem.copyOnly`、`OutputRecord.copied` 中标记
- 已处理判断对复制项只看输出文件是否存在，不再比较输出格式
- 对比页会排除复制项，避免拿同一张图做“原图 vs 结果”
- 默认 2K；改成「关闭」即恢复全部超分

### 4.7 预处理分析

入口：选择图片 → 下一步：预处理。

- 「分析项目」逐张读取分辨率，并对需要超分的图片做 512 px 采样
- 清晰度使用拉普拉斯方差估算，分为清晰 / 一般 / 偏模糊，仅供参考
- 报告给出直接复制、需要超分、已处理跳过的数量，以及分辨率和清晰度分布
- 复制项不参与清晰度采样，避免无谓解码
- 修改选择或跳过阈值后，旧报告会自动作废，需要重新分析
- 分析结果只保存在内存，不写盘

### 4.8 统一导出文件夹

- 设置 → 导出文件夹，用系统选择器指定一个固定目录，授权会持久保存
- 指定后输出到 `导出根/<源文件夹名>/…`；未指定时仍是 `源文件夹/LumaSR_Output`
- 扫描、已处理判断、复制项、任务页与性能日志都使用同一输出位置
- 导出目录不能是源文件夹本身或其子目录，设置时会拦截
- 更换导出目录后，已处理状态以新目录为准

### 4.9 视觉与主题

- 浅色暖纸主题：背景 `#F5F0E6`，卡片 `#FFFDF8`，强调色为赤陶 `#B4552F`
- 页面大标题使用系统衬线体，正文保持无衬线；任务页与设置页共用同一套颜色 token
- 底栏为浅色表面 + 顶部发丝线，选中项用 `primaryContainer` 胶囊标识
- 状态栏与导航栏统一使用深色图标，避免浅色背景上不可见

---

## 5. NPU 排查记录（重要）

测试环境（匿名化）：

```text
SoC: 骁龙 8 至尊版
Android 17 (SDK 37)
ABI: arm64-v8a
HTP: v79
QNN runtime: true
NPU 可用: true
FP16: true
页大小: 4096 B
```

### 已确认问题 1：QNN `14001`

现象：

```text
Prepare: FAILED
错误: QNN prepare: QNN contextCreate failed: 14001
```

`14001` 是 QNN HTP 创建 device 失败，不是模型、FP16 或 16 KB 页问题。

根因：

- Android 12+ 不允许应用命名空间直接 `dlopen` 厂商库
- QNN HTP 通过 FastRPC 访问 Hexagon DSP，需要 `libcdsprpc.so`
- 之前 `AndroidManifest.xml` 没有声明 `uses-native-library`

修复（0.3.3）：

```xml
<application ...>
    <uses-native-library android:name="libcdsprpc.so" android:required="false" />
    <uses-native-library android:name="libadsprpc.so" android:required="false" />
    ...
</application>
```

同时诊断报告新增：

- HTP skel 文件是否存在、大小
- `System.loadLibrary("cdsprpc")` 是否成功
- `nativeLibraryDir`

**注意**：`uses-native-library` 必须是 `<application>` 的子元素，放在 `<manifest>` 下会 AAPT 报错。

### 已确认问题 2：16 KB page（当前设备没有触发）

- `libQnnHtpV*Skel.so` 的 ELF `p_align` 是 `0x1000`（4 KB）
- 其他主库是 `0x4000`（16 KB）
- 如果设备页大小是 16 KB，skel 可能无法加载
- 当前设备页大小 4096 B，所以不是本次 14001 的原因
- 后续如果换 16 KB 设备，需要换 16 KB 对齐的 QNN skel，或者接受 NPU 不可用

### 诊断入口

设置 → **NPU 诊断** → **运行 NPU 自检** → **复制报告**

自检会执行：

```text
prepare → init → 64x64 推理
```

报告包含：设备/SoC/HTP/QNN/FP16/页大小/模型/QNN 模型/精度/Skel/FastRPC/Init/实际 NPU/错误。

### 日志

```bash
adb logcat -c
adb logcat -s QnnModelContexts Waifu2x QualcommHtp LumaSR
```

---

## 6. 修改约定

1. 改版本时同步：
   - `app/build.gradle.kts` 的 `versionCode` / `versionName`
   - 设置页底部版本文案
   - `docs/PLAN.md`
2. 先编译再打包：

   ```bash
   ./gradlew :app:compileReleaseKotlin
   ```

3. 不要重命名 `engine97` 的 JNI 类/包。
4. 不要随意打开 R8；如果打开，确认 `proguard-rules.pro` 保留 JNI 类名和 native 方法。
5. 不要改 `useLegacyPackaging`。
6. `QueueState` / `QueueOptions` 新增字段必须给默认值，保证旧 JSON 能反序列化。
7. 目录/输出规则改动时，同时更新：
   - `FolderScanner`
   - `OutputIndex`
   - `AppViewModel.isProcessed`
   - `AppViewModel.startBatch`
8. 修改 NPU 逻辑时，同时检查：
   - `Engine97`
   - `Waifu2x.initializeQnnIfAvailable`
   - `BatchWorker` 回退
   - 单图回退
9. UI 文案用中文，代码注释可以中英混合，保持简洁。

---

## 7. 测试清单

### 基础

- [ ] 单图：选图 → 推理 → 对比 → 保存相册
- [ ] 文件夹：选目录 → 扫描数量正确
- [ ] 递归开/关
- [ ] 保留子目录开/关
- [ ] 网格筛选：全部/未处理/已处理/大图
- [ ] 全选/反选/排序
- [ ] 批处理开始 → 任务页进度
- [ ] 暂停 → 继续
- [ ] 失败重试
- [ ] 完成后打开输出文件夹
- [ ] 对比页上一张/下一张

### 输出

- [ ] `源/LumaSR_Output` 创建正确
- [ ] 保留结构时子目录镜像正确
- [ ] 平铺时重名 `_2` 后缀正确
- [ ] 再次扫描能正确标记已处理
- [ ] 切换 PNG/WebP/JPEG 后重新处理
- [ ] 0 字节输出不会被标记为已处理

### NPU

- [ ] 设置页显示 HTP / QNN / FP16 / 页大小
- [ ] NPU 诊断：Prepare / Init / 64x64 推理
- [ ] NPU 失败时自动回退 Vulkan
- [ ] 任务页显示实际后端和回退原因
- [ ] 切换模型后不会复用旧 QNN context

---

## 8. 构建与打包

- 构建：`scripts/build.sh`
- 源码交接包：`scripts/package-source.sh`

---

## 9. 已知限制 / 下一步

- [x] 真机验证：FastRPC 修复后 Prepare / Init / 推理全通
- [x] 批量性能：编码与下一张推理重叠，日志可查各阶段耗时
- [ ] 16 KB page 设备的 QNN skel 兼容
- [ ] APK 223 MB：可做设备专用包，删掉用不到的 HTP skel
- [ ] 未包含 MediaTek NPU 后端
- [ ] 模型下载/导入、按目录记忆参数
- [ ] 只有少量骁龙 8 系机型做过完整回归，其他机型未测

---

## 10. 给后续维护者的提示

- 先读 `README.md`、`docs/MODELS.md` 和本文件。
- 不要 `clean`；用 `scripts/build.sh` 增量构建。
- 改完先 `:app:compileReleaseKotlin`，再 `scripts/build.sh`。
- 涉及 NPU 时，保持“可诊断、可回退”，不要吞异常。
- 不要伪造真机结果；没有设备就把编译验证和待测项写清楚。
