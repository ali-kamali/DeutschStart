import sys
from pathlib import Path

# Add server root to path
sys.path.append(str(Path(__file__).resolve().parent.parent))

from app.database import SessionLocal
from app.models.grammar import GrammarTopic
from app.models.vocabulary import VocabularyItem

def main():
    db = SessionLocal()
    try:
        vocab_count = db.query(VocabularyItem).count()
        grammar_count = db.query(GrammarTopic).count()
        print(f"Vocabulary Items: {vocab_count}")
        print(f"Grammar Topics: {grammar_count}")
        
        if grammar_count > 0:
            topics = db.query(GrammarTopic).limit(3).all()
            print("Sample topics:", [t.id for t in topics])
    finally:
        db.close()

if __name__ == "__main__":
    main()
