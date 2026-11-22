# 模型清单

引擎来自 [HaoweiLi97/mihon_img_upscale](https://github.com/HaoweiLi97/mihon_img_upscale) v1.3.23。
模型全部打包在 `engine97/src/main/assets/`，无需运行时下载。

## 1. Vulkan GPU 模型（NCNN）

| 家族 | 倍数 | 资产目录 | 来源 |
|---|---|---|---|
| Real-CUGAN SE | 2/3/4x | `realcugan-models` | nihui/realcugan-ncnn-vulkan |
| Real-CUGAN Pro | 2/3x | `realcugan-pro-models` | nihui/realcugan-ncnn-vulkan |
| Real-CUGAN Nose | 2x | `waifu2x-models-nose` | nihui/realcugan-ncnn-vulkan |
| Real-ESRGAN Anime | 2/3/4x | `realesrgan-models/v3-anime` | xinntao/Real-ESRGAN |
| Real-ESRGAN Photo | 2x 输出 | `realesrgan-models/v3-general` | xinntao/Real-ESRGAN |
| Waifu2x | 2x, noise 0-3 | `waifu2x-models` | nagadomi/waifu2x |
| Waifu2x Fast | 2x, noise 0-3 | `waifu2x-models-upconv7` | nagadomi/waifu2x |
| AnimeJaNai v2 UltraCompact | 2x | `animejanai-ncnn-vulkan/animejanai-v2-ultra-compact-x2` | the-database |
| sudo UltraCompact | 2x | `sudo-ultracompact/2x-sudo-UltraCompact` | OpenModelDB |
| SPAN NomosUni Photo | 2x | `span-nomosuni/2x-NomosUni-SPAN-multijpg-ldl` | Philip Hofmann |

Real-CUGAN 每个倍数包含 no-denoise / denoise1x / 2x / 3x / conservative 等变体；
Real-CUGAN Pro 只有 no-denoise / denoise3x / conservative。

## 2. Qualcomm NPU 模型（QNN / HTP）

> 以下运行时库与 DLC 不随公开仓库分发，需自行用 QAIRT 2.49.0 获取和生成，放入对应目录后再构建。

15 个 FP16 DLC + 1 个 INT8 W8A8 DLC，位于 `engine97/src/main/assets/qnn-models/`：

| DLC | 精度 |
|---|---|
| realcugan-se-x2-{no-denoise,denoise1x,denoise2x,denoise3x,conservative} | FP16 |
| realcugan-pro-x2-{no-denoise,denoise3x,conservative} | FP16 |
| realcugan-pro-x3-{no-denoise,denoise3x,conservative} | FP16 |
| realesrgan-animevideov3-x2 | FP16 |
| realesrgan-general-x4v3-x2 | FP16 |
| span-nomosuni-x2 | FP16 |
| realesrgan-animevideov3-x2-w8a8 | INT8 W8A8 |

首次使用某个 NPU 模型时，App 会把 DLC 编译成当前设备 HTP 的 context，
缓存在 `noBackupFilesDir/qnn-contexts/`。编译由 `libQnnHtpPrepare.so` 完成，
推理由 `libQnnHtp.so` + `libQnnHtpV{69,73,75,79,81}Skel.so` 完成。

## 3. 运行时库

| 库 | 说明 |
|---|---|
| `libwaifu2x-jni.so` | 预编译引擎（NCNN + QNN + 分块推理 + 着色器） |
| `libQnnHtp.so` | QNN HTP 后端 |
| `libQnnHtpPrepare.so` | 设备端 DLC → context 编译 |
| `libQnnHtpV*Skel.so` / `Stub.so` | 各代 Hexagon HTP |
| `libQnnModelDlc.so` | DLC 组合 |
| `libQnnSystem.so` | QNN System API |

## 4. 许可

- Real-ESRGAN / Real-ESRNet：BSD-3-Clause，Copyright (c) 2021 Xintao Wang
- Real-CUGAN：MIT，Copyright (c) 2021 nihui
- Waifu2x：MIT，Copyright (c) 2017 nagadomi
- AnimeJaNai / sudo：CC BY-NC-SA 4.0，仅限非商业使用
- SPAN NomosUni：CC BY 4.0
- NCNN：BSD-3-Clause，Copyright (c) 2017 Tencent
- Mihon Upscale 引擎：Apache-2.0
- Qualcomm QNN 运行时：Qualcomm 许可，不随仓库分发

APK 内保留了各模型的 `ATTRIBUTION.txt`。
