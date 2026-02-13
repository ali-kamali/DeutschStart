
import sqlite3
from pathlib import Path

# Path to the specific extracted DB
DB_PATH = Path("e:/Health/Germany/server/data/anki_export/Starten_wir_A1__German_Vocabulary__Sentences_with_Audio/collection.anki2")

print(f"Connecting to: {DB_PATH}")

try:
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # List tables
    print("\n--- TABLES ---")
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = cursor.fetchall()
    for t in tables:
        print(t[0])

    if ('notes',) in tables:
        # Get fields definition if possible (models table)
        # Also print the fields content
        cursor.execute("SELECT flds FROM notes LIMIT 3")
        rows = cursor.fetchall()

        print("\n--- RAW NOTES DUMP ---")
        for i, (flds,) in enumerate(rows):
            print(f"\nNote {i+1}:")
            fields = flds.split('\x1f')
            for idx, field in enumerate(fields):
                # truncated for display to avoid huge dumps but show enough context
                content = field[:100].replace('\n', ' ')
                print(f"  Field {idx}: {content}...")
    else:
        print("\nERROR: 'notes' table not found!")

    conn.close()

except Exception as e:
    print(f"ERROR: {e}")
