import subprocess
import json
import logging
from pathlib import Path
from tempfile import NamedTemporaryFile

logger = logging.getLogger(__name__)

class AudioGenerator:
    """
    Wrapper around Piper TTS for generating German audio.
    Uses the 'thorsten-high' model downloaded in Dockerfile.
    """
    
    def __init__(self, model_path: str = "/app/piper-voices/de_DE-thorsten-high.onnx", piper_binary: str = "/app/piper/piper"):
        self.model_path = Path(model_path)
        self.piper_binary = Path(piper_binary)
        
        if not self.model_path.exists():
            logger.warning(f"Piper model not found at {self.model_path}. TTS will fail.")
        if not self.piper_binary.exists():
            logger.warning(f"Piper binary not found at {self.piper_binary}. TTS will fail.")

    def generate_audio(self, text: str, output_path: Path):
        """
        Generate audio from text using Piper TTS.
        Output format is WAV (Piper default), then converted to OGG Vorbis via ffmpeg.
        """
        if not text:
            raise ValueError("Text cannot be empty")
            
        output_path.parent.mkdir(parents=True, exist_ok=True)
        
        # Piper expects JSON input for batch or simple text via stdin
        # cmd: echo 'Text' | piper --model ... --output_file ...
        
        # Test mode: If binary missing, just touch the file
        if not self.piper_binary.exists():
            logger.warning(f"Piper binary missing. Creating dummy audio file at {output_path}")
            with open(output_path, "wb") as f:
                f.write(b"DUMMY_AUDIO_CONTENT")
            return output_path

        try:
            # 1. Generate WAV to temp file
            with NamedTemporaryFile(suffix=".wav", delete=False) as tmp_wav:
                tmp_wav_path = Path(tmp_wav.name)
            
            # Simple text-to-speech
            cmd = [
                str(self.piper_binary),
                "--model", str(self.model_path),
                "--output_file", str(tmp_wav_path)
            ]
            
            # Pipe text to stdin
            process = subprocess.run(
                cmd,
                input=text.encode("utf-8"),
                check=True,
                capture_output=True
            )
            
            # 2. Convert to OGG Vorbis with ffmpeg (Quality 4 ~ 128kbps, -14 LUFS normalization)
            # Loudness normalization: loudnorm=I=-14:TP=-1.5:LRA=11
            ffmpeg_cmd = [
                "ffmpeg", "-y",
                "-i", str(tmp_wav_path),
                "-af", "loudnorm=I=-14:TP=-1.5:LRA=11",
                "-c:a", "libvorbis",
                "-q:a", "4",
                str(output_path)
            ]
            
            subprocess.run(ffmpeg_cmd, check=True, capture_output=True)
            
            # Cleanup temp WAV
            tmp_wav_path.unlink(missing_ok=True)
            
            return output_path

        except subprocess.CalledProcessError as e:
            logger.error(f"TTS Error: {e.stderr.decode() if e.stderr else str(e)}")
            raise RuntimeError(f"Failed to generate audio for '{text}'") from e
        except FileNotFoundError as e:
            logger.error(f"Dependency not found: {e}")
            raise RuntimeError(f"Missing system dependency (ffmpeg/piper): {e}") from e
