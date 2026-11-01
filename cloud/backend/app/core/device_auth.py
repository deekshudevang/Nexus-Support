"""Device Keystore & ECDSA Request Signature Authentication (P-256 / SHA-256)."""

import base64
import hashlib
import time
from typing import Optional

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils
from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models.device import DeviceModel


def compute_canonical_message(method: str, path: str, timestamp_str: str, body_bytes: bytes) -> bytes:
    """Build deterministic canonical message for ECDSA signature verification."""
    body_hash = hashlib.sha256(body_bytes).hexdigest()
    canonical_str = f"{method.upper()}:{path}:{timestamp_str}:{body_hash}"
    return canonical_str.encode("utf-8")


def decode_signature_bytes(signature_str: str) -> bytes:
    """Decode signature from base64 or hex format, supporting DER or raw 64-byte r||s."""
    sig_str = signature_str.strip()
    try:
        # Try hex first if only hex characters
        if all(c in "0123456789abcdefABCDEF" for c in sig_str) and len(sig_str) % 2 == 0:
            raw = bytes.fromhex(sig_str)
        else:
            # Base64 with padding adjustment
            rem = len(sig_str) % 4
            padded = sig_str + ("=" * (4 - rem) if rem else "")
            raw = base64.b64decode(padded)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Malformed device signature encoding: {str(e)}",
        )

    # If raw 64-byte P-256 signature (32 bytes r + 32 bytes s), convert to ASN.1 DER
    if len(raw) == 64:
        r = int.from_bytes(raw[:32], byteorder="big")
        s = int.from_bytes(raw[32:], byteorder="big")
        return utils.encode_dss_signature(r, s)

    return raw


def verify_device_signature(
    device_id: str,
    timestamp_str: str,
    signature_b64: str,
    method: str,
    path: str,
    body_bytes: bytes,
    db: Session,
    max_drift_sec: int = 300,
) -> DeviceModel:
    """
    Validate device request signature and replay protection window.
    """
    try:
        ts = int(timestamp_str)
    except (ValueError, TypeError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid X-Device-Timestamp header format",
        )

    now = int(time.time())
    if abs(now - ts) > max_drift_sec:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Device timestamp out of sync (drift={abs(now - ts)}s > {max_drift_sec}s replay window)",
        )

    device = db.query(DeviceModel).filter(DeviceModel.device_id == device_id).first()
    if not device:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Device '{device_id}' is not enrolled in system keystore",
        )

    if device.status.upper() != "ACTIVE":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Device '{device_id}' is revoked or suspended (status={device.status})",
        )

    canonical_msg = compute_canonical_message(method, path, timestamp_str, body_bytes)
    sig_der = decode_signature_bytes(signature_b64)

    try:
        public_key = serialization.load_pem_public_key(device.public_key_pem.encode("utf-8"))
        if not isinstance(public_key, ec.EllipticCurvePublicKey):
            raise ValueError("Key is not an Elliptic Curve public key")

        public_key.verify(sig_der, canonical_msg, ec.ECDSA(hashes.SHA256()))
    except (InvalidSignature, ValueError) as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cryptographic device signature verification failed",
        )

    # Update last seen timestamp
    device.last_seen_at = now
    try:
        db.commit()
    except Exception:
        db.rollback()

    return device


def sign_device_request(
    private_key: ec.EllipticCurvePrivateKey,
    method: str,
    path: str,
    timestamp_str: str,
    body_bytes: bytes,
) -> str:
    """Helper for testing or clients to generate base64 DER signature."""
    canonical_msg = compute_canonical_message(method, path, timestamp_str, body_bytes)
    sig_der = private_key.sign(canonical_msg, ec.ECDSA(hashes.SHA256()))
    return base64.b64encode(sig_der).decode("utf-8")
