import os
import random
import socket
import time
import re
import requests
import concurrent.futures
from datetime import datetime, timedelta, timezone

# ==========================================
# 🎯 全局默认地区设置
# ==========================================
DEFAULT_REGIONS = "HKG,NRT,SJC,LAX"

# ==========================================
# 🚫 大陆被封段黑名单（珠海电信 2026-08 实测）
# 这些段从国内访问 TCP 握手必超时，海外扫描器会误判为可用
# ==========================================
BLOCKED_PREFIXES = [
    "162.158.", "162.159.",
    "172.64.", "172.69.", "172.70.", "172.71.",
    "108.162.", "173.245.",
]

# ✅ 大陆已知活段白名单（珠海电信 2026-08 本地实测）
LIVE_PREFIXES = [
    "104.16.", "104.17.", "104.18.", "104.19.", "104.20.", "104.21.",
    "172.66.",
]

def is_blocked_ip(ip_str):
    """检查 IP 是否在大陆被封段"""
    for prefix in BLOCKED_PREFIXES:
        if ip_str.startswith(prefix):
            return True
    return False

def is_live_ip(ip_str):
    """检查 IP 是否在大陆已知活段"""
    for prefix in LIVE_PREFIXES:
        if ip_str.startswith(prefix):
            return True
    return False

# ==========================================
# Cloudflare IPv4 Ranges
# ==========================================
def load_cf_cidrs(file_path="ip.txt"):
    if not os.path.exists(file_path):
        print(f"Error: 找不到 {file_path} 文件！")
        exit(1)
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            cidrs = [line.strip() for line in f if line.strip() and not line.strip().startswith("#")]
        if not cidrs:
            print(f"Error: {file_path} 文件为空！")
            exit(1)
        return cidrs
    except Exception as e:
        print(f"Error: 读取 {file_path} 失败！{e}")
        exit(1)

CF_CIDRS = load_cf_cidrs()

def generate_random_ip(hot_cidrs=None):
    for _ in range(10):
        try:
            if hot_cidrs and random.random() < 0.5:
                cidr = random.choice(hot_cidrs)
            else:
                cidr = random.choice(CF_CIDRS)

            if '/' in cidr:
                base_ip, prefix = cidr.split('/')
                prefix = int(prefix)
            else:
                base_ip = cidr
                prefix = 32

            parts = list(map(int, base_ip.split('.')))
            if len(parts) != 4:
                continue

            ip_long = (parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8) | parts[3]
            host_bits = 32 - prefix
            mask = (1 << host_bits) - 1
            random_host = random.randint(0, mask)
            final_ip_long = (ip_long & ~mask) | random_host

            p1 = (final_ip_long >> 24) & 255
            p2 = (final_ip_long >> 16) & 255
            p3 = (final_ip_long >> 8) & 255
            p4 = final_ip_long & 255
            return f"{p1}.{p2}.{p3}.{p4}"
        except Exception:
            continue
    return "1.1.1.1"

def test_ip(ip, check_api_url, timeout=5.0):
    # 🚫 先检查是否在被封段（不浪费 API 请求）
    if is_blocked_ip(ip):
        return None

    start_time = time.time()
    try:
        url = f"{check_api_url}?proxyip={ip}"
        resp = requests.get(url, timeout=timeout).json()
        if resp.get("success") is True:
            connect_time = int((time.time() - start_time) * 1000)
            colo = resp.get("dataCenter") or resp.get("colo") or resp.get("country") or "UNK"
            latency = resp.get("latencyMs") or resp.get("tcpDuration") or connect_time
            return {"ip": ip, "latency": latency, "colo": colo}
    except Exception:
        pass
    return None

