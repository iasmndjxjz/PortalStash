# 构建与验证记录（0.4.0）

本文件只记录**本次构建实际执行过**的检查。未执行的项目明确写为未验证。

## 产物

| 文件 | 说明 |
| --- | --- |
| `PortalStash-LSP-0.4.0-os4-experimental.apk` | 已签名实验包（见 Release） |
| `PortalStash-LSP-0.4.0-os4-source.zip` | 纯源码归档（不含厂商 APK、反编译产物、工具二进制、签名私钥） |

- APK SHA256：`6075b5670a2624804ddcb5116c8f5c2910cf7451d64ded0f6872ab00778dce2c`
- `versionCode=4`，`versionName=0.4.0-os4-experimental`
- `minSdk=28`，`targetSdk=35`
- 签名方案：APK Signature Scheme v3（单签名者）

## 已执行

1. **编译**：`javac --release 8` → D8（min-api 28）→ aapt 打包 → zipalign → apksigner。仅有 JDK 关于 source/target 8 过时与一处 deprecated API 的警告。
2. **签名一致性**：0.4.0 与 0.3.0 的证书指纹逐字节比对一致，可作为同签名升级包。
3. **存档检查**：
   - `assets/xposed_init` 指向 `io.github.portalstash.HookEntry`
   - DEX 中存在 `HookEntry`、`Motion`、`SystemPluginHook`
   - APK 内不含 `lib/` 原生库
   - 作用域为 `com.miui.contentextension`、`com.android.systemui`、`miui.systemui.plugin`
4. **纯 JVM 检查**：237 项断言通过，覆盖设置默认值与白名单、圆角范围、材质模式取值、弹簧数值的有限性与收敛。

```text
PASS 237 pure JVM configuration checks; Android behavior NOT tested.
```

5. **源码归档检查**：不包含 `research/`、`decompiled/`、`tools/`、`*.apk`、`*.keystore`。

## 真机验证（维护者自测）

维护者已在澎湃 OS 4 真机安装该 Release 中的同一 APK 并注入，确认以下流程可用：

- 长按传送门后出现新增的「暂存」入口，原「识别文字 / 识别图片」入口不受影响
- 点击暂存后保存成功，入口切换为保存图标与「暂存栏」
- 暂存栏可展开，文字与图片正确显示
- 内容可长按拖出，可上滑移除

## 未执行（不要当作已验证）

- 未系统测量动画帧率、掉帧或手势误触率。
- 未逐项确认原生玻璃在不同机型上是否真正显示（接口调用成功不代表材质生效）。
- 未验证系统界面组件材质的实际效果，也未测试与 HyperLight 等模块的共存。
- 未测试横竖屏、折叠屏、多用户、锁屏恢复、不同 LSPosed 分支。

## 静态分析依据

系统界面组件 `miui.systemui.plugin` 18.3.2.22.0，SHA256 `22d1ff62878c9efdbb617d49f8c2852ba51aeed0208f4108141d126bad5b87dc`。

只读分析确认了 `MiBackgroundStyle` 的材质选择、`setMiGlass` / `setMiViewMaterialType` / `setMiGlassBlurRadius` 调用、`ViewOutlineProvider` 圆角裁切实现，以及弹簧参数（damping 0.82 / 0.9，response 0.4 / 0.3）。这些值用于本项目**独立实现**的动效，未移植其完整 Folme 引擎。

传送门 `com.miui.contentextension` 5.1.5，SHA256 `6339920fcf8c61e7e060d24aea9dc80add1de769b80ea5827370606604c6b9ae`。JADX 反编译有 6 处错误，相关入口与生命周期方法已人工核对。
