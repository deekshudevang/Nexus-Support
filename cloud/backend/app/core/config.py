"""Unified Runtime Configuration for MeshLink Gateway Backend (Single Source of Truth)."""

import os
from typing import List, Optional, Set
from urllib.parse import urlparse

ENVIRONMENT = (os.getenv("ENVIRONMENT") or os.getenv("ENV") or "development").strip().lower()
IS_DEV = ENVIRONMENT == "development"

DATABASE_PATH = os.getenv("DATABASE_PATH", "meshlink_sync.db")


def _resolve_database_url() -> str:
    raw_url = os.getenv("DATABASE_URL", "").strip()
    if ENVIRONMENT == "production":
        if not raw_url or raw_url.startswith("sqlite") or ":memory:" in raw_url:
            raise RuntimeError(
                "CRITICAL SAFETY INVARIANT 9 VIOLATION: Production environment must never run on SQLite. "
                "A valid PostgreSQL connection URL is mandatory."
            )
        if not (
            raw_url.startswith("postgresql://")
            or raw_url.startswith("postgresql+psycopg2://")
            or raw_url.startswith("postgresql+asyncpg://")
        ):
            raise RuntimeError(
                f"CRITICAL SAFETY INVARIANT 9 VIOLATION: Invalid PostgreSQL database URL scheme for production: '{raw_url}'"
            )
        if raw_url.startswith("postgresql://"):
            return "postgresql+psycopg2://" + raw_url[len("postgresql://"):]
        return raw_url
    if raw_url.startswith("postgresql://"):
        return "postgresql+psycopg2://" + raw_url[len("postgresql://"):]
    db_path = os.getenv("DATABASE_PATH", "meshlink_sync.db")
    return raw_url or f"sqlite:///{db_path}"


DATABASE_URL = _resolve_database_url()

_raw_origins = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173")
CORS_ORIGINS: List[str] = [orig.strip() for orig in _raw_origins.split(",") if orig.strip()]

_DEV_DEFAULT_GW_KEY = "meshlink_gw_dev_secret"
_DEV_DEFAULT_OP_TOKEN = "meshlink_op_dev_secret"
_DEV_DEFAULT_JWT_SECRET = "meshlink_jwt_dev_secret_do_not_use_in_prod"

PROD_INSECURE_VALUES = {
    "",
    _DEV_DEFAULT_GW_KEY,
    "meshlink_gateway_dev_secret",
    _DEV_DEFAULT_OP_TOKEN,
    _DEV_DEFAULT_JWT_SECRET,
    "*",
}

_raw_gw_keys = os.getenv("GATEWAY_API_KEYS", _DEV_DEFAULT_GW_KEY if IS_DEV else "")
GATEWAY_API_KEYS: Set[str] = {k.strip() for k in _raw_gw_keys.split(",") if k.strip()}
if IS_DEV:
    GATEWAY_API_KEYS.add(_DEV_DEFAULT_GW_KEY)

_raw_op_tokens = os.getenv("OPERATOR_TOKENS", f"{_DEV_DEFAULT_OP_TOKEN},op_token_valid_1,demo" if IS_DEV else "")
OPERATOR_TOKENS: Set[str] = {k.strip() for k in _raw_op_tokens.split(",") if k.strip()}
if IS_DEV:
    OPERATOR_TOKENS.add(_DEV_DEFAULT_OP_TOKEN)
    OPERATOR_TOKENS.add("op_token_valid_1")

_raw_revoked = os.getenv("REVOKED_CREDENTIALS", "")
REVOKED_CREDENTIALS: Set[str] = {k.strip() for k in _raw_revoked.split(",") if k.strip()}

JWT_SECRET = os.getenv("JWT_SECRET", _DEV_DEFAULT_JWT_SECRET if IS_DEV else "").strip()

RATE_LIMIT_REQUESTS = int(os.getenv("RATE_LIMIT_REQUESTS", "10000" if ENVIRONMENT != "production" else "120"))
RATE_LIMIT_WINDOW_SEC = int(os.getenv("RATE_LIMIT_WINDOW_SEC", "60"))


def validate_production_configuration(
    environment: str,
    gateway_keys: Set[str],
    operator_tokens: Set[str],
    allowed_origins: List[str],
    database_url: Optional[str] = None,
    jwt_secret: Optional[str] = None,
) -> None:
    """Fail closed when production would start with dev credentials, insecure origins, SQLite, or weak JWT secret."""
    if environment.strip().lower() != "production":
        return

    insecure_creds = {_DEV_DEFAULT_GW_KEY, "meshlink_gateway_dev_secret", _DEV_DEFAULT_OP_TOKEN}
    if (
        not gateway_keys
        or not operator_tokens
        or set(gateway_keys).intersection(insecure_creds)
        or set(operator_tokens).intersection(insecure_creds)
    ):
        raise RuntimeError(
            "Unsafe production configuration: set explicit non-development gateway and operator credentials."
        )

    if jwt_secret is not None:
        if not jwt_secret.strip() or jwt_secret.strip() in PROD_INSECURE_VALUES or len(jwt_secret.strip()) < 32:
            raise RuntimeError(
                "Unsafe production configuration: set a strong JWT_SECRET (>= 32 chars, non-default) in production."
            )

    normalized_origins = {origin.strip().rstrip("/") for origin in allowed_origins if origin.strip()}
    unsafe_origin = any(
        urlparse(origin).scheme != "https"
        or urlparse(origin).hostname in {"localhost", "127.0.0.1", "::1"}
        for origin in normalized_origins
    )
    if not normalized_origins or "*" in normalized_origins or unsafe_origin:
        raise RuntimeError(
            "Unsafe production configuration: set an explicit HTTPS CORS origin allow-list without localhost or wildcard origins."
        )

    if database_url is not None:
        db_target = database_url.strip().lower()
        if not db_target or db_target.startswith("sqlite") or ":memory:" in db_target:
            raise RuntimeError(
                "CRITICAL SAFETY INVARIANT 9 VIOLATION: Production environment must never run on SQLite. "
                "A valid PostgreSQL connection URL is mandatory."
            )
        if not (
            db_target.startswith("postgresql://")
            or db_target.startswith("postgresql+psycopg2://")
            or db_target.startswith("postgresql+asyncpg://")
        ):
            raise RuntimeError(
                f"CRITICAL SAFETY INVARIANT 9 VIOLATION: Invalid PostgreSQL database URL scheme for production: {db_target}"
            )


validate_production_configuration(
    ENVIRONMENT,
    GATEWAY_API_KEYS,
    OPERATOR_TOKENS,
    CORS_ORIGINS,
    DATABASE_URL,
    JWT_SECRET,
)
