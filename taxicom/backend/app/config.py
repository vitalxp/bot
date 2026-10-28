from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="TAXICOM_", env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./taxicom.db"
    public_base_url: str = "https://api.taxicom.app"

    # ES256 key pair in PEM. When empty, an ephemeral key is generated at startup (dev/test only).
    jwt_private_key_pem: str = ""
    jwt_issuer: str = "https://api.taxicom.app"
    access_ttl_seconds: int = 15 * 60
    refresh_ttl_days: int = 30

    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    mail_from: str = "Taxi Commandos <reports@taxicom.app>"

    telegram_bot_token: str = ""
    telegram_bot_username: str = "taxicom_bot"
    telegram_webhook_secret: str = ""

    link_code_ttl_minutes: int = 15


@lru_cache
def get_settings() -> Settings:
    return Settings()
