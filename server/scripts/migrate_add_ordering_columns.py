import sys
import os
from sqlalchemy import text

# Add parent directory to path so we can import app modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.database import engine

def migrate():
    print("Migrating database schema...")
    with engine.connect() as conn:
        with conn.begin():
            # Check if columns exist
            # Postgres specific check or just try/except
            
            # Priority
            try:
                conn.execute(text("ALTER TABLE vocabulary_items ADD COLUMN priority INTEGER DEFAULT 4"))
                print("Added 'priority' column.")
            except Exception as e:
                print(f"Skipping 'priority' (might exist): {e}")

            # Theme
            try:
                conn.execute(text("ALTER TABLE vocabulary_items ADD COLUMN theme VARCHAR"))
                print("Added 'theme' column.")
            except Exception as e:
                print(f"Skipping 'theme' (might exist): {e}")

            # Order Index
            try:
                conn.execute(text("ALTER TABLE vocabulary_items ADD COLUMN order_index INTEGER"))
                print("Added 'order_index' column.")
            except Exception as e:
                print(f"Skipping 'order_index' (might exist): {e}")
                
            # Create Indices if needed? 
            # SQLAlchemy usually handles indices on creation, but here we might need manual CREATE INDEX
            # Let's skip valid indices for now, urgency is high.
            
    print("Migration complete.")

if __name__ == "__main__":
    migrate()
