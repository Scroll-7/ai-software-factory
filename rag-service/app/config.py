import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    database_url: str = os.getenv(
        "PFE_DATABASE_URL", "postgresql://pfe:pfe@localhost:5432/pfe_factory")
    ollama_url: str = os.getenv("PFE_OLLAMA_URL", "http://localhost:11434")
    embedding_model: str = os.getenv("PFE_EMBEDDING_MODEL", "VERIFY_EMBEDDING_MODEL_TAG")
    embedding_dim: int = int(os.getenv("PFE_EMBEDDING_DIM", "768"))
    workspaces_dir: Path = Path(os.getenv(
        "PFE_WORKSPACES", r"C:\factory ai\workspaces"))
    max_chunk_tokens: int = 320
    overlap_tokens: int = 40
    max_code_lines: int = 40
    max_repo_context_tokens: int = 1200


settings = Settings()
