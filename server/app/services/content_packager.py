import json
import zipfile
import shutil
from pathlib import Path
from typing import List, Optional
from datetime import datetime
from app.models.vocabulary import VocabularyItem
from app.services.audio_generator import AudioGenerator
from sqlalchemy.orm import Session
import logging
import time

logger = logging.getLogger(__name__)

from concurrent.futures import ProcessPoolExecutor, as_completed
import os

# Helper for parallel execution needs to be top-level
def _generate_audio_task(args):
    text, output_path, language = args  # Added language
    if output_path.exists():
        return True, str(output_path)
        
    # Initialize generator inside the worker process
    gen = AudioGenerator()
    try:
        gen.generate_audio(text, output_path, language=language)
        return True, str(output_path)
    except Exception as e:
        return False, f"Error generating '{text}': {str(e)}"

class ContentPackager:
    """
    Generates downloadable content packs for Android client.
    Uses a persistent cache for audio files to speed up generation.
    """
    
    def __init__(self, db: Session, output_dir: Path, cache_dir: Optional[Path] = None):
        self.db = db
        self.output_dir = output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # Persistent cache directory (defaults to output_dir/../cache)
        self.cache_dir = cache_dir or self.output_dir.parent / "audio_cache"
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.cache_vocab_dir = self.cache_dir / "vocab"
        self.cache_sent_dir = self.cache_dir / "sentences"
        self.cache_en_dir = self.cache_dir / "english"  # NEW: English translations
        self.cache_vocab_dir.mkdir(exist_ok=True)
        self.cache_sent_dir.mkdir(exist_ok=True)
        self.cache_en_dir.mkdir(exist_ok=True)
        
        self.audio_gen = AudioGenerator()
        
        # Staging for ZIP creation
        self.staging_dir = self.output_dir / "staging"
        self.audio_dir = self.staging_dir / "audio"
        self.vocab_audio_dir = self.audio_dir / "vocab"
        self.sentence_audio_dir = self.audio_dir / "sentences"
        self.english_audio_dir = self.audio_dir / "english"  # NEW
        
        self._init_staging()
        
    def _init_staging(self):
        if self.staging_dir.exists():
            shutil.rmtree(self.staging_dir, ignore_errors=True)
            time.sleep(0.1) # Windows Safety
        self.vocab_audio_dir.mkdir(parents=True, exist_ok=True)
        self.sentence_audio_dir.mkdir(parents=True, exist_ok=True)
        self.english_audio_dir.mkdir(parents=True, exist_ok=True)  # NEW

    def generate_pack(self, version_tag: str = "v1"):
        items = self.db.query(VocabularyItem).all()
        current_time = int(datetime.now().timestamp())
        
        pack_data = []
        tasks = [] # List of (text, path) tuples
        
        # Pass 1: Identification & Task Collection
        logger.info(f"Scanning {len(items)} items for audio generation...")
        
        for item in items:
            # --- Vocab Audio ---
            vocab_filename = f"{item.id}.ogg"
            if item.audio_learn_path:
                existing_name = Path(item.audio_learn_path).name
                if (self.cache_vocab_dir / existing_name).exists():
                     vocab_filename = existing_name

            cached_path = self.cache_vocab_dir / vocab_filename
            
            if not cached_path.exists():
                text = item.word
                if item.article:
                    text = f"{item.article} {item.word}"
                tasks.append((text, cached_path, "de"))  # German vocabulary

            # --- Sentence Audio ---
            sentences = item.example_sentences
            if isinstance(sentences, str):
                try: sentences = json.loads(sentences)
                except: sentences = []
            
            if sentences:
                for idx, sent in enumerate(sentences):
                    sent_text = sent.get("german", "")
                    if not sent_text: continue
                    
                    sent_filename = f"{item.id}_sent_{idx+1}.ogg"
                    if sent.get("original_audio") or sent.get("audio_path"):
                         raw_path = sent.get("original_audio") or sent.get("audio_path")
                         existing_sent_name = Path(raw_path).name
                         if (self.cache_sent_dir / existing_sent_name).exists():
                             sent_filename = existing_sent_name
                         elif (self.cache_vocab_dir / existing_sent_name).exists():
                             # Fallback if mixed
                             pass

                    cached_sent_path = self.cache_sent_dir / sent_filename
                    
                    if not cached_sent_path.exists():
                        tasks.append((sent_text, cached_sent_path, "de"))  # German sentence

            # --- English Translation Audio ---
            if item.translation_en:
                en_filename = f"{item.id}_en.ogg"
                cached_en_path = self.cache_en_dir / en_filename
                
                if not cached_en_path.exists():
                    tasks.append((item.translation_en, cached_en_path, "en"))  # English translation


        # Pass 2: Parallel Generation
        if tasks:
            logger.info(f"Generating audio for {len(tasks)} missing files in parallel...")
            # Use appropriate number of workers (e.g., CPU count)
            # Since we are IO/CPU bound (piper is fast, ffmpeg is CPU), use CPU count.
            max_workers = os.cpu_count() or 4
            
            with ProcessPoolExecutor(max_workers=max_workers) as executor:
                futures = [executor.submit(_generate_audio_task, task) for task in tasks]
                
                for future in as_completed(futures):
                    success, msg = future.result()
                    if not success:
                        logger.error(f"Gen Failed: {msg}")
        else:
            logger.info("All audio files cached. Skipping generation.")

        # Pass 3: Assembly (Copying files)
        logger.info("Assembling pack...")
        
        for item in items:
            # 1. Vocab Audio
            vocab_filename = f"{item.id}.ogg"
            if item.audio_learn_path:
                existing_name = Path(item.audio_learn_path).name
                if (self.cache_vocab_dir / existing_name).exists():
                     vocab_filename = existing_name

            cached_path = self.cache_vocab_dir / vocab_filename
            staging_path = self.vocab_audio_dir / vocab_filename
            
            audio_rel_path = None
            if cached_path.exists():
                staging_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(cached_path, staging_path)
                audio_rel_path = f"audio/vocab/{vocab_filename}"
                
                if not item.audio_learn_path:
                     item.audio_learn_path = audio_rel_path


            # 2. Sentence Audio
            sentences = item.example_sentences
            if isinstance(sentences, str):
                try: sentences = json.loads(sentences)
                except: sentences = []
            
            processed_sentences = []
            if sentences:
                for idx, sent in enumerate(sentences):
                    sent_text = sent.get("german", "")
                    if not sent_text: continue
                    
                    sent_filename = f"{item.id}_sent_{idx+1}.ogg"
                    if sent.get("original_audio") or sent.get("audio_path"):
                         raw_path = sent.get("original_audio") or sent.get("audio_path")
                         existing_sent_name = Path(raw_path).name
                         if (self.cache_sent_dir / existing_sent_name).exists():
                             sent_filename = existing_sent_name

                    cached_sent_path = self.cache_sent_dir / sent_filename
                    staging_sent_path = self.sentence_audio_dir / sent_filename
                    
                    sent_rel_path = None
                    if cached_sent_path.exists():
                        staging_sent_path.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy(cached_sent_path, staging_sent_path)
                        sent_rel_path = f"audio/sentences/{sent_filename}"
                    
                    if sent_rel_path:
                        sent["audio_path"] = sent_rel_path
                        if "original_audio" in sent:
                            del sent["original_audio"]
                    
                    processed_sentences.append(sent)
            
            # 3. English Translation Audio
            en_audio_rel_path = None
            if item.translation_en:
                en_filename = f"{item.id}_en.ogg"
                cached_en_path = self.cache_en_dir / en_filename
                staging_en_path = self.english_audio_dir / en_filename
                
                if cached_en_path.exists():
                    staging_en_path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy(cached_en_path, staging_en_path)
                    en_audio_rel_path = f"audio/english/{en_filename}"
            
            entry = {
                "id": item.id,
                "word": item.word,
                "article": item.article,
                "gender": item.gender,
                "plural": item.plural_form,
                "pos": item.part_of_speech,
                "trans_en": item.translation_en,
                "sentences": processed_sentences
            }
            if audio_rel_path:
                entry["audio"] = audio_rel_path
            if en_audio_rel_path:  # NEW
                entry["audio_en"] = en_audio_rel_path
                
            pack_data.append(entry)

        # 3. Write data.json
        with open(self.staging_dir / "vocabulary.json", "w", encoding="utf-8") as f:
            json.dump(pack_data, f, ensure_ascii=False, indent=2)
            
        # 4. Write manifest
        manifest = {
            "version": version_tag,
            "generated_at": current_time,
            "item_count": len(pack_data),
            "format": "1.0"
        }
        with open(self.staging_dir / "manifest.json", "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)
            
        # 5. Zip it
        zip_filename = f"deutschstart_{version_tag}.zip"
        zip_path = self.output_dir / zip_filename
        
        shutil.make_archive(str(zip_path.with_suffix('')), 'zip', self.staging_dir)
        
        # Cleanup staging (Keep cache!)
        shutil.rmtree(self.staging_dir, ignore_errors=True)
        
        return zip_path
