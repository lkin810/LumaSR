# LumaSR · 光影超分

基于 [HaoweiLi97/mihon_img_upscale](https://github.com/HaoweiLi97/mihon_img_upscale)（Mihon Upscale v1.3.23）的图像增强引擎，
重做的**独立本地图片超分 App**。支持 GPU 和高通骁龙 NPU 两种加速方式，
界面采用**文件夹优先**的批处理流程。

## 功能

- **文件夹优先批量**：选择文件夹 → 自动递归扫描 → 网格筛选/全选/反选 → 统一参数 → 后台处理
- **可预测的输出**：自动写入 `源文件夹/LumaSR_Output`，可选保留子目录结构，支持跳过已处理
- **两种加速方式**：GPU 支持全部模型；骁龙 NPU 支持部分模型，速度更快、更省电
- **12 个模型家族、30+ 变体**：Real-CUGAN SE/Pro/Nose、Real-ESRGAN、Waifu2x、
  W2xEX、AnimeJaNai、sudo、SPAN NomosUni
- **单图工作流**：选图 → 模型/倍数/降噪/后端/精度 → 推理 → 拖动对比 → 保存到相册
- **任务与结果**：总进度、当前文件、逐项状态、暂停/继续/重试失败、结果预览
- **连续对比**：批量结果可上一张/下一张，滑杆、双指缩放、1:1、切换原图
- **设置页**：默认后端/模型、输出规则、设备信息、模型管理、通知与隐私
- **实验室**：模型对比测试（同一张图跑多个模型/后端，输出 `LumaSR_Test` + CSV）与模型说明页
- **大图保护**：按最大输出像素自动降采样输入
- **完全离线**：所有模型打包进 APK，不依赖运行时下载

## 哪些设备能用

- Android 10 及以上
- 64 位 ARM 手机（arm64-v8a）
- GPU：支持 Vulkan 的 Adreno / Mali
- NPU（可选）：高通骁龙 8 系；没有 NPU 也能用 GPU 正常跑

### NPU 支持范围

| 骁龙芯片 | 大致对应 |
|---|---|
| 骁龙 8 Gen 1 / 8+ Gen 1 | 2021–2022 年旗舰 |
| 骁龙 8 Gen 2 | 2022–2023 年旗舰 |
| 骁龙 8 Gen 3 | 2023–2024 年旗舰 |
| 骁龙 8 至尊版 | 2024–2025 年旗舰 |
| 第五代骁龙 8 至尊版 | 2025–2026 年旗舰 |

- 更早的骁龙 888 和 7 系不在支持范围
- 同款芯片也可能因厂商限制无法访问 NPU，需要在自己的手机上实测确认
- 首次使用 NPU 会编译模型，需要等几十秒，之后会缓存
- NPU 不可用时会自动改用 GPU，并在任务页说明原因

支持 NPU 的模型（其他模型只能用 GPU，不影响正常使用）：

- Real-CUGAN SE 2x（全部降噪档位）
- Real-CUGAN Pro 2x / 3x
- Real-ESRGAN 动漫 2x、照片 2x
- W2xEX Photo Small 2x
- SPAN NomosUni Photo 2x

## 模型

| 模型家族 | 倍数 | 降噪 | NPU |
|---|---|---|---|
| Real-CUGAN SE | 2/3/4x | 无/1/2/3/保守 | 2x 全档 |
| Real-CUGAN Pro | 2/3x | 无/3/保守 | 2x/3x |
| Real-CUGAN Nose | 2x | 无 | — |
| Real-ESRGAN（动漫/照片） | 2/3/4x；照片 2x | — | 2x |
| Waifu2x / Fast | 2x | 0-3 | — |
| W2xEX Universal / Omni | 2x | — | — |
| W2xEX Photo Small | 2x | — | 2x |
| AnimeJaNai v2 UltraCompact | 2x | — | — |
| sudo UltraCompact | 2x | — | — |
| SPAN NomosUni Photo | 2x | — | 2x |

模型来源与许可见 [docs/MODELS.md](docs/MODELS.md)。

## 构建

要求：JDK 17、Android SDK 35。**不需要 NDK/CMake**，引擎是预编译 `.so`。

常用命令：

```bash
scripts/build.sh            # 增量 Release：产物复制到 dist/ 并生成 SHA-256
scripts/package-source.sh   # 打包源码 + 文档
scripts/deploy.sh           # 作者自用：生成下载页并部署（需自备部署环境）
scripts/verify.sh           # 作者自用：校验线上文件与本地产物一致
```

脚本不会 `clean`；已开启 Gradle 配置缓存和构建缓存，改几行 Kotlin 后增量构建通常几十秒。
`deploy.sh` / `verify.sh` 依赖 Trae 部署插件，只在本机出包时用 `build.sh` 即可。

手动构建：

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleRelease
```

产物默认在 `app/build/outputs/apk/release/app-release.apk`，`scripts/build.sh` 会复制成
`dist/LumaSR-<versionName>.apk`，并生成同名 `.sha256`。

> 当前 Release **关闭了 R8**（`isMinifyEnabled = false`），构建更快、堆栈可读，适合调试和自用。
> 正式分发前在 [app/build.gradle.kts](app/build.gradle.kts) 把 `isMinifyEnabled` / `isShrinkResources`
> 改回 `true`，并确认 [proguard-rules.pro](app/proguard-rules.pro) 的 JNI keep 规则。

> Release lint 已在 [app/build.gradle.kts](app/build.gradle.kts) 关闭，避免 230 MB 包在 CI 小内存容器里
> 被 lint 阶段 OOM；需要时单独运行 `./gradlew :app:lintRelease`。

## 目录结构

```
LumaSR/
├── app/                          # Compose UI、单图/文件夹批量/对比/保存
│   └── src/main/java/io/lumasr/app/
│       ├── engine/Engine97.kt    # 97 引擎门面（GPU/NPU 调度）
│       ├── engine/EngineModels.kt# 12 个模型家族与 NPU 支持表
│       ├── folder/               # SAF 扫描、输出文件夹、格式写入
│       ├── ui/
│       │   ├── home/             # 首页
│       │   ├── folder/           # 文件夹概览 + 网格选择
│       │   ├── config/           # 单图参数 / 批处理参数
│       │   ├── batch/            # 任务与结果
│       │   ├── compare/          # 对比
│       │   ├── settings/         # 设置 + 模型管理
│       │   └── components/       # 通用组件
│       ├── data/                 # 解码 / 相册保存 / 偏好
│       └── batch/                # WorkManager 文件夹队列 + 通知
├── engine97/                     # 97 引擎（预编译 .so + QNN + 模型资产）
│   ├── src/main/jniLibs/arm64-v8a/
│   ├── src/main/assets/          # ncnn 模型 + qnn-models DLC
│   └── src/main/java/eu/kanade/tachiyomi/util/...
├── scripts/                      # build.sh / deploy.sh / verify.sh / 下载页模板
├── dist/                         # 构建产物与 SHA-256（已 gitignore）
└── docs/
```

## 已知限制

- 只在骁龙 8 至尊版机型上验证过，其他芯片未测
- APK 约 223 MB，体积主要来自 NPU 运行时和模型
- 个别采用 16 KB 内存页的新设备可能无法加载 NPU 组件
- 只支持高通骁龙 NPU，不支持联发科
- NPU 模型以 FP16 为主，只有一个 INT8 版本
- 批量任务只支持文件夹入口，所有图片共用一套参数
- 部分文件管理器不支持直接打开输出目录
- “跳过已处理”按“输出文件夹/相对路径.格式”判断同名文件

## 许可与致谢

本项目基于 [HaoweiLi97/mihon_img_upscale](https://github.com/HaoweiLi97/mihon_img_upscale)
（Mihon Upscale v1.3.23，Apache-2.0）的引擎与模型资产，UI 与批处理流程为独立重写。

- 本仓库代码：Apache-2.0，见 [LICENSE](LICENSE)
- 上游引擎 / 预编译 `libwaifu2x-jni.so` / QNN 后端：Apache-2.0，
  来自 [HaoweiLi97/mihon_img_upscale](https://github.com/HaoweiLi97/mihon_img_upscale)
- 上游又基于 Mihon / Tachiyomi（Apache-2.0）；`engine97` 保留原
  `eu.kanade.tachiyomi.util.*` 包名，因为 JNI 是静态注册
- NCNN：BSD-3-Clause
- Real-ESRGAN / Real-ESRNet：BSD-3-Clause
- Real-CUGAN：MIT
- Waifu2x：MIT
- W2xEX / AnimeJaNai / sudo / SPAN：遵循各自作者 / OpenModelDB 许可
- Qualcomm QNN 运行时：Qualcomm 许可，随上游官方 APK 分发

完整第三方声明见 [NOTICE](NOTICE)，模型来源见 [docs/MODELS.md](docs/MODELS.md)。
