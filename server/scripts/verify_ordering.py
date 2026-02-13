import sys
import os
from sqlalchemy import text

# Add parent directory to path so we can import app modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.database import engine

def verify():
    print("Verifying database content...")
    with engine.connect() as conn:
        # Check specific items
        print("\nChecking specific IDs:")
        try:
            for target_id in ["dreizehn", "ab", "bringen"]:
                 r = conn.execute(text(f"SELECT id, order_index FROM vocabulary WHERE id = '{target_id}'")).fetchone()
                 if r:
                     print(f"ID: {r.id} | Order Index: {r.order_index}")
                 else:
                     print(f"ID: {target_id} not found.")
        except Exception as e:
            print(f"Error checking specific IDs: {e}")

        result = conn.execute(text("SELECT id, word, priority, theme, order_index FROM vocabulary ORDER BY order_index LIMIT 20"))
        rows = result.fetchall()
        
        print(f"Found {len(rows)} rows (showing first 20 ordered by order_index):")
        for row in rows:
            print(f"Index: {row.order_index} | Priority: {row.priority} | Theme: {row.theme} | Word: {row.word}")
            
        # Check for nulls
        null_count = conn.execute(text("SELECT count(*) FROM vocabulary WHERE order_index IS NULL")).scalar()
        print(f"\nItems with NULL order_index: {null_count}")

if __name__ == "__main__":
    verify()
