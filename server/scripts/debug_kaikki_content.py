import json
from pathlib import Path

# Path inside the container for the dictionary
JSONL_PATH = Path("data/dictionaries/kaikki.org-dictionary-German.jsonl")

if not JSONL_PATH.exists():
    print(f"Error: {JSONL_PATH} does not exist.")
    exit(1)

print(f"Reading file: {JSONL_PATH}")
with open(JSONL_PATH, "r", encoding="utf-8") as f:
    for i in range(10):  # Check 10 lines
        line = f.readline()
        if not line: break
        try:
            data = json.loads(line)
            print(f"Index {i}: Word='{data.get('word')}'")
            if "sounds" in data:
                for sound in data['sounds']:
                    print(f"  Sound Keys: {list(sound.keys())}")
                    if 'audio' in sound: print(f"    audio: {sound['audio']}")
                    if 'mp3_url' in sound: print(f"    mp3_url: {sound['mp3_url']}")
                    if 'ogg_url' in sound: print(f"    ogg_url: {sound['ogg_url']}")
        except Exception as e:
            print(f"Error parsing line {i+1}: {e}")
