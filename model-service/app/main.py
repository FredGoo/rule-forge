"""RuleForge Model Service — FastAPI application."""

from __future__ import annotations

import logging

from fastapi import FastAPI

from app.config import settings
from app.models.registry import ModelRegistry
from app.routes import health, manage, predict

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="RuleForge Model Service",
    version="0.1.0",
    description="Load PKL models and serve predictions via REST API",
)

# Shared registry instance
_registry = ModelRegistry()


@app.on_event("startup")
async def startup() -> None:
    # Wire registry into route modules
    predict.registry = _registry
    manage.registry = _registry
    health.registry = _registry

    # Reload previously saved models
    for model_info in _registry.list_models():
        if model_info.active:
            try:
                _registry.activate(model_info.model_id)
                logger.info("Restored model '%s' on startup", model_info.model_id)
            except Exception:
                logger.warning("Could not restore model '%s'", model_info.model_id)

    logger.info("Model service started (loaded_models=%d)", _registry.loaded_count)


# Register routes
app.include_router(predict.router, tags=["predict"])
app.include_router(manage.router, tags=["manage"])
app.include_router(health.router, tags=["health"])
