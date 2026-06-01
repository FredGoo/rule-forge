"""Model registry — manages model lifecycle (register, predict, activate, deactivate, delete)."""

from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from app.config import settings
from app.models.loader import detect_fields, load_pkl
from app.schemas import FieldDef, ModelInfo

logger = logging.getLogger(__name__)


class LoadedModel:
    """A model loaded in memory."""

    def __init__(self, info: ModelInfo, model: Any) -> None:
        self.info = info
        self.model = model


class ModelRegistry:
    """Thread-safe model registry with file-backed storage."""

    def __init__(self) -> None:
        self._cache: dict[str, LoadedModel] = {}
        self._models_dir = settings.data_dir / "models"
        self._metadata_dir = settings.data_dir / "metadata"
        self._models_dir.mkdir(parents=True, exist_ok=True)
        self._metadata_dir.mkdir(parents=True, exist_ok=True)

    # ---- Registration ----

    def register(self, model_id: str, pkl_bytes: bytes, name: str = "",
                 description: str = "", version: str = "1.0.0",
                 input_fields: list[FieldDef] | None = None,
                 output_fields: list[FieldDef] | None = None) -> ModelInfo:
        """Register a new PKL model. Loads into memory immediately."""
        if model_id in self._cache:
            raise ValueError(f"Model '{model_id}' already registered")

        # Save PKL file
        pkl_path = self._models_dir / f"{model_id}.pkl"
        pkl_path.write_bytes(pkl_bytes)

        # Load model
        model = load_pkl(str(pkl_path))

        # Auto-detect fields if not provided
        auto_detected = False
        if input_fields is None or output_fields is None:
            detected_in, detected_out = detect_fields(model)
            auto_detected = True
            if input_fields is None:
                input_fields = detected_in
            if output_fields is None:
                output_fields = detected_out

        info = ModelInfo(
            model_id=model_id,
            name=name or model_id,
            description=description,
            version=version,
            active=True,
            input_fields=input_fields,
            output_fields=output_fields,
            auto_detected=auto_detected,
            model_type=type(model).__name__,
            file_size_bytes=len(pkl_bytes),
        )

        # Save metadata
        self._save_metadata(info)

        # Cache
        self._cache[model_id] = LoadedModel(info, model)
        logger.info("Registered model '%s' (type=%s, inputs=%d, outputs=%d)",
                     model_id, info.model_type, len(info.input_fields), len(info.output_fields))
        return info

    # ---- Prediction ----

    def predict(self, model_id: str, inputs: dict[str, Any]) -> dict[str, Any]:
        """Run prediction and return outputs dict."""
        loaded = self._cache.get(model_id)
        if loaded is None or not loaded.info.active:
            raise ValueError(f"Model '{model_id}' not loaded or inactive")

        model = loaded.model

        # Build input DataFrame with field validation
        input_dict: dict[str, Any] = {}
        for field in loaded.info.input_fields:
            val = inputs.get(field.name, field.default)
            if val is None and field.required:
                raise ValueError(f"Missing required input field: {field.name}")
            if val is not None:
                input_dict[field.name] = _cast(val, field.type)

        df = pd.DataFrame([input_dict])

        # Predict
        has_proba = hasattr(model, "predict_proba")
        if has_proba:
            proba = model.predict_proba(df)[0]
            classes = getattr(model, "classes_", None)
            pred_class = model.predict(df)[0]
        else:
            pred_raw = model.predict(df)[0]
            proba = None
            pred_class = None

        # Build outputs
        outputs: dict[str, Any] = {}
        for field in loaded.info.output_fields:
            if field.name == "predicted_class":
                outputs[field.name] = str(pred_class) if pred_class is not None else str(pred_raw)
            elif field.name.startswith("prob_") and proba is not None and classes is not None:
                class_label = field.name[5:]  # strip "prob_"
                for i, cls in enumerate(classes):
                    if str(cls) == class_label:
                        outputs[field.name] = float(proba[i])
                        break
            elif field.name == "score":
                val = pred_raw if pred_class is None else pred_class
                outputs[field.name] = float(val) if isinstance(val, (int, float, np.floating)) else str(val)

        return outputs

    # ---- Lifecycle ----

    def activate(self, model_id: str) -> None:
        """Load model into memory from disk."""
        if model_id in self._cache:
            return  # Already loaded

        pkl_path = self._models_dir / f"{model_id}.pkl"
        if not pkl_path.exists():
            raise FileNotFoundError(f"PKL file not found for model '{model_id}'")

        info = self._load_metadata(model_id)
        model = load_pkl(str(pkl_path))
        info.active = True
        self._save_metadata(info)
        self._cache[model_id] = LoadedModel(info, model)
        logger.info("Activated model '%s'", model_id)

    def deactivate(self, model_id: str) -> None:
        """Unload model from memory, keep files."""
        loaded = self._cache.pop(model_id, None)
        if loaded is None:
            return
        loaded.info.active = False
        self._save_metadata(loaded.info)
        logger.info("Deactivated model '%s'", model_id)

    def delete(self, model_id: str) -> None:
        """Delete model (memory + files + metadata)."""
        self._cache.pop(model_id, None)
        pkl_path = self._models_dir / f"{model_id}.pkl"
        meta_path = self._metadata_dir / f"{model_id}.json"
        pkl_path.unlink(missing_ok=True)
        meta_path.unlink(missing_ok=True)
        logger.info("Deleted model '%s'", model_id)

    # ---- Queries ----

    def list_models(self) -> list[ModelInfo]:
        """List all known models (loaded or not)."""
        result: list[ModelInfo] = []
        # Loaded models
        for loaded in self._cache.values():
            result.append(loaded.info)
        # Unloaded models on disk
        for meta_path in self._metadata_dir.glob("*.json"):
            model_id = meta_path.stem
            if model_id not in self._cache:
                result.append(self._load_metadata(model_id))
        return result

    def get_model(self, model_id: str) -> ModelInfo | None:
        """Get model info by ID."""
        loaded = self._cache.get(model_id)
        if loaded:
            return loaded.info
        meta_path = self._metadata_dir / f"{model_id}.json"
        if meta_path.exists():
            return self._load_metadata(model_id)
        return None

    @property
    def loaded_count(self) -> int:
        return len(self._cache)

    # ---- Persistence helpers ----

    def _save_metadata(self, info: ModelInfo) -> None:
        path = self._metadata_dir / f"{info.model_id}.json"
        path.write_text(info.model_dump_json(indent=2))

    def _load_metadata(self, model_id: str) -> ModelInfo:
        path = self._metadata_dir / f"{model_id}.json"
        return ModelInfo.model_validate_json(path.read_text())


def _cast(value: Any, type_name: str) -> Any:
    """Cast a value to the expected type."""
    if type_name == "int":
        return int(value)
    elif type_name == "float":
        return float(value)
    elif type_name == "bool":
        if isinstance(value, str):
            return value.lower() in ("true", "1", "yes")
        return bool(value)
    return str(value)
