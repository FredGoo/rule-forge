"""Prediction endpoint."""

from __future__ import annotations

import time

from fastapi import APIRouter, HTTPException

from app.models.registry import ModelRegistry
from app.schemas import PredictRequest, PredictResponse

router = APIRouter()
registry: ModelRegistry | None = None  # Set during app startup


@router.post("/predict", response_model=PredictResponse)
async def predict(req: PredictRequest) -> PredictResponse:
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")

    info = registry.get_model(req.model_id)
    if info is None:
        raise HTTPException(404, f"Model '{req.model_id}' not found")
    if not info.active:
        raise HTTPException(400, f"Model '{req.model_id}' is not active")

    t0 = time.perf_counter()
    try:
        outputs = registry.predict(req.model_id, req.inputs)
    except ValueError as e:
        raise HTTPException(400, str(e))
    except Exception as e:
        raise HTTPException(500, f"Prediction failed: {e}")
    latency_ms = (time.perf_counter() - t0) * 1000

    return PredictResponse(
        model_id=req.model_id,
        outputs=outputs,
        latency_ms=round(latency_ms, 2),
        model_version=info.version,
    )
