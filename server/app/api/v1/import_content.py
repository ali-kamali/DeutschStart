from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.schemas.content import VocabularyImportRequest, GrammarImportRequest
from app.models.vocabulary import VocabularyItem
from app.models.grammar import GrammarTopic
from app.tasks.pipeline import generate_qa_report_task
import json
from pathlib import Path
import os
import re
from datetime import datetime

router = APIRouter()

# Anchored directory structure (relative to this file → server/app/api/v1 → server/)
_SERVER_ROOT = Path(__file__).resolve().parent.parent.parent.parent
RAW_DATA_DIR = _SERVER_ROOT / "data" / "raw"
RAW_VOCAB_DIR = RAW_DATA_DIR / "vocab"
RAW_GRAMMAR_DIR = RAW_DATA_DIR / "grammar"

# Strict source-name pattern: only alphanumeric, underscores, hyphens
_SAFE_SOURCE_RE = re.compile(r"^[A-Za-z0-9_-]+$")


def _ensure_dirs():
    """Create raw data directories if they don't exist (lazy init, not at import time)."""
    RAW_VOCAB_DIR.mkdir(parents=True, exist_ok=True)
    RAW_GRAMMAR_DIR.mkdir(parents=True, exist_ok=True)


def _safe_source_name(raw: str | None) -> str:
    """Return a validated source name, or a safe default."""
    if raw and _SAFE_SOURCE_RE.match(raw):
        return raw
    return "unknown_source"


@router.post("/vocabulary", status_code=status.HTTP_201_CREATED)
async def import_vocabulary(request: VocabularyImportRequest, db: Session = Depends(get_db)):
    """
    Import vocabulary JSON from ChatGPT/Manual sources.
    Saves raw JSON to disk for audit, then upserts into DB.
    """
    _ensure_dirs()

    # 1. Save Raw JSON
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    safe_source = _safe_source_name(request.source_name)
    filename = f"{timestamp}_{safe_source}.json"

    # Build and validate the output path (inlined for CodeQL taint tracking)
    abs_root = os.path.realpath(str(RAW_VOCAB_DIR))
    abs_file = os.path.realpath(os.path.join(abs_root, filename))

    if not abs_file.startswith(abs_root + os.sep):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid source name for vocabulary import.",
        )

    with open(abs_file, "w", encoding="utf-8") as f:
        json.dump(request.model_dump(by_alias=True), f, indent=2, ensure_ascii=False)

    # 2. Upsert into Database
    count = 0
    for item in request.items:
        vocab_id = item.word.lower().strip()

        db_item = db.query(VocabularyItem).filter(VocabularyItem.id == vocab_id).first()
        if not db_item:
            db_item = VocabularyItem(id=vocab_id, word=item.word)
            db.add(db_item)

        # Update fields
        db_item.translation_en = item.translation_en
        db_item.part_of_speech = item.part_of_speech
        db_item.category = item.category
        if item.gender:
            db_item.gender = item.gender
        if item.plural_form:
            db_item.plural_form = item.plural_form
        if item.gender_mnemonic:
            db_item.gender_mnemonic = item.gender_mnemonic
        if item.example_sentences:
            db_item.example_sentences = item.example_sentences
        
        # Ordering Fields
        if item.priority:
            db_item.priority = item.priority
        if item.theme:
            db_item.theme = item.theme
        if item.order_index is not None:
            db_item.order_index = item.order_index
        
        # Kaikki Data
        if item.kaikki_data:
            db_item.kaikki_data = item.kaikki_data
        if item.kaikki_audio_path:
            db_item.kaikki_audio_path = item.kaikki_audio_path

        # Metadata
        db_item.generation_source = request.source_name
        db_item.last_updated = int(datetime.now().timestamp())

        count += 1

    db.commit()
    return {"message": f"Successfully imported {count} items", "file_saved": abs_file}


@router.post("/grammar", status_code=status.HTTP_201_CREATED)
async def import_grammar(request: GrammarImportRequest, db: Session = Depends(get_db)):
    """
    Import grammar topics.
    """
    _ensure_dirs()

    # 1. Save Raw JSON
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    safe_source = _safe_source_name(request.source_name)
    filename = f"{timestamp}_{safe_source}.json"

    # Build and validate the output path (inlined for CodeQL taint tracking)
    abs_root = os.path.realpath(str(RAW_GRAMMAR_DIR))
    abs_file = os.path.realpath(os.path.join(abs_root, filename))

    if not abs_file.startswith(abs_root + os.sep):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file path",
        )

    with open(abs_file, "w", encoding="utf-8") as f:
        json.dump(request.model_dump(), f, indent=2, ensure_ascii=False)

    # 2. Upsert
    count = 0
    for topic in request.topics:
        db_topic = db.query(GrammarTopic).filter(GrammarTopic.id == topic.id).first()
        if not db_topic:
            db_topic = GrammarTopic(id=topic.id, title=topic.title)
            db.add(db_topic)

        db_topic.title = topic.title
        db_topic.description = topic.description
        db_topic.sequence_order = topic.sequence_order
        db_topic.content_json = [s.model_dump() for s in topic.sections]
        db_topic.exercises_json = topic.exercises

        db_topic.last_updated = int(datetime.now().timestamp())
        count += 1

    db.commit()
    return {"message": f"Successfully imported {count} grammar topics", "file_saved": abs_file}


@router.post("/generate-qa-report", status_code=status.HTTP_202_ACCEPTED)
async def trigger_qa_report():
    """
    Trigger generation of Semantic QA Report (CSV).
    Task runs in background via Celery.
    """
    task = generate_qa_report_task.delay()
    return {"message": "Report generation started", "task_id": str(task.id)}
