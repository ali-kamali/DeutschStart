from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from app.database import get_db
from app.services.content_packager import ContentPackager
from pathlib import Path
import os
import re

router = APIRouter()

# Anchored path
_SERVER_ROOT = Path(__file__).resolve().parent.parent.parent.parent
PACKS_DIR = _SERVER_ROOT / "data" / "processed" / "packs"

# Strict filename pattern: alphanumeric, hyphens, underscores, dots, ending in .zip
_SAFE_FILENAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*\.zip$")


@router.post("/latest", status_code=status.HTTP_201_CREATED)
def generate_pack(version_tag: str = "v1", db: Session = Depends(get_db)):
    """
    Trigger generation of a new content pack.
    Ideally this should be a background task, but for MVP we run sync to debug.
    WARNING: This will take time if TTS cache is cold!
    """
    try:
        packager = ContentPackager(db, PACKS_DIR)
        zip_path = packager.generate_pack(version_tag)
        return {"message": "Pack generated successfully", "path": str(zip_path), "url": f"/api/v1/packs/{zip_path.name}"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/latest")
def get_latest_pack():
    """
    Get metadata for the latest available content pack.
    Searches provided PACKS_DIR for zip files and reads their embedded manifest or sorts by time.
    For MVP: Just find the newest ZIP file.
    """
    if not PACKS_DIR.exists():
        raise HTTPException(status_code=404, detail="No packs found")
        
    zips = list(PACKS_DIR.glob("*.zip"))
    if not zips:
        raise HTTPException(status_code=404, detail="No packs found")
    
    # Sort by modification time (newest first)
    latest_zip = max(zips, key=os.path.getmtime)
    
    return {
        "filename": latest_zip.name,
        "url": f"/api/v1/packs/{latest_zip.name}",
        "size": latest_zip.stat().st_size,
        "created_at": latest_zip.stat().st_mtime
    }

@router.get("/{filename}")
async def download_pack(filename: str):
    # 1. Strict allowlist: reject anything that isn't a simple .zip filename
    if not _SAFE_FILENAME_RE.match(filename):
        raise HTTPException(status_code=400, detail="Invalid filename format")

    # 2. Strip any path component (belt-and-suspenders)
    safe_name = os.path.basename(filename)
    if safe_name != filename:
        raise HTTPException(status_code=400, detail="Invalid filename format")

    # 3. Build the absolute path and verify containment
    abs_root = os.path.realpath(str(PACKS_DIR))
    abs_file = os.path.realpath(os.path.join(abs_root, safe_name))

    # Guard: the resolved path MUST start with the root directory + separator.
    # This is the canonical pattern that CodeQL recognises as a path-traversal sanitiser.
    if not abs_file.startswith(abs_root + os.sep):
        raise HTTPException(status_code=403, detail="Access denied")

    # 4. Existence check on the validated path
    if not os.path.isfile(abs_file):
        raise HTTPException(status_code=404, detail="Pack not found")

    return FileResponse(
        path=abs_file,
        filename=safe_name,
        media_type="application/zip"
    )
