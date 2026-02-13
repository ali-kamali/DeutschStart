import json
from pathlib import Path
import urllib.request
import urllib.parse
import urllib.error
import time
import sqlite3
import os
import random

# Configuration
API_BASE = "http://localhost:8000/api/v1"
SEED_DIR = Path(__file__).parent.parent / "data" / "seed"
MERGED_FILE = SEED_DIR / "merged_vocab.json"

# --- HELPER: DAFlex API ---
def fetch_daflex_frequency(word):
    """
    Query DAFlex API for a word.
    Returns: (a1_freq, total_freq) or (0, 0) if not found.
    """
    # DAFlex expects the word to be URL encoded
    url = f"https://cental.uclouvain.be/cefrlex/cefrlex/daflex/autocomplete/TreeTagger%20-%20German/{urllib.parse.quote(word)}/"
    
    headers = {
        'accept': '*/*',
        'accept-language': 'en-US,en;q=0.9',
        'priority': 'u=1, i',
        'referer': 'https://cental.uclouvain.be/cefrlex/daflex/search/',
        'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36',
    }

    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                data = json.load(response)
                # DAFlex returns a list of objects. We look for the exact match or first item.
                # Structure: [{'key': [freq_A1, freq_A2...], 'value': 'Word [POS]'}]
                if data and isinstance(data, list):
                    # Simple heuristic: take the first result's A1 frequency (index 0 of key list)
                    # "key" is [A1, A2, B1, B2, C1, C2, Total]
                    freqs = data[0].get('key', [])
                    if freqs and len(freqs) >= 7:
                        return freqs[0], freqs[6] # A1, Total
    except Exception as e:
        # print(f"DAFlex error for {word}: {e}") # Be quiet to avoid spam
        pass
        
    return 0, 0

# Helper: Generate a normalized key for merging
def generate_key(word):
    """
    Generate a normalized key for deduplication.
    Strips common articles and lowercases the word.
    """
    key = word.strip().lower()
    prefixes = ["der ", "die ", "das ", "ein ", "eine "]
    for p in prefixes:
        if key.startswith(p):
            # Only strip if it's a prefix followed by a valid character
            # (already checked by endswith space in prefix list)
            return key[len(p):].strip()
    return key

# Helper: Smart merge two items
def smart_merge(existing_item, new_item):
    """
    Merges new_item into existing_item with specific rules:
    - Prefer word with article (longer length usually)
    - Fill missing POS, translation, category, gender, plural_form
    - Merge and deduplicate example sentences
    - PRESESERVE manual priority/theme if exists
    """
    merged = existing_item.copy()
    
    # Word: Prefer the one that starts with an article (heuristic: longer is better usually for "der Mann" vs "Mann")
    # or just check for article prefixes
    existing_word = merged.get("word", "")
    new_word = new_item.get("word", "")
    
    # If existing is just "Mann" and new is "der Mann", take new.
    # Simple heuristic: newer one is better if it's longer (has article)
    if len(new_word) > len(existing_word):
        merged["word"] = new_word
    
    # Fields to fill if missing
    for field in ["pos", "translation", "category", "gender", "plural_form", "priority", "theme"]:
        if not merged.get(field) and new_item.get(field):
            merged[field] = new_item.get(field)
            
    # Example sentences: Append and deduplicate
    existing_sentences = merged.get("example_sentences", [])
    new_sentences = new_item.get("example_sentences", [])
    
    # Use a set of (german, english) tuples to deduplicate
    unique_sentences = {}
    
    all_sentences = existing_sentences + new_sentences
    for s in all_sentences:
        # Create a unique key for the sentence (e.g. the german text)
        s_key = s.get("german", "").strip()
        if s_key and s_key not in unique_sentences:
            unique_sentences[s_key] = s
            
    merged["example_sentences"] = list(unique_sentences.values())
    
    return merged

