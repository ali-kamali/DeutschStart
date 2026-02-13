import json
import urllib.request
import urllib.error
import time
from pathlib import Path

# Configuration
API_BASE = "http://localhost:8000/api/v1"
SEED_FILE = Path(__file__).parent.parent / "data" / "seed" / "a1_frequency_400.json"

def import_vocabulary():
    print(f"Reading seed file: {SEED_FILE}")
    if not SEED_FILE.exists():
        print(f"Error: Seed file not found at {SEED_FILE}")
        return

    with open(SEED_FILE, "r", encoding="utf-8") as f:
        items = json.load(f)

    payload = {
        "source_name": "seed_a1_400",
        "items": items
    }
    
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        f"{API_BASE}/import/vocabulary",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        print("Importing vocabulary...")
        with urllib.request.urlopen(req) as response:
            result = json.load(response)
            print(f"Import Success: {result}")
    except urllib.error.HTTPError as e:
        print(f"Import Failed: {e.code} - {e.read().decode()}")
    except Exception as e:
        print(f"Import Error: {e}")

def generate_pack():
    print("Triggering pack generation (this may take a while)...")
    req = urllib.request.Request(
        f"{API_BASE}/packs/latest?version_tag=v1",
        data=b"", # POST request needs data even if empty for some libs, but query param handles args here
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as response:
            result = json.load(response)
            print(f"Pack Generation Success: {result}")
            print(f"Download URL: {API_BASE}/packs/{result['path'].split('/')[-1]}")
    except urllib.error.HTTPError as e:
        print(f"Pack Generation Failed: {e.code} - {e.read().decode()}")
    except Exception as e:
        print(f"Pack Generation Error: {e}")

if __name__ == "__main__":
    import_vocabulary()
    print("-" * 30)
    generate_pack()
