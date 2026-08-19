# Cloudflare IP 反代与DNS

这是一款专为 Android 打造的实用网络工具，旨在帮助用户快速扫描、筛选最优的 Cloudflare（或反代）IP，并将这些优质 IP 自动同步到您的 Cloudflare DNS 记录中。

## ✨ 核心功能

* 🔍 **高速 IP 扫描**: 自定义并发线程数、延迟阈值、目标 IP 数量，快速找出延迟最低的优选节点。
* 🎯 **数据中心过滤**: 支持根据机场或数据中心（如 HKG, LAX, SJC）进行精准测速和筛选。
* ☁️ **Worker API 检测**: 支持通过自定义 Cloudflare Worker API 测试 IP 的真实连通性和速度。
* 🔄 **全自动 DNS 同步**: 结合预设规则，在扫描完成后自动将最优 IP 同步至您的 Cloudflare 域名记录，无缝替换失效/高延迟的旧 IP。
* 📦 **本地数据管理**: 自动保存历史扫描记录，支持一键收藏和管理优质 IP。

* <img src="https://i.mji.rip/2026/08/18/89ded85a772dc332bdb5dffeec0bb1c6.jpeg" alt="89ded85a772dc332bdb5dffeec0bb1c6.jpeg" border="0" />

## 📥 下载与安装

本项目配置了全自动的 GitHub Actions CI/CD 流水线，代码推送后会自动打包并发布，无需手动编译。

👉 **[前往 Releases 页面下载最新版](../../releases)**

提供的安装包变体：

* **arm64-v8a** (推荐绝大多数现代手机使用，体积小)
* **armeabi-v7a** (兼容老旧安卓设备)
* **x86 / x86_64** (适用于安卓模拟器)
* **universal** (全架构通用安装包)

## 🛠️ 内嵌 IP 库修改与设置说明

为了保证测速速度和效率，应用的默认测速网段（内嵌 CIDR IP 库）是直接硬编码在核心源码中的。如果您想要新增或修改扫描的网段，请按以下说明进行操作：

**文件路径：**
`app/src/main/java/com/example/scanner/ScannerEngine.kt`

**修改步骤：**
1. 在代码编辑器中打开上述文件。
2. 找到大概在第 21 行左右的 `cfCidrs` 列表，代码如下所示：

```kotlin
    // Some common CF IPv4 ranges
    private val cfCidrs = listOf(
        "104.16.0.0/13",
        "172.64.0.0/13",
        "104.22.0.0/16",
        "172.68.0.0/16",
        "172.69.0.0/16"
        // 您可以在这里按照 "IP/掩码" 的格式继续添加您想要的网段，注意使用双引号和逗号分隔
    )
```

3. 将您想要扫描的 Cloudflare 或反代 IP 的 CIDR 网段添加进该列表。
4. 修改完毕后，保存文件，并点击左侧的 **"Push to GitHub"**。
5. GitHub Actions 会自动开始编译，几分钟后即可在 Releases 页面下载到包含最新 IP 库的变体版本（1.4.x）。
