import json
import urllib.request
import sys

def check():
    path = 'modrinth.index.json'
    print(f"Reading {path}...")
    try:
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception as e:
        print(f"FAILED TO READ JSON: {e}")
        return

    files = data.get('files', [])
    print(f"Found {len(files)} files.")
    
    failures = 0
    for i, f_obj in enumerate(files):
        p = f_obj.get('path', 'unknown')
        urls = f_obj.get('downloads', [])
        expected_size = f_obj.get('fileSize', 0)
        
        if not urls:
            print(f"[{i+1}] NO URL: {p}")
            failures += 1
            continue
            
        url = urls[0]
        try:
            req = urllib.request.Request(url, method='HEAD')
            with urllib.request.urlopen(req, timeout=15) as resp:
                actual_size = int(resp.headers.get('Content-Length', 0))
                if resp.status == 200:
                    if actual_size == expected_size:
                        print(f"[{i+1}] OK: {p}")
                    else:
                        print(f"[{i+1}] SIZE MISMATCH: {p} (Exp: {expected_size}, Got: {actual_size})")
                        failures += 1
                else:
                    print(f"[{i+1}] STATUS {resp.status}: {p} URL: {url}")
                    failures += 1
        except Exception as e:
            print(f"[{i+1}] ERROR: {p} - {e} URL: {url}")
            failures += 1
            
    if failures == 0:
        print("ALL FILES VERIFIED SUCCESSFULLY.")
    else:
        print(f"VERIFICATION COMPLETED WITH {failures} FAILURES.")

if __name__ == '__main__':
    check()
