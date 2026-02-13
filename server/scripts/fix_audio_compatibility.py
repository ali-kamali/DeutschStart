#!/usr/bin/env python3
import os
import subprocess
import argparse
from pathlib import Path
import shutil

def get_sample_rate(file_path):
    """Returns the sample rate of the audio file using ffprobe."""
    try:
        cmd = [
            "ffprobe", 
            "-v", "error", 
            "-select_streams", "a:0", 
            "-show_entries", "stream=sample_rate", 
            "-of", "default=noprint_wrappers=1:nokey=1", 
            str(file_path)
        ]
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
        return int(result.stdout.strip())
    except (subprocess.CalledProcessError, ValueError):
        return None

def convert_audio(file_path, target_rate=22050):
    """Converts audio to the target sample rate using ffmpeg."""
    # Create temp file with same extension so ffmpeg detects format correctly
    temp_path = file_path.parent / f"{file_path.stem}_temp{file_path.suffix}"
    
    # Basic conversion maintaining codec if possible (ffmpeg auto-detects from extension)
    # forcing mono (-ac 1) and target rate (-ar)
    cmd = [
        "ffmpeg", "-y",
        "-i", str(file_path),
        "-ar", str(target_rate),
        "-ac", "1",
        str(temp_path)
    ]
    
    try:
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)
        # Replace original with new
        shutil.move(temp_path, file_path)
        return True
    except subprocess.CalledProcessError as e:
        print(f"Error converting {file_path}: {e.stderr.decode()}")
        if temp_path.exists():
            temp_path.unlink()
        return False

def main():
    parser = argparse.ArgumentParser(description="Scan and fix audio file sample rates.")
    parser.add_argument("directories", nargs="*", help="Directories to scan (default: data/processed/audio_cache, data/anki_export)")
    parser.add_argument("--rate", type=int, default=22050, help="Target sample rate (default: 22050)")
    args = parser.parse_args()

    # Default directories to scan (relative to script location in container: /app/scripts)
    # effective paths: /app/data/processed/audio_cache, /app/data/anki_export
    default_dirs = [
        "../data/processed/audio_cache",
        "../data/processed/audio/kaikki",
        "../data/anki_export"
    ]

    target_dirs = []
    if args.directories:
        target_dirs = [Path(d).resolve() for d in args.directories]
    else:
        # Resolve defaults relative to script file
        script_dir = Path(__file__).parent.resolve()
        for d in default_dirs:
            path = (script_dir / d).resolve()
            if path.exists():
                target_dirs.append(path)
    
    if not target_dirs:
        print("No valid directories found to scan.")
        return

    total_checked = 0
    total_fixed = 0
    total_errors = 0

    audio_extensions = {".mp3", ".wav", ".ogg", ".m4a", ".flac"}

    for target_dir in target_dirs:
        print(f"\n--- Scanning {target_dir} ---")
        
        for root, dirs, files in os.walk(target_dir):
            for file in files:
                file_path = Path(root) / file
                if file_path.suffix.lower() in audio_extensions:
                    total_checked += 1
                    rate = get_sample_rate(file_path)
                    
                    if rate is None:
                        # Only show error if we really expect it to be audio
                        print(f"[ERROR] Could not read: {file_path.name}")
                        total_errors += 1
                        continue
                    
                    if rate != args.rate:
                        print(f"[FIXING] {file_path.name} ({rate}Hz -> {args.rate}Hz)")
                        if convert_audio(file_path, args.rate):
                            total_fixed += 1
                        else:
                            total_errors += 1
    
    print("\n" + "=" * 40)
    print(f"Total Scan Complete.")
    print(f"Files Checked: {total_checked}")
    print(f"Files Fixed:   {total_fixed}")
    print(f"Errors:        {total_errors}")
    print("=" * 40)


if __name__ == "__main__":
    main()
