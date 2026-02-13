from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.config import settings
from app.database import engine, Base
from app.api.v1 import import_content, packs

# Create tables if they don't exist (simpler than Alembic for MVP start)
# For production, use Alembic migrations.
Base.metadata.create_all(bind=engine)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup logic
    print("Starting DeutschStart Content Server...")
    yield
    # Shutdown logic
    print("Shutting down...")

app = FastAPI(
    title="DeutschStart Content Server",
    description="Backend for generating and validating German learning content.",
    version="0.1.0",
    lifespan=lifespan
)

# Include Routers
app.include_router(import_content.router, prefix="/api/v1/import", tags=["Import"])
app.include_router(packs.router, prefix="/api/v1/packs", tags=["Packs"])

@app.get("/health")
async def health_check():
    return {"status": "ok", "version": app.version}

@app.get("/")
async def root():
    return {"message": "Welcome to DeutschStart Content Server API"}

# Forced reload trigger

