import json
from pathlib import Path
from app.validators.kaikki_validator import KaikkiValidator

# Robust path resolution
CURRENT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = CURRENT_DIR.parent.parent
SEED_PATH = PROJECT_ROOT / "data" / "seed" / "a1_frequency_400.json"

def validate_seed():
    if not SEED_PATH.exists():
        print(f"Seed file not found at {SEED_PATH}")
        return

    print(f"Validating seed list: {SEED_PATH}")
    with open(SEED_PATH, "r", encoding="utf-8") as f:
        vocab_list = json.load(f)

    # Use context manager
    with KaikkiValidator() as validator:
        passed = 0
        failed = 0
        
        print(f"{'Word':<20} {'Status':<10} {'Notes'}")
        print("-" * 60)
        
        for item in vocab_list:
            word = item["word"]
            
            result = validator.validate(word)
            
            if result.valid:
                passed += 1
                print(f"{word:<20} OK")
            else:
                failed += 1
                print(f"{word:<20} FAIL       {result.errors}")

        print("-" * 60)
        print(f"Total: {len(vocab_list)}")
        print(f"Passed: {passed}")
        print(f"Failed: {failed}")

if __name__ == "__main__":
    validate_seed()
