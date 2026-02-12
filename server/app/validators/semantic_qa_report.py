import csv
import json
from pathlib import Path
from typing import List
from app.models.vocabulary import VocabularyItem
from app.validators.cefr_a1_checker import A1ConstraintChecker
from app.validators.kaikki_validator import KaikkiValidator


class SemanticQAReport:
    """
    Generates a CSV report for human review of generated content.
    Flags potential issues (A1 violations, Dictionary mismatches).
    """

    def __init__(self, output_dir: Path):
        self.output_dir = output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.a1_checker = A1ConstraintChecker()

    def generate(self, items: List[VocabularyItem], filename: str = "qa_review.csv"):
        filepath = self.output_dir / filename

        # Use Kaikki (context manager)
        with KaikkiValidator() as kv:
            with open(filepath, "w", newline="", encoding="utf-8-sig") as f:
                writer = csv.writer(f)
                header = [
                    "ID", "Word", "POS", "Gen/Pl [DB]", "Gen/Pl [Kaikki]",
                    "Sentence", "A1 Check", "Kaikki Check", "Action (Keep/Regen/Edit)",
                ]
                writer.writerow(header)

                for item in items:
                    # 1. Validate against Kaikki
                    kaikki_res = kv.validate(item.word, item.gender, item.plural_form)
                    kaikki_status = "OK" if kaikki_res.valid else f"FAIL: {kaikki_res.errors}"

                    # Show actual DB values when valid, corrections when invalid
                    if kaikki_res.valid:
                        kaikki_info = f"{item.gender or '–'} / {item.plural_form or '–'}"
                    else:
                        kaikki_info = (
                            f"{kaikki_res.corrections.get('gender', item.gender or '–')}"
                            f" / "
                            f"{kaikki_res.corrections.get('plural', item.plural_form or '–')}"
                        )

                    # 2. Check sentences
                    sentences = item.example_sentences

                    if not sentences:
                        writer.writerow([
                            item.id, item.word, item.part_of_speech,
                            f"{item.gender}/{item.plural_form}", kaikki_info,
                            "(No sentences)", "N/A", kaikki_status, "",
                        ])
                        continue

                    # Ensure sentences is list of dicts (handle legacy string storage)
                    if isinstance(sentences, str):
                        try:
                            sentences = json.loads(sentences)
                        except (json.JSONDecodeError, TypeError):
                            sentences = []

                    for sent in sentences:
                        text = sent.get("german", "")
                        a1_errors = self.a1_checker.check(text, vocab_whitelist=None)
                        a1_status = "OK" if not a1_errors else f"WARN: {a1_errors}"

                        writer.writerow([
                            item.id, item.word, item.part_of_speech,
                            f"{item.gender}/{item.plural_form}", kaikki_info,
                            text, a1_status, kaikki_status, "",
                        ])

        return filepath