def sync_to_cloudflare(api_token, zone_id, target_domain, best_ips, cf_email):
    headers = {
        "Authorization": f"Bearer {api_token}",
        "Content-Type": "application/json"
    }
    url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records?type=A&name={target_domain}"

    print(f"Fetching existing DNS records for {target_domain}...")
    try:
        resp = requests.get(url, headers=headers).json()
        if not resp.get("success"):
            print("Failed to fetch DNS records:", resp)
            return False

        existing_records = resp.get("result", [])
        existing_map = {r["content"]: r["id"] for r in existing_records}
        desired_ips = [ip["ip"] for ip in best_ips]

        for ip_val, record_id in existing_map.items():
            if ip_val not in desired_ips:
                print(f"Deleting outdated IP: {ip_val}")
                del_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records/{record_id}"
                requests.delete(del_url, headers=headers)

        for ip_val in desired_ips:
            if ip_val not in existing_map:
                print(f"Adding new IP: {ip_val}")
                post_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records"
                data = {
                    "type": "A",
                    "name": target_domain,
                    "content": ip_val,
                    "ttl": 60,
                    "proxied": False
                }
                requests.post(post_url, headers=headers, json=data)

        print("Cloudflare DNS Sync completed successfully!")
        return True
    except Exception as e:
        print(f"Exception during Cloudflare sync: {e}")
        return False

def save_ips_to_file(best_ips):
    bj_time = datetime.now(timezone.utc) + timedelta(hours=8)
    time_str = bj_time.strftime("%Y-%m-%d %H:%M:%S")

    with open("ips-v4.txt", "w", encoding="utf-8") as f:
        for ip in best_ips:
            f.write(f"{ip['ip']}#{ip['colo']}\n")

    print(f"Successfully saved {len(best_ips)} IPs to ips-v4.txt")

