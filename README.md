# LumaSR · 光影超分

基于 [HaoweiLi97/mihon_img_upscale](https://github.com/HaoweiLi97/mihon_img_upscale)（Mihon Upscale v1.3.23）的图像增强引擎，
重做的**独立本地图片超分 App**。引擎使用 97 的 **NCNN Vulkan GPU + Qualcomm QNN NPU**
双后端，UI 采用**文件夹优先**的批处理流程。

## 特性

- **文件夹优先批量**：SAF 选目录 → 递归扫描 → 网格筛选/全选/反选 → 一套参数 → 后台处理
- **可预测的输出**：自动写入 `源文件夹/LumaSR_Output`，可选保留子目录结构，支持跳过已处理
- **双后端**：Vulkan GPU（全模型）与 Qualcomm Hexagon NPU（QNN/HTP，FP16，Anime 支持 INT8）
- **12 个模型家族、30+ 变体**：Real-CUGAN SE/Pro/Nose、Real-ESRGAN、Waifu2x、
  W2xEX、AnimeJaNai、sudo、SPAN NomosUni
- **NPU 覆盖 15 个 FP16 变体**：Real-CUGAN SE 2x 五档、Pro 2x/3x 三档、
  Real-ESRGAN 2x、W2xEX Photo Small 2x、SPAN NomosUni 2x
- **单图工作流**：选图 → 模型/倍数/降噪/后端/精度 → 推理 → 拖动对比 → 保存到相册
- **任务与结果**：总进度、当前文件、逐项状态、暂停/继续/重试失败、结果预览
- **连续对比**：批量结果可上一张/下一张，滑杆、双指缩放、1:1、切换原图
- **设置页**：默认后端/模型、输出规则、设备信息、模型管理、通知与隐私
- **实验室**：模型对比测试（同一张图跑多个模型/后端，输出 `LumaSR_Test` + CSV）与模型说明页
- **大图保护**：按最大输出像素自动降采样输入
- **完全离线**：所有模型打包进 APK，不依赖运行时下载

## 当前状态

- `:app` 与 `:engine97` 编译通过，Release APK 已产出（0.4.3-lab，约 223 MB）
- 引擎为 97 官方 APK 的预编译 `libwaifu2x-jni.so` + QNN 运行时 + DLC 资产
- UI v2（文件夹批处理）与 v0.4 实验室已完成，设计稿见 [docs/ui-v2.html](docs/ui-v2.html)
- NPU 14001（FastRPC 未声明）已在 0.3.3 修复，0.4.x 在 SM8750 真机验证通过
- 首测建议：设置 → NPU 诊断 → 运行自检；通过后再用「模型对比测试」跑一张图

## 设备与 NPU 支持

- Android 10+（minSdk 29）
- arm64-v8a
- GPU：支持 Vulkan 的 Adreno / Mali
- NPU：Qualcomm Snapdragon，设备 HTP 架构需为 **v69 / v73 / v75 / v79 / v81** 之一
  （APK 已内置对应 `libQnnHtpV*Skel.so`）
- 精度：FP16；Real-ESRGAN Anime 2x 另有 INT8 版本
- Android 12+ 通过 FastRPC 访问 Hexagon，manifest 已声明
  `libcdsprpc.so` / `libadsprpc.so`

支持 NPU 的模型（未列出的模型自动使用 Vulkan）：

- Real-CUGAN SE 2x（noise 0/1/2/3/保守全档）
- Real-CUGAN Pro 2x / 3x
- Real-ESRGAN Anime 2x、Real-ESRGAN Photo 2x
- W2xEX Photo Small 2x
- SPAN NomosUni Photo 2x

> NPU 模型首次使用会在设备上把 DLC 编译成 HTP context，可能需要几十秒，
> 之后缓存在应用私有目录。NPU 初始化失败会自动回退 Vulkan，并在任务页显示原因。

实测设备：HONOR AAK-AN00（SM8750 / HTP v79 / Android 17）。这只是验证用机，
不代表只支持该机型；其他 HTP 版本未逐一实测。

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
scripts/build.sh     # 增量 Release：自动找 JDK 17，产物复制到 dist/ 并输出 SHA
scripts/deploy.sh    # 自动增量构建，然后生成下载页、部署、校验 URL
scripts/verify.sh    # 只校验线上页面和 APK 是否与 dist/ 一致
scripts/package-source.sh  # 打包完整源码 + 交接文档，生成 dist/*-source.zip
```

脚本不会 `clean`；Gradle 配置缓存、构建缓存、`lint` 关闭都已开好，改几行 Kotlin 后增量构建通常几十秒。
`deploy.sh` 会自动把版本、大小、SHA-256 填进 `scripts/download-page.html`，不需要手改 hash。
`deploy.sh` 需要 Trae 部署插件和已登录环境；只想本地出包时用 `build.sh` 即可。

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

- 真机验证覆盖 HONOR AAK-AN00（SM8750 / HTP v79 / Android 17），其他机型未测
- APK 约 223 MB：QNN HTP Prepare 79 MB + 5 个 HTP skel 约 90 MB + 模型
- QNN 的 `libQnnHtpV*Skel.so` 是 4 KB page 对齐（与官方 APK 一致），16 KB page 设备可能无法加载
- 未包含 MediaTek NPU 后端（LiteRT），只做 Snapdragon NPU
- NPU 模型以 FP16 为主，仅 Real-ESRGAN Anime 2x 有 INT8
- 批量任务只支持文件夹入口，所有图片共用一套参数
- 输出使用 SAF `DocumentFile` 写入，个别文件管理器对目录 Uri 的“打开”支持有限
- “跳过已处理”按 `输出文件夹/相对路径.格式` 判断同名文件

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
