
import json
import urllib.request
import urllib.error
from pathlib import Path

API_BASE = "http://localhost:8000/api/v1"
MERGED_FILE = Path("e:/Health/Germany/server/data/seed/merged_vocab.json")

def debug_import():
    try:
        with open(MERGED_FILE, "r", encoding="utf-8") as f:
            items = json.load(f)
            
        print(f"Importing {len(items)} items...")
        
        payload = {
            "source_name": "debug_import",
            "items": items
        }
        
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{API_BASE}/import/vocabulary",
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        
        with urllib.request.urlopen(req) as response:
            result = json.load(response)
            print(f"Success: {result}")
            
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code}:")
        error_body = e.read().decode()
        print(error_body)
        try:
             err_json = json.loads(error_body)
             print("\nParsed Error:")
             print(json.dumps(err_json, indent=2))
        except:
             pass
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    debug_import()
