import os
import random
import socket
import time
import re
import requests
import concurrent.futures

# Cloudflare IPv4 Ranges
CF_CIDRS = [
    "104.16.0.0/13", "104.22.0.0/16", "104.23.0.0/16", "162.152.0.0/13",
    "162.158.0.0/16", "162.159.0.0/16", "172.64.0.0/13", "172.68.0.0/16",
    "172.69.0.0/16", "172.70.0.0/16", "172.71.0.0/16"
]

def generate_random_ip():
    cidr = random.choice(CF_CIDRS)
    base_ip, prefix = cidr.split('/')
    prefix = int(prefix)
    
    parts = list(map(int, base_ip.split('.')))
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

def test_ip(ip, port=443, timeout=2.0):
    start_time = time.time()
    try:
        with socket.create_connection((ip, port), timeout=timeout) as sock:
            connect_time = int((time.time() - start_time) * 1000)
            
            # Send HTTP Request simulating the Android ScannerEngine
            request = (
                "GET /cdn-cgi/trace HTTP/1.1\r\n"
                "Host: speed.cloudflare.com\r\n"
                "User-Agent: CFAutoSync/1.0\r\n"
                "Connection: close\r\n\r\n"
            )
            sock.sendall(request.encode('utf-8'))
            
            response = sock.recv(4096).decode('utf-8', errors='ignore')
            
            # Basic validation like in Kotlin ScannerEngine
            if "cloudflare" in response.lower() and ("plain HTTP request" in response or "400 Bad Request" in response):
                cf_ray_match = re.search(r"CF-RAY:\s*[a-zA-Z0-9]+-([A-Z]{3})", response)
                colo = cf_ray_match.group(1) if cf_ray_match else "UNK"
                return {"ip": ip, "latency": connect_time, "colo": colo}
    except Exception:
        pass
    return None

def sync_to_cloudflare(api_token, zone_id, target_domain, best_ips):
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
        
        # 1. Delete records that are no longer in our best_ips list
        for ip_val, record_id in existing_map.items():
            if ip_val not in desired_ips:
                print(f"Deleting outdated IP: {ip_val}")
                del_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records/{record_id}"
                requests.delete(del_url, headers=headers)
                
        # 2. Add new IPs
        for ip_val in desired_ips:
            if ip_val not in existing_map:
                print(f"Adding new IP: {ip_val}")
                post_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records"
                data = {
                    "type": "A",
                    "name": target_domain,
                    "content": ip_val,
                    "ttl": 60,  # Auto/1 minute
                    "proxied": False
                }
                requests.post(post_url, headers=headers, json=data)
                
        print("Cloudflare DNS Sync completed successfully!")
        return True
    except Exception as e:
        print(f"Exception during Cloudflare sync: {e}")
        return False

def main():
    api_token = os.environ.get("CF_API_TOKEN")
    zone_id = os.environ.get("CF_ZONE_ID")
    target_domain = os.environ.get("CF_TARGET_DOMAIN")
    sync_count = int(os.environ.get("SYNC_COUNT", 5))
    scan_count = int(os.environ.get("SCAN_COUNT", 1000))
    
    if not all([api_token, zone_id, target_domain]):
        print("Error: Missing required environment variables (CF_API_TOKEN, CF_ZONE_ID, CF_TARGET_DOMAIN).")
        print("Please configure them in GitHub Secrets.")
        exit(1)
        
    print(f"Generating {scan_count} random Cloudflare IPs...")
    ips_to_test = [generate_random_ip() for _ in range(scan_count)]
    
    print("Testing IPs concurrently...")
    valid_ips = []
    
    # Use ThreadPool to scan fast
    with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
        futures = {executor.submit(test_ip, ip): ip for ip in ips_to_test}
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            if result:
                valid_ips.append(result)
                
    if not valid_ips:
        print("No valid IPs found in this scan. Aborting sync.")
        exit(1)
        
    # Sort by latency (lowest first)
    valid_ips.sort(key=lambda x: x["latency"])
    
    best_ips = valid_ips[:sync_count]
    print("\n--- Top IPs Selected ---")
    for ip in best_ips:
        print(f"IP: {ip['ip']:<15} | Latency: {ip['latency']:>3}ms | Colo: {ip['colo']}")
        
    print("\nStarting Cloudflare DNS Sync...")
    sync_to_cloudflare(api_token, zone_id, target_domain, best_ips)

if __name__ == "__main__":
    main()
