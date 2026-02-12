import sqlite3
import json
from pathlib import Path
from dataclasses import dataclass
from typing import Optional, List, Dict

@dataclass
class ValidationResult:
    valid: bool
    errors: List[str]
    corrections: Dict[str, str]

class KaikkiValidator:
    def __init__(self, db_path: Optional[str] = None):
        if db_path:
            self.db_path = Path(db_path)
        else:
            # Resolve relative to this file: server/app/validators/ -> server/data/dictionaries/
            current_dir = Path(__file__).resolve().parent
            project_root = current_dir.parent.parent
            self.db_path = project_root / "data" / "dictionaries" / "kaikki_german.db"
            
        self.conn = None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def connect(self):
        if not self.conn:
            self.conn = sqlite3.connect(self.db_path)
    
    def close(self):
        if self.conn:
            self.conn.close()
            self.conn = None

    def validate(self, word: str, claimed_gender: Optional[str] = None, claimed_plural: Optional[str] = None) -> ValidationResult:
        if not self.conn:
            self.connect()
            
        cursor = self.conn.cursor()
        
        # Check existence
        row = cursor.execute(
            "SELECT gender, plural, ipa FROM entries WHERE lemma = ?", (word,)
        ).fetchone()
        
        if not row:
            return ValidationResult(valid=False, errors=[f"'{word}' not found in dictionary"], corrections={})
        
        db_gender, db_plural_json, db_ipa = row
        errors = []
        corrections = {}
        
        # Gender check (for nouns)
        if claimed_gender:
            # db_gender might be "m", "f", "n" or combined
            if not db_gender or claimed_gender not in db_gender:
                errors.append(f"Gender mismatch: Claimed '{claimed_gender}', Found '{db_gender}'")
                corrections["gender"] = db_gender
        
        # Plural check
        if claimed_plural and db_plural_json:
            try:
                valid_plurals = json.loads(db_plural_json)
                if claimed_plural not in valid_plurals:
                    errors.append(f"Plural mismatch: Claimed '{claimed_plural}', Valid options {valid_plurals}")
                    corrections["plural"] = valid_plurals[0] if valid_plurals else ""
            except json.JSONDecodeError:
                pass # Skip if DB data is malformed
                
        return ValidationResult(
            valid=len(errors) == 0,
            errors=errors,
            corrections=corrections
        )
