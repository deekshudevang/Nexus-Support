#!/usr/bin/env python3
"""
Model Access Verification Script for Gemma 4 Offline Fine-Tuning Pipeline
Audits authorization to access google/gemma-4-E2B-it on Hugging Face Hub.
CRITICAL SECURITY RULE: NEVER PRINTS OR LOGS HUGGING FACE ACCESS TOKENS.
"""
import sys
import os

MODEL_ID = "google/gemma-4-E2B-it"

def main():
    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — MODEL ACCESS AUDIT")
    print("=" * 60)
    print(f"Target Model: {MODEL_ID}")

    # Check token presence in environment or huggingface-cli cache
    env_token = os.environ.get("HF_TOKEN") or os.environ.get("HUGGING_FACE_HUB_TOKEN")
    if env_token:
        print("[INFO] Hugging Face token detected in environment variables (length: %d chars)." % len(env_token))
    else:
        print("[INFO] No HF_TOKEN detected in environment variables. Checking huggingface-cli cache...")

    try:
        from huggingface_hub import HfApi, get_token
        from huggingface_hub.utils import GatedRepoError, RepositoryNotFoundError
    except ImportError as e:
        print(f"[FAILED] huggingface_hub import error: {e}")
        print("Remediation: pip install huggingface_hub")
        sys.exit(1)

    cached_token = get_token()
    if cached_token:
        print("[INFO] Hugging Face token detected in local CLI cache (length: %d chars)." % len(cached_token))
    elif not env_token:
        print("[WARNING] No Hugging Face token found in environment or CLI cache.")
        print("Note: google/gemma-4-E2B-it is a gated model.")
        print("Remediation steps:")
        print("  1. Visit: https://huggingface.co/google/gemma-4-E2B-it and accept model terms.")
        print("  2. Run: huggingface-cli login  (or set $env:HF_TOKEN='your_token')")

    token_to_use = env_token or cached_token

    print("\n--- Verifying Hub Metadata Access ---")
    api = HfApi(token=token_to_use)
    try:
        info = api.model_info(MODEL_ID)
        print(f"[VERIFIED] Access granted to model: {info.modelId}")
        print(f"Author / Org:     {info.author}")
        print(f"Sha / Commit:     {info.sha[:10] if info.sha else 'N/A'}")
        print(f"Gated Status:     {getattr(info, 'gated', 'N/A')}")
        print(f"Pipeline Tag:     {getattr(info, 'pipeline_tag', 'N/A')}")
        print("\n[VERIFIED] Model access verification passed.")
        return 0
    except GatedRepoError:
        print(f"[FAILED] Repository '{MODEL_ID}' is gated and your token does not have access.")
        print("Remediation: Visit https://huggingface.co/google/gemma-4-E2B-it to accept the Gemma license terms.")
        return 2
    except RepositoryNotFoundError:
        print(f"[FAILED] Repository '{MODEL_ID}' was not found on Hugging Face Hub.")
        return 3
    except Exception as e:
        err_msg = str(e)
        if "401" in err_msg or "Unauthorized" in err_msg or "Invalid token" in err_msg:
            print("[FAILED] Hugging Face token is unauthorized or expired.")
        elif "403" in err_msg or "Forbidden" in err_msg:
            print("[FAILED] Access forbidden (terms not accepted or token missing permission).")
        else:
            print(f"[FAILED / UNAVAILABLE] Could not verify access: {err_msg}")
        return 4

if __name__ == "__main__":
    sys.exit(main())
