from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    nvidia_api_key: str
    nvidia_base_url: str = "https://integrate.api.nvidia.com/v1"

    vision_model: str = "mistralai/mistral-large-3-675b-instruct-2512"
    text_model: str = "nvidia/nemotron-3-super-120b-a12b"

    chroma_persist_dir: str = "./chroma_db"

    max_image_size_mb: int = 20
    max_rag_results: int = 5

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


@lru_cache
def get_settings() -> Settings:
    return Settings()
