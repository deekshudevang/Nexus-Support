import hashlib
import hmac
import os
import secrets
import time
from typing import Dict, Optional
from fastapi import HTTPException, Header, status
from app.core.config import GATEWAY_API_KEYS, OPERATOR_TOKENS, REVOKED_CREDENTIALS

def hash_password(password: str) -> str:
    """Hash a password using PBKDF2-HMAC-SHA256 with a random 16-byte salt."""
    salt = secrets.token_bytes(16)
    key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 100_000)
    return f"pbkdf2_sha256${salt.hex()}${key.hex()}"

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a password against a stored PBKDF2 hash or configured operator token."""
    # Check against stored hash
    if hashed_password.startswith("pbkdf2_sha256$"):
        parts = hashed_password.split("$")
        if len(parts) == 3:
            salt = bytes.fromhex(parts[1])
            expected = bytes.fromhex(parts[2])
            computed = hashlib.pbkdf2_hmac("sha256", plain_password.encode("utf-8"), salt, 100_000)
            return secrets.compare_digest(computed, expected)
    
    # Fallback to direct constant-time token comparison for operator bootstrap tokens
    return any(secrets.compare_digest(plain_password, token) for token in OPERATOR_TOKENS)

def extract_credential(auth_header: Optional[str], custom_header: Optional[str]) -> Optional[str]:
    if custom_header:
        return custom_header.strip()
    if auth_header:
        parts = auth_header.split()
        if len(parts) == 2 and parts[0].lower() == "bearer":
            return parts[1].strip()
        return auth_header.strip()
    return None

def verify_gateway_write(
    authorization: Optional[str] = Header(None),
    x_gateway_key: Optional[str] = Header(None)
) -> str:
    cred = extract_credential(authorization, x_gateway_key)
    if not cred:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Gateway credentials required")

    if cred in REVOKED_CREDENTIALS:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Gateway credential has been revoked")

    for valid_key in GATEWAY_API_KEYS:
        if secrets.compare_digest(cred, valid_key):
            return cred

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid gateway API key")

def verify_operator_read(
    authorization: Optional[str] = Header(None),
    x_operator_token: Optional[str] = Header(None)
) -> str:
    cred = extract_credential(authorization, x_operator_token)
    if not cred:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Operator token required")

    if cred in REVOKED_CREDENTIALS:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Operator token has been revoked")

    for valid_token in OPERATOR_TOKENS:
        if secrets.compare_digest(cred, valid_token):
            return cred

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid operator token")

# ── WebSocket Single-Use Ticket Store ─────────────────────────────────────────
_ws_tickets: Dict[str, float] = {}

def generate_ws_ticket() -> str:
    ticket = secrets.token_urlsafe(32)
    _ws_tickets[ticket] = time.time() + 30.0
    return ticket

def consume_ws_ticket(ticket: str) -> bool:
    if not ticket:
        return False
    now = time.time()
    expired = [t for t, exp in _ws_tickets.items() if exp < now]
    for t in expired:
        _ws_tickets.pop(t, None)

    if ticket in _ws_tickets and _ws_tickets[ticket] >= now:
        _ws_tickets.pop(ticket, None)
        return True
    return False

