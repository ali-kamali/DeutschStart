import json
import sqlite3
import urllib.request
import os
import sys
from pathlib import Path
import time

# Configuration
# Robust path resolution: relative to this script's location
CURRENT_DIR = Path(__file__).resolve().parent   # server/app/tasks
PROJECT_ROOT = CURRENT_DIR.parent.parent        # server/app -> server
DATA_DIR = PROJECT_ROOT / "data" / "dictionaries"
DB_PATH = DATA_DIR / "kaikki_german.db"
JSONL_PATH = DATA_DIR / "kaikki.org-dictionary-German.jsonl"
KAIKKI_URL = "https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl"

def init_db():
    """Initialize the SQLite database schema."""
    if not DATA_DIR.exists():
        DATA_DIR.mkdir(parents=True, exist_ok=True)
    
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    # Store lemmatized entries
    c.execute('''
        CREATE TABLE IF NOT EXISTS entries (
            lemma TEXT PRIMARY KEY,
            pos TEXT,
            gender TEXT,
            plural TEXT,
            ipa TEXT,
            senses TEXT
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_lemma ON entries(lemma)')
    conn.commit()
    conn.close()
    print(f"Database initialized at {DB_PATH}")

def download_kaikki():
    """Download the Kaikki JSONL file using urllib."""
    if JSONL_PATH.exists():
        print(f"File {JSONL_PATH} already exists. Skipping download.")
        return

    print(f"Downloading {KAIKKI_URL}...")
    try:
        def reporthook(blocknum, blocksize, totalsize):
            readso_far = blocknum * blocksize
            if totalsize > 0:
                percent = readso_far * 1e2 / totalsize
                s = "\r%5.1f%% %*d / %d" % (
                    percent, len(str(totalsize)), readso_far, totalsize)
                sys.stderr.write(s)
                if readso_far >= totalsize: # near the end
                    sys.stderr.write("\n")
        
        urllib.request.urlretrieve(KAIKKI_URL, JSONL_PATH, reporthook)
        print("Download complete.")
    except Exception as e:
        print(f"Error downloading: {e}")
        if JSONL_PATH.exists():
            os.remove(JSONL_PATH) # Cleanup partial download

def process_kaikki():
    """Parse JSONL, aggregate by lemma in memory, and populate SQLite."""
    print("Processing JSONL...")
    
    # In-memory aggregation to handle multiple entries per lemma (etymologies)
    entries = {} # lemma -> {pos, gender, plural_set, ipa, senses_list}
    count = 0
    skipped = 0
    
    try:
        with open(JSONL_PATH, "r", encoding="utf-8") as f:
            for line in f:
                try:
                    data = json.loads(line)
                except json.JSONDecodeError:
                    continue

                pos = data.get("pos")
                if pos not in ["noun", "verb", "adj", "adv", "pron", "det", "num", "conj", "prep", "particle", "intj"]: 
                    skipped += 1
                    continue

                lemma = data.get("word")
                if not lemma: continue

                # Extraction logic (same as before)
                # Gender extraction - Hierarchical check
                gender = data.get("gender", "")
                
                # 1. Check senses if top-level missing
                if not gender and "senses" in data:
                    for sense in data["senses"]:
                        if "tags" in sense:
                             tags = sense["tags"]
                             if "masculine" in tags: gender = "m"
                             elif "feminine" in tags: gender = "f"
                             elif "neuter" in tags: gender = "n"
                             if gender: break
                
                # 2. Check head_templates
                if not gender and "head_templates" in data:
                    for template in data["head_templates"]:
                        if "args" in template:
                            args = template["args"]
                            g_val = args.get("1") or args.get("g")
                            if g_val:
                                if g_val in ["m", "f", "n"]: gender = g_val
                                elif "m" in g_val: gender = "m"
                                elif "f" in g_val: gender = "f"
                                elif "n" in g_val: gender = "n"
                            if gender: break
                
                plural = []
                if "forms" in data:
                    for form in data["forms"]:
                        tags = form.get("tags", [])
                        if "plural" in tags and "nominative" in tags:
                            plural.append(form.get("form"))
                
                ipa = ""
                if "sounds" in data:
                    for sound in data["sounds"]:
                        if "ipa" in sound:
                            ipa = sound["ipa"]
                            break 
                
                senses = []
                if "senses" in data:
                    for sense in data["senses"]:
                        if "glosses" in sense: senses.extend(sense["glosses"])
                        elif "raw_glosses" in sense: senses.extend(sense["raw_glosses"])

                # Aggregation Logic
                if lemma not in entries:
                    entries[lemma] = {
                        "pos": pos,
                        "gender": gender,
                        "plurals": set(plural),
                        "ipa": ipa,
                        "senses": senses
                    }
                else:
                    entry = entries[lemma]
                    # Update gender if missing
                    if not entry["gender"] and gender:
                        entry["gender"] = gender
                    # Merge plurals
                    entry["plurals"].update(plural)
                    # Keep first IPA usually, or update if missing
                    if not entry["ipa"] and ipa:
                        entry["ipa"] = ipa
                    # Append senses
                    entry["senses"].extend(senses)

                count += 1
                if count % 10000 == 0:
                    print(f"Parsed {count} lines...", end="\r")

        print(f"\nAggregated {len(entries)} unique lemmas. Writing to DB...")
        
        conn = sqlite3.connect(DB_PATH)
        c = conn.cursor()
        
        batch = []
        for lemma, data in entries.items():
            plural_json = json.dumps(list(data["plurals"]), ensure_ascii=False)
            senses_json = json.dumps(data["senses"], ensure_ascii=False)
            
            batch.append((
                lemma, 
                data["pos"], 
                data["gender"], 
                plural_json, 
                data["ipa"], 
                senses_json
            ))
            
            if len(batch) >= 2000:
                c.executemany("INSERT OR REPLACE INTO entries VALUES (?, ?, ?, ?, ?, ?)", batch)
                conn.commit()
                batch = []
                
        if batch:
            c.executemany("INSERT OR REPLACE INTO entries VALUES (?, ?, ?, ?, ?, ?)", batch)
            conn.commit()
            
        print(f"Finished writing {len(entries)} entries.")
        
    except Exception as e:
        print(f"\nError processing JSONL: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if 'conn' in locals(): conn.close()

def main():
    print("Starting Kaikki ingest task...")
    init_db()
    download_kaikki()
    process_kaikki()
    print("Task parsed successfully.")

if __name__ == "__main__":
    main()
