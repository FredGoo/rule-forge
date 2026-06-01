"""PKL model loading and field auto-detection."""

from __future__ import annotations

import logging
import pickle
from typing import Any

import numpy as np

from app.schemas import FieldDef

logger = logging.getLogger(__name__)


def load_pkl(path: str) -> Any:
    """Load a PKL file and return the model object."""
    with open(path, "rb") as f:
        model = pickle.load(f)  # noqa: S301
    logger.info("Loaded PKL model from %s, type=%s", path, type(model).__name__)
    return model


def detect_fields(model: Any) -> tuple[list[FieldDef], list[FieldDef]]:
    """
    Attempt to auto-detect input/output fields from an sklearn model.

    Returns (input_fields, output_fields). Returns empty lists if detection fails.
    """
    input_fields: list[FieldDef] = []
    output_fields: list[FieldDef] = []

    # --- Input fields: sklearn stores feature names after fit() ---
    feature_names = getattr(model, "feature_names_in_", None)
    if feature_names is not None:
        for name in feature_names:
            input_fields.append(FieldDef(name=str(name), type="float"))

    # --- Output fields ---
    # Classification: model.classes_ gives class labels
    classes = getattr(model, "classes_", None)
    if classes is not None:
        if len(classes) <= 10:
            # Few classes → report each as a probability output
            for cls in classes:
                output_fields.append(FieldDef(name=f"prob_{cls}", type="float"))
            output_fields.append(FieldDef(name="predicted_class", type="string"))
        else:
            # Many classes → just report predicted class
            output_fields.append(FieldDef(name="predicted_class", type="string"))
    else:
        # Regression or pipeline without classes → single score output
        output_fields.append(FieldDef(name="score", type="float"))

    # If using a Pipeline, try to get info from the final estimator
    if hasattr(model, "steps"):
        final_est = model.steps[-1][1]
        if not feature_names:
            sub_names = getattr(final_est, "feature_names_in_", None)
            if sub_names is not None:
                input_fields = [FieldDef(name=str(n), type="float") for n in sub_names]
        if not classes:
            sub_classes = getattr(final_est, "classes_", None)
            if sub_classes is not None:
                output_fields = [FieldDef(name=f"prob_{c}", type="float") for c in sub_classes]
                output_fields.append(FieldDef(name="predicted_class", type="string"))

    return input_fields, output_fields