def merge_seed_files():
    """
    Merges all JSON files in the seed directory into a single list of vocabulary items.
    Uses 'word' (case-insensitive, ignoring articles) as the key to deduplicate.
    Smart merges content instead of just overwriting.
    """
    print(f"Scanning seed directory: {SEED_DIR}")
    if not SEED_DIR.exists():
        print(f"Error: Seed directory not found at {SEED_DIR}")
        return None

    merged_items = {}
    json_files = sorted(list(SEED_DIR.glob("*.json")))
    
    if not json_files:
        print("No JSON files found to merge.")
        return None

    print(f"Found {len(json_files)} files.")

    for file_path in json_files:
        # Skip the output file itself if it exists
        if file_path.name == MERGED_FILE.name:
            continue
            
        print(f"Processing {file_path.name}...")
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                items = json.load(f)
                
                if not isinstance(items, list):
                    print(f"Warning: {file_path.name} does not contain a list. Skipping.")
                    continue

                for item in items:
                    word = item.get("word")
                    if not word:
                        continue
                    
                    # Key handling: lower case and strip articles for better matching
                    key = generate_key(word)
                    
                    if key in merged_items:
                        # Smart merge with existing data
                        merged_items[key] = smart_merge(merged_items[key], item)
                    else:
                        merged_items[key] = item
                        
        except Exception as e:
            print(f"Error reading {file_path.name}: {e}")

    # Load Anki data if available
    anki_path = SEED_DIR.parent / "anki_export" / "Starten_wir_A1__German_Vocabulary__Sentences_with_Audio" / "anki_extracted.json"
    if anki_path.exists():
        print(f"Merging Anki data from {anki_path.name}...")
        try:
            with open(anki_path, "r", encoding="utf-8") as f:
                anki_items = json.load(f)
                
            for item in anki_items:
                # Transform audio fields
                if "original_audio" in item:
                    val = item.pop("original_audio", None)
                    if val:
                        item["audio_learn_path"] = f"audio/vocab/{val}"
                    
                if item.get("example_sentences"):
                     for sent in item["example_sentences"]:
                         if "original_audio" in sent:
                             val = sent.pop("original_audio", None)
                             if val:
                                 sent["audio_path"] = f"audio/sentences/{val}"

                word = item.get("word")
                if not word: continue
                
                key = generate_key(word)
                
                if key in merged_items:
                    merged_items[key] = smart_merge(merged_items[key], item)
                else:
                    # Only add new items if they have valid content (e.g. not just "L01·Foo")
                    # Since Anki data is messy, we prioritize existing manual data
                    merged_items[key] = item
                    
        except Exception as e:
             print(f"Error merging Anki data: {e}")


    result_list = list(merged_items.values())
    print(f"Merged complete. Total unique items: {len(result_list)}")
    
    # Save the merged file (optional, but good for debugging)
    with open(MERGED_FILE, "w", encoding="utf-8") as f:
        json.dump(result_list, f, indent=2, ensure_ascii=False)
    print(f"Saved merged file to: {MERGED_FILE}")
    
    return result_list

def assign_priority_and_theme(items):
    """
    1. Fetch DAFlex Frequency.
    2. Assign Priority/Theme based on rules.
    """
    print("Assigning Priority & Theme data...")
    print("Fetching DAFlex frequency...")
    
    updated_items = []
    
    for i, item in enumerate(items):
        if i % 10 == 0:
            print(f"Processing {i}/{len(items)}...", end="\r")
            
        word = item.get("word", "")
        clean_word = generate_key(word) # strips articles
        
        # 1. Fetch Frequency if priority not manually set
        if not item.get("daflex_freq"):
            a1_freq, total_freq = fetch_daflex_frequency(clean_word)
            item["daflex_a1"] = a1_freq
            item["daflex_total"] = total_freq
            # Minimal delay to respect API limits but keep it moving
            # The API allows some burst, but let's be safe.
            time.sleep(0.01) 

        # 2. Assign Priority
        # Default to 4
        p = 4
        
        pos = item.get("pos", "").lower()
        cat = item.get("category", "").lower()
        freq = item.get("daflex_a1", 0)
        
        # Rule Set
        if pos in ["pronoun", "particle", "conjunction", "preposition", "number", "article", "question word"]:
            p = 1
        elif freq > 500: # High frequency
            p = 1
        elif freq > 50: # Mid frequency
            p = 2
        elif "food" in cat or "shopping" in cat or "time" in cat:
            p = 3
        else:
            p = 4
            
        # Overrides based on manual category mapping
        if "people" in cat or "family" in cat:
            p = min(p, 2) # Ensure at least P2
        if "home" in cat:
            p = min(p, 2)
            
        # Set theme based on category if not present
        if not item.get("theme"):
            item["theme"] = item.get("category", "General")
            
        # Only overwrite if not manually set? Let's overwrite for now to enforce system.
        item["priority"] = p
        
        updated_items.append(item)
        
    print(f"\nPriority assignment complete.")
    return updated_items

