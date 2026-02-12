from app.celery_app import celery_app
from app.database import SessionLocal
from app.models.vocabulary import VocabularyItem
from app.validators.semantic_qa_report import SemanticQAReport
from pathlib import Path
from datetime import datetime

# Anchored output directory (relative to this file → server/app/tasks → server/)
_SERVER_ROOT = Path(__file__).resolve().parent.parent.parent
_REPORTS_DIR = _SERVER_ROOT / "data" / "processed" / "reports"


@celery_app.task
def generate_qa_report_task():
    db = SessionLocal()
    try:
        items = db.query(VocabularyItem).all()

        reporter = SemanticQAReport(_REPORTS_DIR)
        filename = f"qa_review_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"

        filepath = reporter.generate(items, filename)
        return f"Report generated at {filepath}"
    finally:
        db.close()
