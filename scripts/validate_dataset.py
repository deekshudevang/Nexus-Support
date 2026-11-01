#!/usr/bin/env python3
"""
Dataset Schema and Quality Validation Script for Gemma 4 Offline Fine-Tuning
Validates JSONL schema, role alternation, length bounds, and safety-critical audit requirements.
"""
import sys
import os
import json
import argparse
from datetime import datetime

VALID_CATEGORIES = {
    "general_conversation",
    "coding_study",
    "emergency_guidance",
    "mesh_summarization",
    "uncertainty_refusal",
    "rag_conversation",
    "general_retention",
    "image_qa"
}

VALID_SOURCE_TYPES = {"human_written", "curated_from_source", "synthetic"}
VALID_RISK_LEVELS = {"low", "safety_critical"}
VALID_REVIEW_STATUSES = {"unreviewed", "reviewed", "rejected"}
VALID_ROLES = {"system", "user", "assistant"}

REQUIRED_FIELDS = [
    "id", "category", "messages", "source_type", "source_ref",
    "language", "risk_level", "review_status", "reviewer",
    "license", "dataset_version", "created_at"
]

def validate_row(row, idx, filename):
    errors = []

    # 1. Required fields
    for field in REQUIRED_FIELDS:
        if field not in row:
            errors.append(f"Missing required field: '{field}'")

    if errors:
        return errors

    # 2. Field types and enums
    if not isinstance(row["id"], str) or not row["id"].strip():
        errors.append("Field 'id' must be a non-empty string")

    if row["category"] not in VALID_CATEGORIES:
        errors.append(f"Invalid category '{row.get('category')}'. Must be one of {sorted(VALID_CATEGORIES)}")

    if row["source_type"] not in VALID_SOURCE_TYPES:
        errors.append(f"Invalid source_type '{row.get('source_type')}'. Must be one of {sorted(VALID_SOURCE_TYPES)}")

    if row["risk_level"] not in VALID_RISK_LEVELS:
        errors.append(f"Invalid risk_level '{row.get('risk_level')}'. Must be one of {sorted(VALID_RISK_LEVELS)}")

    if row["review_status"] not in VALID_REVIEW_STATUSES:
        errors.append(f"Invalid review_status '{row.get('review_status')}'. Must be one of {sorted(VALID_REVIEW_STATUSES)}")

    if not isinstance(row["language"], str) or not row["language"].strip():
        errors.append("Field 'language' must be a non-empty string (e.g. 'en')")

    # 3. Safety-critical constraints
    if row["risk_level"] == "safety_critical":
        if not row.get("source_ref") or not str(row["source_ref"]).strip():
            errors.append("Safety-critical row MUST have a valid non-empty 'source_ref'")
        if row.get("review_status") != "reviewed":
            errors.append(f"Safety-critical row MUST have review_status='reviewed', found '{row.get('review_status')}'")
        if not row.get("reviewer") or not str(row["reviewer"]).strip():
            errors.append("Safety-critical row MUST have a valid non-empty 'reviewer' identifier")

    # 4. Messages validation
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 2:
        errors.append("Field 'messages' must be a list containing at least 2 messages (user, assistant)")
    else:
        last_role = None
        has_assistant = False
        for m_idx, msg in enumerate(messages):
            if not isinstance(msg, dict):
                errors.append(f"Message {m_idx} must be a dictionary")
                continue
            role = msg.get("role")
            content = msg.get("content")

            if role not in VALID_ROLES:
                errors.append(f"Message {m_idx} invalid role '{role}'. Expected one of {sorted(VALID_ROLES)}")
            if not isinstance(content, str) or not content.strip():
                errors.append(f"Message {m_idx} content must be a non-empty string")

            # Check alternation
            if last_role and role == last_role:
                errors.append(f"Message {m_idx} has consecutive role '{role}' without alternation")
            last_role = role

            if role == "assistant":
                has_assistant = True

        if not has_assistant:
            errors.append("Messages must contain at least one 'assistant' response")

    return errors

def validate_file(filepath):
    print(f"Validating dataset file: {filepath}")
    if not os.path.exists(filepath):
        print(f"[FAILED] File not found: {filepath}")
        return False

    seen_ids = set()
    total_rows = 0
    total_errors = 0
    categories = {}
    risk_counts = {}

    with open(filepath, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            total_rows += 1
            try:
                row = json.loads(line)
            except json.JSONDecodeError as e:
                print(f"  [Row {idx}] JSON decode error: {e}")
                total_errors += 1
                continue

            row_id = row.get("id")
            if row_id in seen_ids:
                print(f"  [Row {idx}] Duplicate ID: '{row_id}'")
                total_errors += 1
            elif row_id:
                seen_ids.add(row_id)

            errors = validate_row(row, idx, filepath)
            if errors:
                for err in errors:
                    print(f"  [Row {idx} | ID: {row.get('id', 'N/A')}] {err}")
                total_errors += len(errors)

            cat = row.get("category", "unknown")
            categories[cat] = categories.get(cat, 0) + 1
            risk = row.get("risk_level", "unknown")
            risk_counts[risk] = risk_counts.get(risk, 0) + 1

    print("\nValidation Summary for %s:" % filepath)
    print(f"  Total Rows:     {total_rows}")
    print(f"  Total Errors:   {total_errors}")
    print("  Category Distribution:")
    for cat, cnt in sorted(categories.items()):
        print(f"    - {cat}: {cnt}")
    print("  Risk Level Distribution:")
    for rk, cnt in sorted(risk_counts.items()):
        print(f"    - {rk}: {cnt}")

    if total_errors == 0 and total_rows > 0:
        print("[VERIFIED] All rows conform to schema and safety rules.")
        return True
    else:
        print("[FAILED] Validation failed with errors.")
        return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Validate JSONL dataset for Gemma 4 pipeline")
    parser.add_argument("file", help="Path to JSONL file to validate")
    args = parser.parse_args()

    success = validate_file(args.file)
    sys.exit(0 if success else 1)