def interleave_and_order(items):
    """
    Sorts items into the final interleaved learning order.
    Returns: List of items with 'order_index' set.
    """
    print("Calculating interleaved order...")
    
    # 1. Bucket by Priority
    buckets = {1: [], 2: [], 3: [], 4: []}
    for item in items:
        p = item.get("priority", 4)
        if p not in buckets: p = 4
        buckets[p].append(item)
        
    final_order = []
    
    # Process each priority bucket
    for p in sorted(buckets.keys()):
        block_items = buckets[p]
        if not block_items: continue
        
        print(f"Processing Priority {p} block ({len(block_items)} items)...")
        
        # Group by Theme/Category within this block
        theme_groups = {}
        for item in block_items:
            t = item.get("theme", "General")
            if t not in theme_groups: theme_groups[t] = []
            theme_groups[t].append(item)
            
        # Separate by POS for mixing
        verbs = []
        nouns_by_theme = {}
        adjs = []
        others = []
        
        for item in block_items:
            # Simple broad pos classification
            pos = item.get("pos", "").lower()
            t = item.get("theme", "General")
            
            if "verb" in pos:
                verbs.append(item)
            elif "noun" in pos:
                if t not in nouns_by_theme: nouns_by_theme[t] = []
                nouns_by_theme[t].append(item)
            elif "adj" in pos or "adv" in pos:
                adjs.append(item)
            else:
                others.append(item)
                
        # Shuffle everything within groups for randomness
        random.shuffle(verbs)
        random.shuffle(adjs)
        random.shuffle(others)
        for t in nouns_by_theme:
            random.shuffle(nouns_by_theme[t])
            
        # Create Daily Batches
        # Target per batch: 2-3 others (func), 3-5 verbs, 3-5 nouns (diff themes), 2-3 adj
        
        while any([others, verbs, adjs, any(nouns_by_theme.values())]):
            batch = []
            
            # 1. Function Words / Others (2-3)
            if others:
                count = random.randint(2, 3)
                for _ in range(count):
                    if others: batch.append(others.pop())
                
            # 2. Verbs (3-5)
            if verbs:
                count = random.randint(3, 5)
                for _ in range(count):
                    if verbs: batch.append(verbs.pop())
                
            # 3. Nouns (3-5, mixed themes)
            if any(nouns_by_theme.values()):
                count = random.randint(3, 5)
                added_nouns = 0
                themes = list(nouns_by_theme.keys())
                random.shuffle(themes) # Randomize theme order for this batch
                
                # Simple round robin taking 1 from each theme
                # Iterate through themes until we have enough nouns or run out
                while added_nouns < count and any(nouns_by_theme.values()):
                    # Use a copy of themes list to iterate safely
                    current_pass_themes = [t for t in themes if nouns_by_theme[t]]
                    if not current_pass_themes: break
                    
                    for t in current_pass_themes:
                        if nouns_by_theme[t]:
                            batch.append(nouns_by_theme[t].pop())
                            added_nouns += 1
                        if added_nouns >= count: break
                
            # 4. Adjectives (2-3)
            if adjs:
                count = random.randint(2, 3)
                for _ in range(count):
                    if adjs: batch.append(adjs.pop())
            
            # Add batch to final order
            final_order.extend(batch)
            
            # Panic Button: if we are stuck (only 1 type left forever), dump the rest
            # The while loop condition handles it, but let's be sure we don't loop empty
            # If batch is empty but items remain (this shouldn't happen with correct logic, but safe guard)
            if not batch and (others or verbs or adjs or any(nouns_by_theme.values())):
                 # Dump leftovers
                 final_order.extend(others); others = []
                 final_order.extend(verbs); verbs = []
                 final_order.extend(adjs); adjs = []
                 for t in nouns_by_theme:
                     final_order.extend(nouns_by_theme[t])
                     nouns_by_theme[t] = []
    
    # Assign index
    for idx, item in enumerate(final_order):
        item["order_index"] = idx
        
    return final_order

