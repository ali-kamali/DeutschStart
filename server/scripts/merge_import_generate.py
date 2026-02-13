import json
from pathlib import Path
import urllib.request
import urllib.error
import time

# Configuration
API_BASE = "http://localhost:8000/api/v1"
SEED_DIR = Path(__file__).parent.parent / "data" / "seed"
MERGED_FILE = SEED_DIR / "merged_vocab.json"

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
    for field in ["pos", "translation", "category", "gender", "plural_form"]:
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
    
    for item in items:
        pos = item.get("pos", "unknown")
        pos_counts[pos] = pos_counts.get(pos, 0) + 1
        
        category = item.get("category", "uncategorized")
        category_counts[category] = category_counts.get(category, 0) + 1
        
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
        # Validate and fix nouns without articles
        merged_data = validate_and_fix_nouns(merged_data)
        
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

