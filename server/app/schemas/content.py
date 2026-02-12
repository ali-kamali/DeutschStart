from pydantic import BaseModel, Field, ConfigDict
from typing import List, Optional, Dict, Any


class VocabularyItemInput(BaseModel):
    """Schema for a single vocabulary item in an import request."""
    model_config = ConfigDict(populate_by_name=True)

    word: str
    translation_en: str = Field(alias="translation")
    part_of_speech: str = Field(alias="pos")
    category: str
    gender: Optional[str] = None
    plural_form: Optional[str] = None
    example_sentences: Optional[List[Dict[str, str]]] = Field(
        default=[],
        description="List of sentence objects with 'german', 'english' keys",
    )
    gender_mnemonic: Optional[str] = None


class VocabularyImportRequest(BaseModel):
    source_name: str = Field(..., description="e.g. 'chatgpt_seed_100_v1'")
    items: List[VocabularyItemInput]


class GrammarSection(BaseModel):
    title: str
    content: str  # Markdown or HTML


class GrammarTopicInput(BaseModel):
    id: str
    title: str
    description: str
    sequence_order: int
    sections: List[GrammarSection]
    exercises: List[Dict[str, Any]]


class GrammarImportRequest(BaseModel):
    source_name: str
    topics: List[GrammarTopicInput]
