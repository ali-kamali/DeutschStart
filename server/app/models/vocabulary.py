from sqlalchemy import Column, Integer, String, JSON, BigInteger
from app.database import Base

class VocabularyItem(Base):
    __tablename__ = "vocabulary"

    id = Column(String, primary_key=True, index=True)
    word = Column(String, index=True, nullable=False)
    article = Column(String, nullable=True) # der/die/das
    gender = Column(String, nullable=True)  # m/f/n
    plural_form = Column(String, nullable=True)
    ipa = Column(String, nullable=True)
    
    # Verb specific
    verb_prefix = Column(String, nullable=True)
    verb_stem = Column(String, nullable=True)
    
    part_of_speech = Column(String, nullable=False)
    translation_en = Column(String, nullable=False)
    
    # Json fields - Example sentences stored as JSON list
    # [{"german": "...", "english": "...", "audio_path": "..."}]
    example_sentences = Column(JSON, nullable=True) 
    
    frequency_rank = Column(Integer, index=True)
    category = Column(String, index=True)
    gender_mnemonic = Column(String, nullable=True)
    
    # Audio paths (relative to content pack root)
    audio_learn_path = Column(String, nullable=True)
    audio_review_path = Column(String, nullable=True)
    
    # Metadata
    generation_source = Column(String) # manual / api
    content_hash = Column(String)      # SHA-256
    last_updated = Column(BigInteger)
