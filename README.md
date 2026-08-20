# Cloudflare IP Auto Scanner & Sync (CF Proxyip 扫描 自动同步器)

这是一个运行在 GitHub Actions 上的全自动 Cloudflare  IP 扫描与 DNS 同步工具。
它会自动生成大量 CF IP 并通过 API 测速挑选出最优的 Proxyip，并自动更新到你的 Cloudflare DNS 记录中。同时具备**自适应热点网段学习**功能，大幅提升扫描效率。

---

## 🚀 部署与配置

### 1. 配置 GitHub Secrets (必须)
要想让脚本正常运行，你需要进入你的 GitHub 仓库：
点击右上角的 **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**，依次添加以下 4 个环境变量：

| Secret 名称 | 说明 | 示例 |
| --- | --- | --- |
| `CF_EMAIL` | 你登录 Cloudflare 的邮箱账号 | `your@email.com` |
| `CF_API_TOKEN` | Cloudflare 的 Global API Key (全局 API 密钥) | `8a9b...` |
| `CF_ZONE_ID` | 你域名的 区域 ID (在 CF 域名概述页面右下角，32位字符串) | `9841...` |
| `CF_TARGET_DOMAIN` | 你希望自动同步更新的子域名 | `us.yourdomain.com` |

配置好后，你可以点击仓库顶部的 **Actions** -> 选中 **Cloudflare IP Auto Scanner & Sync** -> 点击 **Run workflow** 来手动触发一次运行，检查是否正常。

---

## ⚙️ 高级自定义设置

### 🧠 1. 核心亮点：自适应热点网段追踪
系统会自动提取 `ips-v4.txt` 中上次测速成功的优质 IP 生成 `/24` 历史优选网段。
在下一轮扫描生成 IP 时，有 **50% 的概率优先轰炸这些高概率出货的旧区域**，另外 50% 去全网大网段里盲抽，极大加快了扫出神仙 IP 的速度！输出在 `ips-v4.txt` 的结果还会带上地区后缀（如 `1.1.1.1#HKG`），完美兼容各大机场代理客户端。

### 📍 2. 如何修改扫出来的地区？
打开 `scripts/cf_scanner_sync.py`，在代码的大约第 **160 行** 找到 `地区调度配置区`。
当前配置为只保留 `FRA` (法兰克福), `HKG` (香港), `LAX` (洛杉矶), `SJC` (圣何塞) 四大核心地区：
```python
    # === 地区调度配置区 ===
    # 限制只保留这些目标地区的 IP
    target_regions = ["FRA", "HKG", "LAX", "SJC"]
    # ============================================
```
如果你想要加入日本或者换成台湾，直接在数组里添加即可，例如：`["FRA", "HKG", "LAX", "SJC", "NRT", "TPE"]`。

### ⚡ 3. 扫描逻辑与并发设置 (YAML)
在 `.github/workflows/cf-auto-sync.yml` 中，你可以调整环境变量：
- **`SYNC_COUNT`**: 控制最终要同步几个 IP 到 Cloudflare 的 DNS 记录（默认 `10` 个）。
- **`SCAN_COUNT`**: 控制每次生成多少个 IP 去抽卡测速（默认生成 `2000` 个）。

如果你想修改并发数量（默认 `50` 线程），可以在 `scripts/cf_scanner_sync.py` 的 `max_workers=50` 处修改。调高可加快测速，但需注意 API 接口的抗压能力。

### 🌐 4. 如何配置自己想要的 IP 段 (CIDR)？
打开 `scripts/cf_scanner_sync.py`，在文件最顶部你会看到 `IP段配置区`：
```python
    # === Cloudflare IPv4 Ranges (IP段配置区) ===
    # 可以在这里自由增删你想扫描的 CIDR
CF_CIDRS = [
    "104.16.0.0/13", "104.22.0.0/16", "104.23.0.0/16", "162.152.0.0/13",
    "162.158.0.0/16", "162.159.0.0/16", "172.64.0.0/13", "172.68.0.0/16",
    "172.69.0.0/16", "172.70.0.0/16", "172.71.0.0/16"
]
    # ==========================================
```
如果你在网上找到了某些玄学好用的 CF IP段，直接把网段加到这个列表里，程序就会自动去里面随机抽卡测速了。