def print_statistics(items):
    """
    Print statistics about the merged vocabulary list.
    """
    print("\n" + "="*60)
    print("MERGE STATISTICS")
    print("="*60)
    
    # Count by POS
    pos_counts = {}
    category_counts = {}
    nouns_without_articles = []
    total_sentences = 0
    priority_counts = {}
    
    for item in items:
        pos = item.get("pos", "unknown")
        pos_counts[pos] = pos_counts.get(pos, 0) + 1
        
        category = item.get("category", "uncategorized")
        category_counts[category] = category_counts.get(category, 0) + 1
        
        p = item.get("priority", "N/A")
        priority_counts[p] = priority_counts.get(p, 0) + 1
        
        # Count example sentences
        sentences = item.get("example_sentences", [])
        total_sentences += len(sentences)
        
        # Check for nouns without articles
        if pos == "noun":
            word = item.get("word", "").strip().lower()
            if not (word.startswith("der ") or word.startswith("die ") or word.startswith("das ")):
                nouns_without_articles.append(item.get("word", ""))
    
    print(f"\nTotal vocabulary items: {len(items)}")
    print(f"Total example sentences: {total_sentences}")
    print(f"Average sentences per word: {total_sentences/len(items):.2f}")
    
    print("\n--- By Priority ---")
    for p in sorted(priority_counts.keys()):
        print(f"  Priority {p}: {priority_counts[p]}")
        
    print("\n--- By Part of Speech ---")
    for pos in sorted(pos_counts.keys()):
        print(f"  {pos}: {pos_counts[pos]}")
    
    print("\n--- Top 10 Categories ---")
    sorted_categories = sorted(category_counts.items(), key=lambda x: x[1], reverse=True)
    for cat, count in sorted_categories[:10]:
        print(f"  {cat}: {count}")
    
    if nouns_without_articles:
        print(f"\n⚠️  WARNING: {len(nouns_without_articles)} nouns missing articles:")
        for word in nouns_without_articles[:10]:  # Show first 10
            print(f"    - {word}")
        if len(nouns_without_articles) > 10:
            print(f"    ... and {len(nouns_without_articles) - 10} more")
    else:
        print("\n✓ All nouns have articles!")
    
    print("="*60 + "\n")

def validate_and_fix_nouns(items):
    """
    Validate that all nouns have articles. 
    For common nouns without articles, we could try to add them based on gender field.
    """
    fixed_count = 0
    
    for item in items:
        if item.get("pos") == "noun":
            word = item.get("word", "").strip()
            word_lower = word.lower()
            
            # Check if already has article
            if word_lower.startswith("der ") or word_lower.startswith("die ") or word_lower.startswith("das "):
                continue
            
            # Try to add article based on gender
            gender = item.get("gender")
            if gender == "m":
                item["word"] = f"der {word}"
                fixed_count += 1
            elif gender == "f":
                item["word"] = f"die {word}"
                fixed_count += 1
            elif gender == "n":
                item["word"] = f"das {word}"
                fixed_count += 1
            # If no gender specified, we can't auto-fix, will show in warnings
    
    if fixed_count > 0:
        print(f"✓ Auto-fixed {fixed_count} nouns by adding articles based on gender field\n")
    
    return items

