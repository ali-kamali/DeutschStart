
import sqlite3
import zipfile
import json
import os
import shutil
import re
from pathlib import Path
import argparse

# Configuration
BASE_DIR = Path(__file__).parent.parent
RAW_ANKI_DIR = BASE_DIR / "data" / "raw" / "anki"
OUTPUT_DIR = BASE_DIR / "data" / "anki_export"

def clean_html(raw_html):
    """Remove HTML tags from text."""
    if not raw_html:
        return ""
    cleanr = re.compile('<.*?>')
    cleantext = re.sub(cleanr, '', raw_html)
    return cleantext.strip()

def extract_sound_tag(field_text):
    """Extract sound filename from [sound:...] tag."""
    match = re.search(r'\[sound:(.*?)\]', field_text)
    if match:
        return match.group(1)
    return None

def extract_anki_deck(apkg_path, output_dir=OUTPUT_DIR):
    """
    Extracts content from an Anki .apkg file.
    """
    print(f"Processing: {apkg_path}")
    
    # Create output directories
    output_dir.mkdir(parents=True, exist_ok=True)
    media_dir = output_dir / "media"
    media_dir.mkdir(exist_ok=True)
    
    # Extract apk (it's a zip)
    with zipfile.ZipFile(apkg_path, 'r') as z:
        print(f"Zip contents: {z.namelist()[:10]}...") 
        
        # 1. Extract media mapping
        media_map = {}
        try:
            with z.open("media") as f:
                media_map = json.load(f) # Maps "0" -> "filename.mp3"
        except KeyError:
            print("Warning: No media mapping found.")

        # 2. Extract database
        # Try both old and new naming or just grab the first file that isn't media/media.db
        db_filename = "collection.anki2"
        if "collection.anki21" in z.namelist():
             db_filename = "collection.anki21"
        elif "collection.anki2" not in z.namelist():
             # Fallback: Find largest file that isn't media
             candidates = [n for n in z.namelist() if n != "media" and not n.isdigit()]
             if candidates:
                 db_filename = candidates[0]
        
        print(f"Extracting database: {db_filename}")
        z.extract(db_filename, output_dir)
        db_path = output_dir / db_filename
        
        # 3. Extract all media files
        print(f"Extracting {len(media_map)} media files...")
        for key, filename in media_map.items():
            try:
                # Anki zip stores files as stringified integers (keys in the json)
                source = z.read(key)
                with open(media_dir / filename, "wb") as f_out:
                    f_out.write(source)
            except KeyError:
                print(f"Warning: Media file {key} ({filename}) not found in archive.")

    # Connect to SQLite DB
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Get all notes
    # Notes table: id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data
    # flds contains the fields separated by 0x1f
    cursor.execute("SELECT flds, tags FROM notes")
    notes = cursor.fetchall()
    
    extracted_items = []
    
    print(f"Found {len(notes)} notes. Processing...")
    
    print(f"Found {len(notes)} notes. Processing...")
    
    debug_notes = []
    
    for i, (flds, tags) in enumerate(notes):
        fields = flds.split('\x1f')
        
        if i < 3:
            debug_notes.append({
                "index": i,
                "fields": fields
            })
            
    with open(output_dir / "debug_raw.json", "w", encoding="utf-8") as f:
        json.dump(debug_notes, f, indent=2, ensure_ascii=False)
        
    print(f"Found {len(notes)} notes. Processing...")
    
    # Debug dump... (omitted/kept)
    
    for i, (flds, tags) in enumerate(notes):
        fields = flds.split('\x1f')
        
        # Specific mapping for "Starten wir A1" deck
        # Field 0: L01·word
        # Field 1: [sound:word.mp3]
        # Field 2: Sentence DE
        # Field 3: [sound:sentence.mp3]
        # Field 4: Sentence EN
        
        if len(fields) < 5:
            continue

        # 1. Word
        raw_word = clean_html(fields[0])
        # Remove Lxx prefix
        word_match = re.search(r'L\d+[·\s](.*)', raw_word)
        german_word = word_match.group(1) if word_match else raw_word
        german_word = german_word.strip()

        # 2. Word Audio
        word_audio = extract_sound_tag(fields[1])
        
        # 3. Sentence
        sent_german = clean_html(fields[2])
        
        # 4. Sentence Audio
        sent_audio = extract_sound_tag(fields[3])
        
        # 5. Sentence Translation (EN)
        sent_english = clean_html(fields[4])
        
        # Basic gender detection
        gender = None
        lower_word = german_word.lower()
        if lower_word.startswith("der "): gender = "m"
        elif lower_word.startswith("die "): gender = "f"
        elif lower_word.startswith("das "): gender = "n"
        
        pos = "noun" if gender else "phrase" 
        if not gender and raw_word[0].islower():
            # If starts with lowercase and no article, likely verb or adj
            pos = "other"

        # Construct item
        item = {
            "word": german_word,
            "translation": "", # Missing in this deck!
            "pos": pos,
            "gender": gender,
            "category": "Anki Import", 
            "tags": tags.strip(),
            "original_audio": word_audio,
            "example_sentences": []
        }
        
        if sent_german:
            sent_obj = {
                "german": sent_german,
                "english": sent_english,
                "original_audio": sent_audio
            }
            item["example_sentences"].append(sent_obj)
        
        extracted_items.append(item)
        
    conn.close()
    
    # Save to JSON
    json_path = output_dir / "anki_extracted.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(extracted_items, f, indent=2, ensure_ascii=False)
        
    # Clean up DB file
    if db_path.exists():
        os.remove(db_path)
        
    print(f"Extraction complete.")
    print(f"JSON saved to: {json_path}")
    print(f"Media saved to: {media_dir}")
    print(f"Total items: {len(extracted_items)}")
    print("\nIMPORTANT: Review 'anki_extracted.json' and adjust field indices in this script if data looks wrong!")

if __name__ == "__main__":
    # Look for .apkg files in RAW_ANKI_DIR
    apkg_files = list(RAW_ANKI_DIR.glob("*.apkg"))
    
    if not apkg_files:
        print(f"No .apkg files found in {RAW_ANKI_DIR}")
        print("Please place your Anki deck file there.")
    else:
        for apkg in apkg_files:
            # Create a subfolder for each deck to avoid collisions
            deck_output = OUTPUT_DIR / apkg.stem
            extract_anki_deck(apkg, deck_output)
