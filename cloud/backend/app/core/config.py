import os
import secrets
from typing import List

ENVIRONMENT = os.getenv("ENVIRONMENT", "development").lower()

def _resolve_database_url() -> str:
    raw_url = os.getenv("DATABASE_URL", "").strip()
    if ENVIRONMENT == "production":
        if not raw_url or raw_url.startswith("sqlite") or ":memory:" in raw_url:
            raise RuntimeError(
                "CRITICAL SAFETY INVARIANT 9 VIOLATION: Production environment must never run on SQLite. "
                "A valid PostgreSQL connection URL is mandatory."
            )
        if not (raw_url.startswith("postgresql://") or raw_url.startswith("postgresql+psycopg2://") or raw_url.startswith("postgresql+asyncpg://")):
            raise RuntimeError(
                f"CRITICAL SAFETY INVARIANT 9 VIOLATION: Invalid PostgreSQL database URL scheme for production: '{raw_url}'"
            )
        return raw_url
    db_path = os.getenv("DATABASE_PATH", "meshlink_sync.db")
    return raw_url or f"sqlite:///{db_path}"

DATABASE_PATH = os.getenv("DATABASE_PATH", "meshlink_sync.db")
DATABASE_URL = _resolve_database_url()

# Strict Production Security Policy: Reject defaults in production
PROD_INSECURE_VALUES = {"", "meshlink_gateway_dev_secret", "meshlink_op_dev_secret", "*"}

def _get_secret_list(env_var: str, default: List[str]) -> List[str]:
    val = os.getenv(env_var)
    if val:
        return [k.strip() for k in val.split(",") if k.strip()]
    if ENVIRONMENT == "production":
        raise ValueError(f"PRODUCTION FATAL: {env_var} must be explicitly defined with secure keys.")
    return default

GATEWAY_API_KEYS = _get_secret_list("GATEWAY_API_KEYS", ["meshlink_gateway_dev_secret", "gw_key_valid_1"])
OPERATOR_TOKENS = _get_secret_list("OPERATOR_TOKENS", ["meshlink_op_dev_secret", "op_token_valid_1", "demo"])

# Comma-separated list of revoked credentials (instant kill-switch)
REVOKED_CREDENTIALS = set(
    [k.strip() for k in os.getenv("REVOKED_CREDENTIALS", "revoked_gw_key_1,revoked_op_token_1").split(",") if k.strip()]
)

# CORS Policy
_raw_origins = os.getenv("CORS_ORIGINS")
if _raw_origins:
    CORS_ORIGINS = [o.strip() for o in _raw_origins.split(",") if o.strip()]
else:
    if ENVIRONMENT == "production":
        raise ValueError("PRODUCTION FATAL: CORS_ORIGINS must be set explicitly to trusted domains.")
    CORS_ORIGINS = ["*"]

# Rate limiting
RATE_LIMIT_REQUESTS = int(os.getenv("RATE_LIMIT_REQUESTS", "120"))
RATE_LIMIT_WINDOW_SEC = int(os.getenv("RATE_LIMIT_WINDOW_SEC", "60"))

