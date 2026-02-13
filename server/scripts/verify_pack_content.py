import zipfile
import json
import os
from pathlib import Path

PACK_PATH = Path(r"e:\Health\Germany\server\data\processed\packs\deutschstart_v3_fix.zip")

def verify_pack():
    if not PACK_PATH.exists():
        print(f"Pack file not found: {PACK_PATH}")
        return

    print(f"Checking pack: {PACK_PATH}")
    with zipfile.ZipFile(PACK_PATH, 'r') as z:
        if "vocabulary.json" not in z.namelist():
            print("ERROR: vocabulary.json missing in zip!")
            return
            
        with z.open("vocabulary.json") as f:
            data = json.load(f)
            
        print(f"Loaded vocabulary.json with {len(data)} items.")
        
        # Check ordering logic indirectly (since order_index is not in JSON output usually unless changed)
        # But if we see mixed categories, it works.
        
        # Print first 20 items to see the mix.
        sys_out = []
        for i, item in enumerate(data[:30]):
            word = item.get("word", "")
            pos = item.get("pos", "")
            # We don't have priority/theme in final JSON structure (unless I added them to packager output?)
            # Packager output structure: {id, word, pos, ...}
            # Let's see if we can infer theme or just see the mixing.
            sys_out.append(f"{i}: {word} ({pos})")
            
        print("\nFirst 30 items (Should be interleaved):")
        print("\n".join(sys_out))
        
        # Check for non-mixed blocks (e.g. 10 verbs in a row)
        pos_sequence = [item.get("pos", "") for item in data]
        
        # Simple run-length encoding
        if not pos_sequence: return
        
        last_pos = pos_sequence[0]
        count = 1
        max_run = 0
        max_pos = ""
        
        for p in pos_sequence[1:]:
            if p == last_pos:
                count += 1
            else:
                if count > max_run:
                    max_run = count
                    max_pos = last_pos
                count = 1
                last_pos = p
                
        print(f"\nMax run of same POS: {max_run} ({max_pos})")
        if max_run > 10:
             print("WARNING: Long run of same POS found. Interleaving might be broken.")
        else:
             print("SUCCESS: Interleaving seems effective.")

if __name__ == "__main__":
    verify_pack()
