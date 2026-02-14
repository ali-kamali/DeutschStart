import sys
from pathlib import Path
import logging

# Add server root to path
sys.path.append(str(Path(__file__).resolve().parent.parent))

from app.database import SessionLocal
from app.services.content_packager import ContentPackager

# Setup logging
logging.basicConfig(level=logging.INFO)

def main():
    db = SessionLocal()
    try:
        # Output to a temp dir
        output_dir = Path(__file__).resolve().parent.parent / "data" / "processed" / "packs_debug"
        output_dir.mkdir(parents=True, exist_ok=True)
        
        packager = ContentPackager(db, output_dir)
        zip_path = packager.generate_pack(version_tag="debug_v1")
        
        print(f"Pack generated at: {zip_path}")
        
        # Verify if grammar.json is in the zip
        import zipfile
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            files = zip_ref.namelist()
            print("Files in ZIP:")
            for f in files:
                print(f" - {f}")
                
            if "grammar.json" in files:
                print("SUCCESS: grammar.json found in ZIP.")
            else:
                print("FAILURE: grammar.json NOT found in ZIP.")
                
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        db.close()

if __name__ == "__main__":
    main()
