
import json
import logging
from pathlib import Path

DATA_DIR = Path("e:/Health/Germany/server/data/seed")

def check_missing_articles():
    files = sorted(DATA_DIR.glob("*.json"))
    missing_article_count = 0
    total_nouns = 0
    
    for file_path in files:
        if file_path.name == "merged_vocab.json":
            continue
            
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                content = json.load(f)
                
            if not isinstance(content, list):
                continue
                
            for item in content:
                word = item.get("word", "")
                pos = item.get("pos", "")
                
                if pos == "noun":
                    total_nouns += 1
                    lower_word = word.lower().strip()
                    if not (lower_word.startswith("der ") or lower_word.startswith("die ") or lower_word.startswith("das ")):
                        print(f"Missing article in {file_path.name}: '{word}'")
                        missing_article_count += 1
                        
        except Exception as e:
            print(f"Error checking {file_path.name}: {e}")

    print(f"\nTotal nouns checked: {total_nouns}")
    print(f"Nouns missing articles: {missing_article_count}")

if __name__ == "__main__":
    check_missing_articles()
