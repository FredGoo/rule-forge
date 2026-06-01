"""Health check endpoint."""

from __future__ import annotations

from fastapi import APIRouter

from app.models.registry import ModelRegistry
from app.schemas import HealthResponse

router = APIRouter()
registry: ModelRegistry | None = None  # Set during app startup


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    loaded = registry.loaded_count if registry else 0
    return HealthResponse(status="ok", loaded_models=loaded)
