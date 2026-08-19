# Cloudflare IP Auto Scanner & Sync (CF Proxyip 扫描 自动同步器)

这是一个运行在 GitHub Actions 上的全自动 Cloudflare  IP 扫描与 DNS 同步工具。它会自动生成大量 CF IP 
并通过 API 测速挑选出Proxyip IP（默认只挑选 **10 个 USA 节点**），并自动更新到你的 Cloudflare DNS 记录中。

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

### 🕒 1. 如何修改定时运行时间？
默认配置为 **每天北京时间早上 5:00** 自动运行一次。
如果你想修改时间，请在 GitHub 编辑 `.github/workflows/cf-auto-sync.yml` 文件：
```yaml
on:
  schedule:
    # Cron 表达式使用的是 UTC 时间。北京时间早上 5 点 = UTC 时间 21:00
    - cron: '0 21 * * *'
```
*你可以使用 [crontab.guru](https://crontab.guru/) 来生成你想要的 UTC cron 表达式。*

### 📍 2. 如何修改扫出来的地区和数量？
打开 `scripts/cf_scanner_sync.py`，在代码的大约第 **125 行** 找到 `核心筛选配置区`。
当前的默认配置为收集 10 个 USA 节点：
```python
    # === 核心筛选配置区 (你可以随意修改这里) ===
    # 格式: {"地区代码": 需要收集的数量}。修改这里可以任意增删国家和数量。
    target_regions = {
        "USA": 10
    }
    # ============================================
```
如果你想要加入香港或者换成日本，直接按格式修改即可，例如改回 `{"USA": 10, "HKG": 10}`。
ps：压不根儿没必要，反正是跟着你的优选ip落地走。

*(注意：最终同步到 DNS 的总数量，是由 `.github/workflows/cf-auto-sync.yml` 里的 `SYNC_COUNT: '10'` 决定的)*

### 🌐 3. 如何配置自己想要的 IP 段 (CIDR)？
打开 `scripts/cf_scanner_sync.py`，在文件最顶部（大约第 **10 行**）你会看到 `IP段配置区`：
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
