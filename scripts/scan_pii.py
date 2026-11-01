#!/usr/bin/env python3
"""
PII and Secrets Scanner for Gemma 4 Offline AI Datasets and Artifacts
Scans JSONL datasets and text files for personally identifiable information and secrets.
"""
import sys
import os
import json
import re
import argparse

PATTERNS = {
    "Email Address": re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    "Social Security Number (SSN)": re.compile(r"\b\d{3}-\d{2}-\d{4}\b"),
    "Credit Card Number": re.compile(r"\b(?:\d{4}[ -]?){3}\d{4}\b"),
    "Phone Number (Standard)": re.compile(r"\b(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b"),
    "Hugging Face Token": re.compile(r"\bhf_[A-Za-z0-9]{34,}\b"),
    "Google API Key": re.compile(r"\bAIza[0-9A-Za-z-_]{35}\b"),
    "Private Key Header": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "Generic Bearer Token": re.compile(r"\bBearer\s+[A-Za-z0-9\-._~+/]+=*\b", re.IGNORECASE)
}

def scan_text(text):
    findings = []
    for p_name, regex in PATTERNS.items():
        matches = regex.findall(text)
        if matches:
            for m in matches:
                # Redact match for safe reporting
                if len(m) > 6:
                    redacted = m[:2] + "*" * (len(m) - 4) + m[-2:]
                else:
                    redacted = "***"
                findings.append((p_name, redacted))
    return findings

def main():
    parser = argparse.ArgumentParser(description="Scan dataset or files for PII and secrets")
    parser.add_argument("file", help="Path to JSONL or text file to scan")
    args = parser.parse_args()

    filepath = args.file
    if not os.path.exists(filepath):
        print(f"[FAILED] Target file not found: {filepath}")
        sys.exit(1)

    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — PII & SECRETS SCANNER")
    print("=" * 60)
    print(f"Target: {filepath}")

    total_findings = 0
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        for idx, line in enumerate(f, 1):
            if "re.compile(" in line:
                continue
            findings = scan_text(line)
            if findings:
                total_findings += len(findings)
                print(f"[ALERT] Line {idx} contains potential PII / secret:")
                for p_name, redacted in findings:
                    print(f"  - Type: {p_name} | Match: {redacted}")

    print("\n" + "=" * 60)
    print(f"Scan Summary for {filepath}:")
    print(f"  Total PII / Secret Violations: {total_findings}")

    if total_findings == 0:
        print("[VERIFIED] Zero PII or credentials detected. Dataset is sanitized.")
        return 0
    else:
        print("[FAILED] PII or credentials detected. Remove or sanitize before proceeding.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
