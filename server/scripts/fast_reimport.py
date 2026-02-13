import json
from pathlib import Path
import urllib.request
import urllib.error

# Configuration
API_BASE = "http://localhost:8000/api/v1"
SEED_DIR = Path(__file__).parent.parent / "data" / "seed"
MERGED_FILE = SEED_DIR / "merged_vocab.json"

def import_vocabulary(items):
    if not items:
        print("No items to import.")
        return

    payload = {
        "source_name": "merged_seed_import_v2",
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
        print("Importing merged vocabulary...")
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
        f"{API_BASE}/packs/latest?version_tag=v3_fix",
        data=b"", 
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as response:
            result = json.load(response)
            print(f"Pack Generation Success: {result}")
    except urllib.error.HTTPError as e:
        print(f"Pack Generation Failed: {e.code} - {e.read().decode()}")
    except Exception as e:
        print(f"Pack Generation Error: {e}")

if __name__ == "__main__":
    if not MERGED_FILE.exists():
        print(f"Merged file not found at {MERGED_FILE}. Run full script first.")
    else:
        with open(MERGED_FILE, "r", encoding="utf-8") as f:
            items = json.load(f)
            
        print(f"Loaded {len(items)} items from {MERGED_FILE}")
        
        # Verify if order_index is present in file (just a check)
        has_order = any("order_index" in i for i in items[:10])
        print(f"Items have order_index? {has_order}")
        
        import_vocabulary(items)
        generate_pack()
