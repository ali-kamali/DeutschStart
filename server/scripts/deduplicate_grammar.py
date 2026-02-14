import json
import os
import shutil
from pathlib import Path
from typing import Dict, List, Any

# Paths
SERVER_ROOT = Path(__file__).resolve().parent.parent
RAW_GRAMMAR_DIR = SERVER_ROOT.parent / "data" / "raw" / "grammar"
ARCHIVE_DIR = RAW_GRAMMAR_DIR / "archived_raw"
CONSOLIDATED_FILE = RAW_GRAMMAR_DIR / "grammar_a1_consolidated.json"

# Merge Rules: target_id -> [source_ids]
# The target_id will be the final topic ID.
# If target_id doesn't exist in source files, it will be created using the first source_id found.
MERGE_RULES = {
    # --- Exact Duplicates ---
    "prepositions_dative": ["dative_prepositions", "prepositions_dative"],
    "coordinating_conjunctions": ["conjunctions_basic", "coordinating_conjunctions"],
    "imperative_master": ["imperative_mood", "imperative_commands", "imperative_wir"],
    "time_expressions_master": ["time_expressions", "time_expressions_basic", "dates_and_ordinal_numbers", "reading_years", "telling_time_informal", "calendar_review", "time_prepositions_ab_bis"],
    "adjectives_master": ["adjective_basics", "predicate_adjectives", "comparative_superlative", "intensifiers_sehr_zu", "expressions_of_frequency_jeden"], # Grouping adj/adv modifiers here too
    "negation_master": ["negation_kein_nicht", "negation_detailed", "something_and_nothing"],
    "countries_nationalities_master": ["countries_and_languages", "nationalities"],

    # --- Strong Overlaps ---
    "pronouns_personal_master": ["personal_pronouns", "pronouns_accusative", "pronouns_dative"],
    "pronouns_other_master": ["indefinite_pronouns_man", "demonstrative_pronouns_dieser", "indefinite_pronouns_people"],
    
    "possession_master": ["possessive_articles", "possessive_articles_accusative", "possessive_names"],
    
    # Prepositions (Merging all into one big topic, or maybe 2-3 large ones? User suggested one big topic)
    # Let's do: Prepositions (Acc/Dat/Two-Way)
    "prepositions_master": ["prepositions_accusative", "two_way_prepositions", "preposition_seit", "materials_aus", "destinations_nach_in_zu", "zu_hause_nach_hause"],
    
    "word_order_master": ["sentence_structure", "time_manner_place", "word_order_inversion", "satzklammer_review", "adverbs_of_sequence", "adverbs_of_place", "adverbs_of_time", "directional_adverbs_hin_her"], # Grouping adverbs here as they relate to structure
    
    "questions_master": ["question_words", "question_word_welcher", "question_word_was_fuer_ein", "answering_with_doch"],
    
    "reasons_conditions_master": ["subordinating_conjunctions", "subordinating_conjunction_wenn", "giving_reasons_warum_weil", "adverb_deshalb"],
    
    "likes_requests_master": ["expressing_likes_gern", "verb_moegen", "modal_verb_moechten", "polite_requests_haette_gern", "taste_and_preference"],
    
    "numbers_weights_master": ["numbers_and_prices", "measurements_and_weights"],
    
    "dative_master": ["dative_case_intro", "dative_verbs"],
    
    # --- Others to Bundle ---
    "verbs_irregular_master": ["present_tense_irregular", "verb_tun", "wissen_vs_kennen", "verb_brauchen", "verb_lassen"],
    "verbs_regular_master": ["present_tense_regular", "reflexive_verbs_intro", "inseparable_verbs", "noun_verb_combinations"],
    
    "modal_verbs_master": ["modal_verbs", "modal_verbs_past"],
    
    "nouns_articles_master": ["noun_gender", "definite_articles", "indefinite_articles", "compound_nouns", "noun_formation", "diminutives", "plural_forms", "nominative_case", "accusative_case"], # Bundling all basics
    
    "perfect_tense_master": ["perfect_tense", "simple_past_sein_haben", "expressing_future_with_present"] # Tense review
}

def load_all_topics() -> Dict[str, Any]:
    """Scans RAW_GRAMMAR_DIR for .json files and loads them."""
    all_topics = {}
    
    if not RAW_GRAMMAR_DIR.exists():
        print(f"Error: {RAW_GRAMMAR_DIR} does not exist.")
        return {}

    # Read in alphabetical order (00, 01, ...) so later files overwrite earlier ones if duplicate IDs exist exactly
    files = sorted([f for f in RAW_GRAMMAR_DIR.glob("*.json") if f.is_file()])
    
    print(f"Found {len(files)} JSON files.")
    
    for file_path in files:
        if file_path.name == "grammar_a1_consolidated.json":
            continue
            
        print(f"Reading {file_path.name}...")
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                
            for topic in data.get("topics", []):
                tid = topic.get("id")
                if tid:
                    # Latest file wins for exact ID match
                    all_topics[tid] = topic
        except Exception as e:
            print(f"Error reading {file_path.name}: {e}")
            
    return all_topics