def load_hot_cidrs():
    """从上次结果中学习热点段，但先过滤掉封段"""
    hot_cidrs = []
    if os.path.exists("ips-v4.txt"):
        try:
            with open("ips-v4.txt", "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line:
                        ip_str = line.split("#")[0]
                        # 🚫 跳过被封段的 IP
                        if is_blocked_ip(ip_str):
                            continue
                        parts = ip_str.split(".")
                        if len(parts) == 4:
                            hot_cidrs.append(f"{parts[0]}.{parts[1]}.{parts[2]}.0/24")
            hot_cidrs = list(set(hot_cidrs))
            print(f"Loaded {len(hot_cidrs)} hot /24 subnets (blocked segments filtered out).")
        except Exception:
            pass
    return hot_cidrs

def clean_existing_results():
    """清理 ips-v4.txt 中的封段 IP"""
    if not os.path.exists("ips-v4.txt"):
        return 0

    with open("ips-v4.txt", "r", encoding="utf-8") as f:
        lines = f.readlines()

    cleaned = []
    removed = 0
    for line in lines:
        line = line.strip()
        if not line:
            continue
        ip_str = line.split("#")[0]
        if is_blocked_ip(ip_str):
            removed += 1
            continue
        cleaned.append(line)

    if removed > 0:
        with open("ips-v4.txt", "w", encoding="utf-8") as f:
            f.write("\n".join(cleaned) + "\n")
        print(f"🧹 Cleaned {removed} blocked-segment IPs from ips-v4.txt")

    return removed

def main():
    api_token = os.environ.get("CF_API_TOKEN")
    zone_id = os.environ.get("CF_ZONE_ID")
    base_domain = os.environ.get("CF_TARGET_DOMAIN")
    cf_email = os.environ.get("CF_EMAIL")

    region_input = DEFAULT_REGIONS
    target_regions = [r.strip().upper() for r in region_input.split(",") if r.strip()]
    is_scan_all = "ALL" in target_regions

    if is_scan_all:
        print(f"Target Regions: ALL (Global Scan Mode)")
    else:
        print(f"Target Regions: {target_regions}")

    # 🚫 启动时先清理旧结果中的封段 IP
    clean_existing_results()

    check_api_url = "https://proxyip.xxxxxxxx.nyc.mn/check"
    sync_count = int(os.environ.get("SYNC_COUNT", 10))
    scan_count = int(os.environ.get("SCAN_COUNT", 2000))

    # ✅ 热点段加载时已过滤封段
    hot_cidrs = load_hot_cidrs()

    can_sync = True
    if not all([api_token, zone_id, base_domain, cf_email]):
        print("Warning: Missing CF credentials. DNS Sync skipped.")
        can_sync = False

    print(f"Generating {scan_count} random Cloudflare IPs...")
    ips_to_test = [generate_random_ip(hot_cidrs) for _ in range(scan_count)]

    print(f"Testing IPs concurrently via {check_api_url}...")
    print(f"🚫 Blocked prefixes: {', '.join(BLOCKED_PREFIXES)}")

    valid_ips_by_region = {}
    if not is_scan_all:
        valid_ips_by_region = {region: [] for region in target_regions}

    max_attempts = 5
    attempt = 0
    ALL_MODE_LIMIT = 20
    blocked_count = 0

    while attempt < max_attempts:
        total_collected = sum(len(ips) for ips in valid_ips_by_region.values())
        if is_scan_all and total_collected >= ALL_MODE_LIMIT:
            break
        elif not is_scan_all and all(len(ips) >= sync_count for ips in valid_ips_by_region.values()):
            break

        attempt += 1
        print(f"--- Scan Iteration {attempt} ---")
        ips_to_test = [generate_random_ip(hot_cidrs) for _ in range(scan_count)]

        with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
            futures = {executor.submit(test_ip, ip, check_api_url): ip for ip in ips_to_test}
            for future in concurrent.futures.as_completed(futures):
                result = future.result()
                if result:
                    colo = result.get('colo', 'UNK').upper()
                    if colo != 'UNK' and (is_scan_all or colo in target_regions):
                        if colo not in valid_ips_by_region:
                            valid_ips_by_region[colo] = []

                        if is_scan_all:
                            total_collected = sum(len(ips) for ips in valid_ips_by_region.values())
                            if total_collected < ALL_MODE_LIMIT:
                                valid_ips_by_region[colo].append(result)
                                live_tag = "✅LIVE" if is_live_ip(result['ip']) else "⚠️UNKNOWN"
                                print(f"[FOUND {colo}] {result['ip']} ({live_tag}) (Total: {total_collected + 1}/{ALL_MODE_LIMIT})")
                        else:
                            if len(valid_ips_by_region[colo]) < sync_count:
                                valid_ips_by_region[colo].append(result)
                                live_tag = "✅LIVE" if is_live_ip(result['ip']) else "⚠️UNKNOWN"
                                print(f"[FOUND {colo}] {result['ip']} ({live_tag}) (Total {colo}: {len(valid_ips_by_region[colo])}/{sync_count})")

                total_collected = sum(len(ips) for ips in valid_ips_by_region.values())
                if is_scan_all and total_collected >= ALL_MODE_LIMIT:
                    executor.shutdown(wait=False, cancel_futures=True)
                    break
                elif not is_scan_all and all(len(ips) >= sync_count for ips in valid_ips_by_region.values()):
                    executor.shutdown(wait=False, cancel_futures=True)
                    break

    print("\nScan completed. Summary:")
    total_found = 0
    all_best_ips = []

    for region, ips in valid_ips_by_region.items():
        print(f"- {region}: {len(ips)} valid IPs found")
        if not ips:
            print(f"  Warning: No IPs found for {region}")
            continue

        total_found += len(ips)
        ips.sort(key=lambda x: x["latency"])
        limit = ALL_MODE_LIMIT if is_scan_all else sync_count
        best_ips = ips[:limit]
        all_best_ips.extend(best_ips)

        print(f"\n--- Top {len(best_ips)} IPs for {region} ---")
        for ip in best_ips:
            live_tag = "✅" if is_live_ip(ip['ip']) else "⚠️"
            print(f"  {live_tag} {ip['ip']:<15} | {ip['latency']:>3}ms | {ip['colo']}")

        if can_sync:
            target_domain = f"{region.lower()}.{base_domain}"
            print(f"\nDNS Sync → {target_domain}...")
            sync_to_cloudflare(api_token, zone_id, target_domain, best_ips, cf_email)

    if can_sync and all_best_ips:
        all_best_ips.sort(key=lambda x: x["latency"])
        print(f"\n[Global] DNS Sync → {base_domain}")
        sync_to_cloudflare(api_token, zone_id, base_domain, all_best_ips, cf_email)

    if total_found == 0:
        print("No valid IPs found. Aborting.")
        exit(1)

    if all_best_ips:
        save_ips_to_file(all_best_ips)

if __name__ == "__main__":
    main()
