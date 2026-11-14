# 豆儿

把照片变成拼豆图纸的 Android 应用。导入图片后生成逐格图纸，可手动精修、
按行引导拼装，并导出打印版与采购清单。除检查更新外完全离线；检查更新只访问
Gitee 或 GitHub 发布页及其国内加速节点，不上传任何项目数据。

## 功能

- 照片转图纸：自动去背景，保留眼睛、高光等小面积细节
- 像素画还原：识别放大后的像素画，按画像素还原色块
- 裁剪旋转：拖动、缩放、旋转、镜像、正方形锁定、构图辅助线
- 尺寸预设：按图片比例推导网格，支持自定义宽高与比例锁定
- 色卡：18 套内置色卡，含 24/48/72/96/120/144/216/221/272 全色系套装
- 手动编辑：画笔、橡皮、吸管、油漆桶、撤销、区域锁定
- 画像素：图层、帧动画、选区、渐变、文字、GIF；支持底层导入图片，在上层临摹描画
- 多页图纸：每页独立绘制与锁定，改尺寸时统一缩放
- 参考图对比、色板替换、相似色合并
- 拼装引导：按行或按列高亮，记录进度
- 导出：色号 PNG、符号 PNG、A4 分页 PDF、采购清单、.bpat 工程文件
- 空白画布、文字图纸、项目库、收藏与专辑、深色模式
- 屏幕刷新率：跟随系统 / 60 / 90 / 120 Hz，应用内独立生效
- 应用内检查更新：Gitee / GitHub 加速节点优先，下载后校验 SHA-256 并调起系统安装器
- 生成统计：按创建范式统计图纸数量

## 构建

需要 JDK 17 与 Android SDK（compileSdk 34）。在项目根目录创建
`local.properties`，写入 `sdk.dir=<SDK 路径>`。

```
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew assembleRelease
```

发布签名在根目录 `keystore.properties` 中配置（该文件不纳入版本控制）：

```
storeFile=keystore/release.jks
storePassword=<密码>
keyAlias=<别名>
keyPassword=<密码>
```

更新检查会依次读取 `gitee.com/L_Amireux_P/douer`、GitHub 加速节点和 GitHub 官方
Release；Gitee 镜像在 Release 中上传 APK，并在正文写 `SHA256: <64位摘要>`，
客户端会校验。Gitee 不可用时自动跳过并回退。

## 色卡数据

- MARD / COCO / 漫漫 / 盼盼 / 咪小窝：[Zippland/perler-beads](https://github.com/Zippland/perler-beads)（AGPL-3.0）
- Perler / Hama / Artkal：公开色卡数据整理（Apache-2.0）
- 全色系 24–272 套装：实物色卡整理

色值为工程近似值，与实体豆可能存在色差，请以实物色卡为准。

## 版本

- 1.3：画像素底层图片临摹、点击网格尺寸进画布设置、Gitee / GitHub 双源更新、关于页双仓库地址
- 1.2.4：新增应用内 60 / 90 / 120 Hz 刷新率切换与驻留，完善像素校准、项目库与统计
- 1.2.3：项目库性能、创建与导入流程整理
- 1.2.2：像素图校准、裁剪与编辑器交互修正
- 1.2.1：首个公开版本

## 许可

[AGPL-3.0](LICENSE)
