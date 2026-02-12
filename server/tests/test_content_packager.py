import unittest
import shutil
import json
import zipfile
import tempfile
import logging
import traceback
from pathlib import Path
from app.services.content_packager import ContentPackager
from app.models.vocabulary import VocabularyItem
from unittest.mock import MagicMock, patch

# Configure logging to swallow errors during tests
logging.basicConfig(level=logging.CRITICAL)

class TestContentPackager(unittest.TestCase):
    def setUp(self):
        # Use TemporaryDirectory for robust cleanup on Windows
        self.test_dir_obj = tempfile.TemporaryDirectory()
        self.cache_dir_obj = tempfile.TemporaryDirectory()
        
        self.test_dir = Path(self.test_dir_obj.name)
        self.cache_dir = Path(self.cache_dir_obj.name)
        
        # Mock DB
        self.mock_db = MagicMock()
        
        self.item1 = VocabularyItem(
            id="hund", word="Hund", article="der", 
            translation_en="dog", part_of_speech="noun",
            gender="m", plural_form="Hunde",
            example_sentences=[{"german": "Der Hund bellt.", "english": "The dog barks."}]
        )
        self.item2 = VocabularyItem(
            id="katze", word="Katze", article="die",
            translation_en="cat", part_of_speech="noun",
            gender="f", plural_form="Katzen",
            example_sentences=[]
        )
        
        self.mock_db.query.return_value.all.return_value = [self.item1, self.item2]

    def tearDown(self):
        self.test_dir_obj.cleanup()
        self.cache_dir_obj.cleanup()

    def test_pack_generation_and_caching(self):
        """Test pack generation, caching, and structure."""
        packager = ContentPackager(self.mock_db, self.test_dir, self.cache_dir)
        
        # 1. First Run: Should generate audio (using dummy generation)
        # We need to ensure we are not mocking audio_gen here so it creates dummy files
        zip_path_1 = packager.generate_pack("v1")
        
        self.assertTrue(zip_path_1.exists(), "ZIP not created in Run 1")
        
        # Verify cache was populated
        self.assertTrue((self.cache_dir / "vocab/hund.ogg").exists(), "Cache missing vocab audio")
        self.assertTrue((self.cache_dir / "sentences/hund_sent_1.ogg").exists(), "Cache missing sentence audio")
        
        # 2. Second Run: Should reuse cache
        # We mock generate_audio to raise Exception. If cache works, it WON'T call this.
        with patch.object(packager.audio_gen, 'generate_audio', side_effect=Exception("Should use cache!")) as mock_gen:
            zip_path_2 = packager.generate_pack("v2")
            self.assertTrue(zip_path_2.exists(), "ZIP not created in Run 2")
            
            # Verify manifest version
            with zipfile.ZipFile(zip_path_2, 'r') as z:
                with z.open("manifest.json") as f:
                    m = json.load(f)
                    self.assertEqual(m["version"], "v2")
                    
    def test_audio_failure_handling(self):
        """Test that if audio generation fails, the JSON entry lacks the audio key."""
        try:
            packager = ContentPackager(self.mock_db, self.test_dir, self.cache_dir)
            
            # Force generation failure on run 1
            with patch.object(packager.audio_gen, 'generate_audio', side_effect=Exception("TTS Failed")):
                zip_path = packager.generate_pack("v3")
                
                self.assertTrue(zip_path.exists())
                
                with zipfile.ZipFile(zip_path, 'r') as z:
                    # Check manifest
                    with z.open("vocabulary.json") as f:
                        vocab = json.load(f)
                        hund = next(i for i in vocab if i["id"] == "hund")
                        
                        # Should NOT have 'audio' key because gen failed
                        self.assertNotIn("audio", hund)
                    
                    # Check zip content - audio file should NOT be there
                    self.assertNotIn("audio/vocab/hund.ogg", z.namelist())
        except Exception:
            traceback.print_exc()
            raise

if __name__ == '__main__':
    unittest.main()
