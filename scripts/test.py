import urllib.request
import json
import base64

url = 'https://api.github.com/repos/MCModderAnchor/TACZ/contents/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/guns/deagle.json?ref=1.20.1'
try:
    with urllib.request.urlopen(url) as response:
        data = json.loads(response.read())
        content = base64.b64decode(data['content']).decode('utf-8')
        print(content)
except Exception as e:
    print(e)
