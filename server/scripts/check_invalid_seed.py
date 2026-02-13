
import json
from pathlib import Path

FILE = Path("e:/Health/Germany/server/data/seed/merged_vocab.json")

def check():
    with open(FILE, "r", encoding="utf-8") as f:
        items = json.load(f)
        
    print(f"Checking {len(items)} items...")
    
    for i, item in enumerate(items):
        word = item.get("word", "<missing>")
        
        # Check required fields
        if not isinstance(item.get("translation"), str):
            print(f"Item {i} ({word}): 'translation' is not a string: {item.get('translation')}")
            
        if not isinstance(item.get("pos"), str):
            print(f"Item {i} ({word}): 'pos' is not a string: {item.get('pos')}")
            
        if not isinstance(item.get("category"), str):
            print(f"Item {i} ({word}): 'category' is not a string: {item.get('category')}")
            
    print("Check complete.")

if __name__ == "__main__":
    check()