def enrich_with_kaikki(items):
    """
    Enrich vocabulary items with data from Kaikki JSONL file (IPA, Senses, Audio).
    Streams the JSONL file once and matches against the requested vocabulary.
    Avoids creating a full intermediate database.
    """
    print("Enriching vocabulary with Kaikki data (Streaming JSONL)...")
    
    # Paths
    jsonl_path = Path(__file__).parent.parent / "data" / "dictionaries" / "kaikki.org-dictionary-German.jsonl"
    audio_dir = Path(__file__).parent.parent / "data" / "processed" / "audio" / "kaikki"
    audio_dir.mkdir(parents=True, exist_ok=True)
    
    if not jsonl_path.exists():
        print(f"Warning: Kaikki JSONL not found at {jsonl_path}. Downloading...")
        # Download logic here or ask user to run ingest task?
        # Let's perform a direct download if missing.
        jsonl_path.parent.mkdir(parents=True, exist_ok=True)
        url = "https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl"
        try:
            print(f"Downloading from {url}...")
            urllib.request.urlretrieve(url, jsonl_path)
            print("Download complete.")
        except Exception as e:
            print(f"Failed to download Kaikki: {e}")
            return items

    # 1. Build a lookup map for our items (lemma -> list of items)
    # We map "lemma" to the item object so we can update it in place.
    # Note: Multiple items might map to same lemma (unlikely in seed but possible).
    item_map = {}
    
    prefixes = ["der ", "die ", "das ", "ein ", "eine "]
    
    for item in items:
        word = item.get("word", "")
        if not word: continue
        
        # Determine lemma (strip article)
        clean_word = word.strip()
        lemma = clean_word
        for p in prefixes:
            if clean_word.lower().startswith(p):
                lemma = clean_word[len(p):].strip()
                break
        
        # Store using the lemma as key (case sensitive usually matches Kaikki)
        # But Kaikki uses the exact word form usually.
        # "Tisch" -> "Tisch". "Der Tisch" -> "Tisch".
        if lemma not in item_map:
            item_map[lemma] = []
        item_map[lemma].append(item)

    print(f"Scanning Kaikki for {len(item_map)} unique lemmas...")
    if item_map:
        print(f"Sample lemmas in our list: {list(item_map.keys())[:5]}")

    found_count = 0
    audio_download_count = 0
    
    # 2. Stream JSONL
    try:
        with open(jsonl_path, "r", encoding="utf-8") as f:
            line_count = 0
            for line in f:
                line_count += 1
                try:
                    data = json.loads(line)
                except:
                    continue
                    
                word = data.get("word")
                if line_count < 5:
                    print(f"Sample Kaikki word: '{word}'")
                
                if not word: continue
                
                # Check if this word is in our map
                if word in item_map:
                    # Found a match! Extract data.
                    match_items = item_map[word]
                    
                    # Extract IPA
                    ipa = ""
                    audio_url = ""
                    
                    if "sounds" in data:
                        for sound in data["sounds"]:
                            if "ipa" in sound and not ipa:
                                ipa = sound["ipa"]
                            if "ogg_url" in sound and not audio_url:
                                audio_url = sound["ogg_url"]
                            elif "mp3_url" in sound and not audio_url:
                                audio_url = sound["mp3_url"]
                    
                    senses = []
                    if "senses" in data:
                        for sense in data["senses"]:
                            if "glosses" in sense: senses.extend(sense["glosses"])
                            elif "raw_glosses" in sense: senses.extend(sense["raw_glosses"])
                            
                    # Update all matching items
                    for item in match_items:
                        # Only update if missing or if we want to enrich
                        if ipa and not item.get("ipa"):
                            item["ipa"] = ipa
                            
                        # Senses (store as kaikki_data)
                        if senses:
                            # We might find multiple entries for same word (e.g. noun/verb)
                            # Ideally check POS? For MVP, just take the first non-empty.
                            if not item.get("kaikki_data"):
                                item["kaikki_data"] = {"senses": senses}
                            else:
                                # Append?
                                pass
                        
                        # Audio
                        if audio_url and not item.get("kaikki_audio_path"):
                            filename = audio_url.split("/")[-1]
                            local_path = audio_dir / filename
                            
                            # Download if not exists
                            if not local_path.exists():
                                try:
                                    # Retry logic for 429
                                    item_downloaded = False
                                    retries = 3
                                    
                                    while retries > 0 and not item_downloaded:
                                        try:
                                            req = urllib.request.Request(
                                                audio_url,
                                                headers={'User-Agent': 'DeutschStart/1.0 (contact: admin@deutschstart.app)'}
                                            )
                                            with urllib.request.urlopen(req) as response:
                                                 with open(local_path, "wb") as f_out:
                                                     f_out.write(response.read())
                                            
                                            audio_download_count += 1
                                            item_downloaded = True
                                            print(f"Downloaded: {filename}")
                                            
                                            # Be nice to the server (1.0s delay)
                                            time.sleep(1.0)
                                            
                                        except urllib.error.HTTPError as e:
                                            if e.code == 429:
                                                print(f"Rate limited (429) for {filename}. Retrying in 10s...")
                                                time.sleep(10)
                                                retries -= 1
                                            elif e.code == 404:
                                                print(f"File not found (404): {audio_url}")
                                                break # Don't retry
                                            else:
                                                print(f"Failed download {filename}: {e}")
                                                break
                                        except Exception as e:
                                            print(f"Error downloading {filename}: {e}")
                                            break
                                except Exception as e:
                                    print(f"Outer error downloading {filename}: {e}")
                            
                            if local_path.exists():
                                item["kaikki_audio_path"] = f"audio/kaikki/{filename}"
                                
                    found_count += 1
                    
    except Exception as e:
        print(f"Error reading JSONL: {e}")

    print(f"Enrichment complete. Found matches for {found_count} entries.")
    if audio_download_count > 0:
        print(f"Downloaded {audio_download_count} new audio files.")
    
    return items

def import_vocabulary(items):
    if not items:
        print("No items to import.")
        return

    payload = {
        "source_name": "merged_seed_import",
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
        f"{API_BASE}/packs/latest?version_tag=v3",
        data=b"", 
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
    merged_data = merge_seed_files()
    if merged_data:
        # 1. Validate and fix nouns without articles
        merged_data = validate_and_fix_nouns(merged_data)
        
        # 2. Enrich with Kaikki
        merged_data = enrich_with_kaikki(merged_data)
        
        # 3. Assign Priority and Theme (FETCHES DAFLEX)
        merged_data = assign_priority_and_theme(merged_data)
        
        # 4. Interleave and Order
        merged_data = interleave_and_order(merged_data)
        
        # Print statistics
        print_statistics(merged_data)
        
        # Re-save the merged file after fixes
        with open(MERGED_FILE, "w", encoding="utf-8") as f:
            json.dump(merged_data, f, indent=2, ensure_ascii=False)
        print(f"✓ Saved corrected merged file to: {MERGED_FILE}\n")
        
        # Import and generate pack
        import_vocabulary(merged_data)
        print("-" * 30)
        generate_pack()
