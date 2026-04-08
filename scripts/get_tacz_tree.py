import urllib.request, json
url = "https://api.github.com/repos/MCModderAnchor/TACZ/git/trees/1.20.1?recursive=1"
try:
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})) as resp:
        tree = json.loads(resp.read().decode('utf-8'))['tree']
        for item in tree:
            if 'attachment' in item['path'].lower() and item['path'].endswith('.java'):
                print(item['path'])
            elif item['path'].endswith('GunItem.java') or item['path'].endswith('AttachmentItem.java'):
                print(item['path'])
except Exception as e:
    print(e)
