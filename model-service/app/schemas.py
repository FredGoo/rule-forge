"""Pydantic schemas for request/response models."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


# ---- Field definitions ----

class FieldDef(BaseModel):
    """A model input or output field definition."""
    name: str
    type: str = "float"  # int, float, bool, string
    required: bool = True
    default: Any = None


# ---- Model metadata ----

class ModelInfo(BaseModel):
    """Model registration info."""
    model_id: str
    name: str = ""
    description: str = ""
    version: str = "1.0.0"
    active: bool = True
    created_at: datetime = Field(default_factory=datetime.now)
    input_fields: list[FieldDef] = []
    output_fields: list[FieldDef] = []
    auto_detected: bool = False
    model_type: str = ""
    file_size_bytes: int = 0


class ModelUploadRequest(BaseModel):
    """Metadata sent alongside a PKL file upload."""
    model_id: str
    name: str = ""
    description: str = ""
    version: str = "1.0.0"
    input_fields: list[FieldDef] | None = None
    output_fields: list[FieldDef] | None = None


# ---- Predict ----

class PredictRequest(BaseModel):
    """A prediction request."""
    model_id: str
    inputs: dict[str, Any]


class PredictResponse(BaseModel):
    """A prediction response."""
    model_id: str
    outputs: dict[str, Any]
    latency_ms: float
    model_version: str = "1.0.0"


# ---- Health ----

class HealthResponse(BaseModel):
    """Health check response."""
    status: str = "ok"
    loaded_models: int = 0
