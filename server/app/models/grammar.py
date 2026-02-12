from sqlalchemy import Column, String, Integer, JSON, BigInteger
from app.database import Base

class GrammarTopic(Base):
    __tablename__ = "grammar_topics"

    id = Column(String, primary_key=True, index=True) # e.g., "present_tense"
    title = Column(String, nullable=False)
    description = Column(String, nullable=True)
    sequence_order = Column(Integer, index=True)
    
    # Content stored as JSON
    # Structure: {"sections": [...], "examples": [...]}
    content_json = Column(JSON, nullable=False)
    
    # Exercises stored as JSON
    # Structure: [{"question": "...", "options": [...], "answer": "..."}]
    exercises_json = Column(JSON, nullable=False)
    
    content_hash = Column(String)
    last_updated = Column(BigInteger)
