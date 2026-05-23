import os

DEFAULT_URL = "http://localhost:8081"


def get_base_url() -> str:
    return os.environ.get("RULEFORGE_URL", DEFAULT_URL)


def get_api_key() -> str | None:
    return os.environ.get("RULEFORGE_API_KEY")