def merge_topics(all_topics: Dict[str, Any]) -> List[Any]:
    merged_topics = []
    processed_ids = set()

    # 1. Process Merge Rules
    for target_id, source_ids in MERGE_RULES.items():
        # Collect all source topics that exist
        sources = [all_topics[sid] for sid in source_ids if sid in all_topics]
        
        if not sources:
            continue
            
        print(f"Merging {len(sources)} topics into '{target_id}'")
        
        # Base topic is the first one found (usually the 'main' one if it exists, or the first in the list)
        # We try to keep the target_id if it exists in sources, otherwise rename the first one
        base_topic = None
        
        # Check if target_id is actually one of the source_ids (e.g. prepositions_dative)
        if target_id in all_topics:
            base_topic = all_topics[target_id].copy()
        else:
            # Create a fresh topic based on the first source
            base_topic = sources[0].copy()
            base_topic["id"] = target_id
            base_topic["title"] = base_topic["title"] + " (Combined)" # Temporary title update
            base_topic["description"] = f"Start: {base_topic['description']}"
            base_topic["sections"] = [] # Clear sections to append in order
            base_topic["exercises"] = []

        # Assuming we want to aggregate SECTIONS and EXERCISES
        # If we are creating a new Master topic, we should aggregates all content from sources.
        # If 'base_topic' was one of the sources, we need to be careful not to duplicate its content if we iterate over it again.
        
        # Let's create a strictly aggregated topic
        final_topic = {
            "id": target_id,
            "title": format_title(target_id),
            "description": "Comprehensive guide covering: " + ", ".join([s["title"] for s in sources]),
            "sequence_order": min([s.get("sequence_order", 999) for s in sources]), # Take earliest order
            "sections": [],
            "exercises": []
        }
        
        for src in sources:
            # Add a section header if merging different topics
            if len(sources) > 1:
                final_topic["sections"].append({
                    "title": f"--- {src['title']} ---",
                    "content": src.get("description", "")
                })
            
            final_topic["sections"].extend(src.get("sections", []))
            final_topic["exercises"].extend(src.get("exercises", []))
            
            processed_ids.add(src["id"])
            
        merged_topics.append(final_topic)

    # 2. Add remaining topics that weren't merged
    for tid, topic in all_topics.items():
        if tid not in processed_ids:
            merged_topics.append(topic)
            
    # Sort by sequence order
    merged_topics.sort(key=lambda x: x.get("sequence_order", 999))
    
    # Re-assign sequence orders to be contiguous 1..N
    for i, topic in enumerate(merged_topics):
        topic["sequence_order"] = i + 1
        
    return merged_topics

def format_title(tid: str) -> str:
    # Helper to make nice titles for master topics
    titles = {
        "imperative_master": "Imperative Mood (Commands)",
        "time_expressions_master": "Time, Dates & Years",
        "adjectives_master": "Adjectives & Comparisons",
        "negation_master": "Negation (nicht / kein)",
        "countries_nationalities_master": "Countries & Nationalities",
        "pronouns_personal_master": "Personal Pronouns (Nom / Acc / Dat)",
        "pronouns_other_master": "Other Pronouns (man / dieser / jemand)",
        "possession_master": "Possession (mein / dein / sein...)",
        "prepositions_master": "Prepositions (Acc / Dat / Two-Way)",
        "word_order_master": "Sentence Structure & Word Order",
        "questions_master": "Asking Questions (W-Words, Yes/No)",
        "reasons_conditions_master": "Reasons & Conditions (weil, wenn, deshalb)",
        "likes_requests_master": "Expressing Likes & Requests",
        "numbers_weights_master": "Numbers, Prices & Weights",
        "dative_master": "The Dative Case (Intro)",
        "verbs_irregular_master": "Common Irregular Verbs",
        "verbs_regular_master": "Regular & Separable Verbs",
        "modal_verbs_master": "Modal Verbs",
        "nouns_articles_master": "Nouns, Articles & Cases (Basics)",
        "perfect_tense_master": "The Past Tense (Perfekt & Präteritum)"
    }
    return titles.get(tid, tid.replace("_", " ").title())

def main():
    print("Step 1: Loading topics...")
    all_topics = load_all_topics()
    
    print("Step 2: Merging topics...")
    merged_list = merge_topics(all_topics)
    
    print(f"Total topics after merge: {len(merged_list)}")
    
    # Step 3: Archive old files
    if not ARCHIVE_DIR.exists():
        ARCHIVE_DIR.mkdir(parents=True)
        
    for f in RAW_GRAMMAR_DIR.glob("*.json"):
        if f.name == "grammar_a1_consolidated.json":
            continue
        print(f"Archiving {f.name}...")
        shutil.move(str(f), str(ARCHIVE_DIR / f.name))
        
    # Step 4: Write new file
    output_data = {
        "source_name": "grammar_deduplicated_v1",
        "topics": merged_list
    }
    
    with open(CONSOLIDATED_FILE, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, indent=2, ensure_ascii=False)
        
    print(f"Successfully wrote {CONSOLIDATED_FILE}")
    print("Done.")

if __name__ == "__main__":
    main()
