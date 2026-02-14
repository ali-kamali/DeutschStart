import json
import glob
import os
import requests
import sys
from pathlib import Path

# Configuration
API_BASE = "http://localhost:8000/api/v1"
GRAMMAR_DIR = Path("/app/data/raw/grammar")


def merge_json_files(directory):
    print(f"Scanning for JSON files in: {directory}")
    json_files = glob.glob(os.path.join(directory, "*.json"))
    
    merged_topics = {}
    
    for file_path in json_files:
        print(f"Reading: {os.path.basename(file_path)}")
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                
                # Check structure
                if "topics" not in data:
                    print(f"Skipping {file_path}: Missing 'topics' key")
                    continue
                
                for topic in data["topics"]:
                    # Dedup by ID
                    topic_id = topic.get("id")
                    if not topic_id:
                        print(f"Warning: Topic missing ID in {file_path}")
                        continue
                        
                    if topic_id in merged_topics:
                        print(f"  - Overwriting duplicat topic ID: {topic_id}")
                    
                    merged_topics[topic_id] = topic
                    
        except json.JSONDecodeError:
            print(f"Error decoding JSON in {file_path}")
        except Exception as e:
            print(f"Error reading {file_path}: {e}")

    # Convert back to list, sorted by sequence_order if present
    sorted_topics = sorted(
        merged_topics.values(), 
        key=lambda x: x.get("sequence_order", 999)
    )
    
    print(f"Found {len(sorted_topics)} unique topics.")
    return sorted_topics

def import_grammar(topics):
    if not topics:
        print("No topics to import.")
        return

    payload = {
        "source_name": "merged_script_import",
        "topics": topics
    }
    
    try:
        print("Uploading merged grammar topics...")
        response = requests.post(f"{API_BASE}/import/grammar", json=payload)
        
        if response.status_code == 201:
            print("Import Success:", response.json())
        else:
            print(f"Import Failed: {response.status_code} - {response.text}")
            
    except requests.exceptions.RequestException as e:
        print(f"Network Error: {e}")

def generate_pack():
    print("Triggering pack generation...")
    try:
        response = requests.post(f"{API_BASE}/packs/latest?version_tag=v1_grammar_update")
        if response.status_code == 201:
            print("Pack Generation Success:", response.json())
        else:
            print(f"Pack Generation Failed: {response.status_code} - {response.text}")
    except requests.exceptions.RequestException as e:
        print(f"Network Error: {e}")

if __name__ == "__main__":
    if not GRAMMAR_DIR.exists():
        print(f"Error: Grammar directory not found: {GRAMMAR_DIR}")
        sys.exit(1)
        
    topics = merge_json_files(GRAMMAR_DIR)
    import_grammar(topics)
    
    print("-" * 30)
    print("-" * 30)
    # user_input = input("Generate new content pack now? (y/n): ")
    print("Auto-triggering pack generation...")
    generate_pack()

