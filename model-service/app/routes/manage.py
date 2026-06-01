"""Model management endpoints (CRUD + activate/deactivate)."""

from __future__ import annotations

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.models.registry import ModelRegistry
from app.schemas import FieldDef, ModelInfo, ModelUploadRequest

router = APIRouter()
registry: ModelRegistry | None = None  # Set during app startup


@router.get("/models", response_model=list[ModelInfo])
async def list_models() -> list[ModelInfo]:
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")
    return registry.list_models()


@router.get("/models/{model_id}", response_model=ModelInfo)
async def get_model(model_id: str) -> ModelInfo:
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")
    info = registry.get_model(model_id)
    if info is None:
        raise HTTPException(404, f"Model '{model_id}' not found")
    return info


@router.post("/models", response_model=ModelInfo)
async def upload_model(
    file: UploadFile = File(...),
    metadata: str = Form(...),
) -> ModelInfo:
    """Upload a PKL file and register the model."""
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")

    if not file.filename or not file.filename.endswith(".pkl"):
        raise HTTPException(400, "Only .pkl files are accepted")

    req = ModelUploadRequest.model_validate_json(metadata)
    pkl_bytes = await file.read()

    max_bytes = 500 * 1024 * 1024  # 500MB default
    if len(pkl_bytes) > max_bytes:
        raise HTTPException(400, f"File too large: {len(pkl_bytes)} bytes (max {max_bytes})")

    try:
        info = registry.register(
            model_id=req.model_id,
            pkl_bytes=pkl_bytes,
            name=req.name,
            description=req.description,
            version=req.version,
            input_fields=req.input_fields,
            output_fields=req.output_fields,
        )
    except ValueError as e:
        raise HTTPException(409, str(e))
    except Exception as e:
        raise HTTPException(500, f"Failed to load model: {e}")

    return info


@router.post("/models/{model_id}/activate")
async def activate_model(model_id: str) -> dict:
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")
    try:
        registry.activate(model_id)
    except FileNotFoundError as e:
        raise HTTPException(404, str(e))
    return {"status": "activated", "model_id": model_id}


@router.post("/models/{model_id}/deactivate")
async def deactivate_model(model_id: str) -> dict:
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")
    registry.deactivate(model_id)
    return {"status": "deactivated", "model_id": model_id}


@router.delete("/models/{model_id}")
async def delete_model(model_id: str) -> dict:
    if registry is None:
        raise HTTPException(503, "Model registry not initialized")
    registry.delete(model_id)
    return {"status": "deleted", "model_id": model_id}
