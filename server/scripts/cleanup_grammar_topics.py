import sys
import os
from pathlib import Path

# Add server root to path so we can import 'app'
sys.path.append(str(Path(__file__).resolve().parent.parent))

from app.database import SessionLocal
from app.models.grammar import GrammarTopic
from sqlalchemy import text

def main():
    print("Connecting to database...")
    db = SessionLocal()
    try:
        print("Deleting all grammar topics...")
        # Use query.delete() for ORM deletion
        count = db.query(GrammarTopic).delete()
        db.commit()
        print(f"Successfully deleted {count} grammar topics.")
    except Exception as e:
        print(f"Error during cleanup: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    main()
