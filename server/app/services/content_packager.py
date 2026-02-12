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
        self.cache_vocab_dir.mkdir(exist_ok=True)
        self.cache_sent_dir.mkdir(exist_ok=True)
        
        self.audio_gen = AudioGenerator()
        
        # Staging for ZIP creation
        self.staging_dir = self.output_dir / "staging"
        self.audio_dir = self.staging_dir / "audio"
        self.vocab_audio_dir = self.audio_dir / "vocab"
        self.sentence_audio_dir = self.audio_dir / "sentences"
        
        self._init_staging()
        
    def _init_staging(self):
        if self.staging_dir.exists():
            shutil.rmtree(self.staging_dir, ignore_errors=True)
            time.sleep(0.1) # Windows Safety
        self.vocab_audio_dir.mkdir(parents=True, exist_ok=True)
        self.sentence_audio_dir.mkdir(parents=True, exist_ok=True)

    def generate_pack(self, version_tag: str = "v1"):
        items = self.db.query(VocabularyItem).all()
        current_time = int(datetime.now().timestamp())
        
        pack_data = []
        
        for item in items:
            # 1. Generate/Fetch Vocab Audio
            vocab_filename = f"{item.id}.ogg"
            cached_path = self.cache_vocab_dir / vocab_filename
            staging_path = self.vocab_audio_dir / vocab_filename
            
            audio_rel_path = None
            
            # Check cache
            if cached_path.exists():
                staging_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(cached_path, staging_path)
                audio_rel_path = f"audio/vocab/{vocab_filename}"
            else:
                # Generate
                text = item.word
                if item.article:
                    text = f"{item.article} {item.word}"
                
                try:
                    self.audio_gen.generate_audio(text, cached_path)
                    staging_path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy(cached_path, staging_path)
                    audio_rel_path = f"audio/vocab/{vocab_filename}"
                    
                    # Update DB metadata (optional, good for debugging)
                    item.audio_learn_path = audio_rel_path
                except Exception as e:
                    logger.error(f"Audio Gen Failed for {item.word}: {e}")

            # 2. Generate/Fetch Sentence Audio
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
                    cached_sent_path = self.cache_sent_dir / sent_filename
                    staging_sent_path = self.sentence_audio_dir / sent_filename
                    
                    sent_rel_path = None
                    
                    if cached_sent_path.exists():
                        staging_sent_path.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy(cached_sent_path, staging_sent_path)
                        sent_rel_path = f"audio/sentences/{sent_filename}"
                    else:
                        try:
                            self.audio_gen.generate_audio(sent_text, cached_sent_path)
                            staging_sent_path.parent.mkdir(parents=True, exist_ok=True)
                            shutil.copy(cached_sent_path, staging_sent_path)
                            sent_rel_path = f"audio/sentences/{sent_filename}"
                        except Exception as e:
                            logger.error(f"Audio Gen Failed for sentence '{sent_text}': {e}")
                    
                    if sent_rel_path:
                        sent["audio_path"] = sent_rel_path
                    
                    processed_sentences.append(sent)
            
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
            
            # Only add audio key if it exists
            if audio_rel_path:
                entry["audio"] = audio_rel_path
                
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
