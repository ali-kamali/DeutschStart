# DeutschStart Content Server

Backend for generating, validating, and serving German learning content.

## Setup

1. **Install Dependencies** (Poetry):
   ```bash
   cd server
   poetry install
   ```

2. **Environment Variables**:
   ```bash
   cp .env.example .env
   # Edit .env with your OpenAI API key
   ```

3. **Start Infrastructure**:
   ```bash
   docker-compose up -d db redis minio
   ```

4. **Initialize Dictionary** (Run Once):
   ```bash
   poetry run python -m app.tasks.ingest_kaikki
   ```

5. **Run Server**:
   ```bash
   poetry run uvicorn app.main:app --reload
   ```

## Directory Structure
- `app/`: Source code
- `data/`: Generated content (raw, processed)
- `dictionaries/`: Kaikki database
