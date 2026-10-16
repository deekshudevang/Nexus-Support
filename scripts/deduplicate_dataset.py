#!/usr/bin/env python3
"""
Dataset Deduplication & Contradiction Detection Script for Gemma 4 Offline Fine-Tuning
Finds exact duplicates, near-duplicates, and contradictory answer pairs.
"""
import sys
import os
import json
import re
import argparse
from collections import defaultdict

def normalize_text(text):
    text = text.lower()
    text = re.sub(r"[^\w\s]", "", text)
    tokens = text.split()
    return tokens

def jaccard_similarity(set_a, set_b):
    if not set_a or not set_b:
        return 0.0
    intersection = len(set_a & set_b)
    union = len(set_a | set_b)
    return intersection / union

def get_first_user_prompt(messages):
    for m in messages:
        if m.get("role") == "user":
            return m.get("content", "").strip()
    return ""

def get_first_assistant_response(messages):
    for m in messages:
        if m.get("role") == "assistant":
            return m.get("content", "").strip()
    return ""

def main():
    parser = argparse.ArgumentParser(description="Find duplicates and conflicts in JSONL dataset")
    parser.add_argument("file", help="Path to JSONL file")
    parser.add_argument("--threshold", type=float, default=0.85, help="Near-duplicate Jaccard threshold (default 0.85)")
    args = parser.parse_args()

    filepath = args.file
    if not os.path.exists(filepath):
        print(f"[FAILED] File not found: {filepath}")
        sys.exit(1)

    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — DATASET DEDUPLICATION AUDIT")
    print("=" * 60)
    print(f"Target: {filepath}")

    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
                user_prompt = get_first_user_prompt(row.get("messages", []))
                assistant_resp = get_first_assistant_response(row.get("messages", []))
                tokens = set(normalize_text(user_prompt))
                records.append({
                    "line": idx,
                    "id": row.get("id", f"line_{idx}"),
                    "user_prompt": user_prompt,
                    "assistant_resp": assistant_resp,
                    "tokens": tokens,
                    "category": row.get("category", "unknown")
                })
            except Exception as e:
                print(f"Line {idx} parse error: {e}")

    print(f"Loaded {len(records)} records for analysis.")

    # 1. Exact duplicates
    exact_prompt_map = defaultdict(list)
    for r in records:
        norm_p = " ".join(normalize_text(r["user_prompt"]))
        exact_prompt_map[norm_p].append(r)

    exact_duplicates = 0
    conflicting_answers = 0
    for norm_p, matches in exact_prompt_map.items():
        if len(matches) > 1:
            exact_duplicates += 1
            # Check if assistant answers conflict
            first_ans = matches[0]["assistant_resp"]
            for m in matches[1:]:
                if m["assistant_resp"].strip() != first_ans.strip():
                    conflicting_answers += 1
                    print(f"\n[CONFLICTING ANSWERS DETECTED]")
                    print(f"  Prompt: '{matches[0]['user_prompt']}'")
                    print(f"  Item 1 ({matches[0]['id']}): {first_ans[:100]}...")
                    print(f"  Item 2 ({m['id']}): {m['assistant_resp'][:100]}...")

    # 2. Near duplicates
    near_duplicates = 0
    n = len(records)
    for i in range(n):
        for j in range(i + 1, n):
            # Skip if exact match already counted
            if records[i]["user_prompt"] == records[j]["user_prompt"]:
                continue
            sim = jaccard_similarity(records[i]["tokens"], records[j]["tokens"])
            if sim >= args.threshold:
                near_duplicates += 1
                if near_duplicates <= 5:
                    print(f"\n[NEAR-DUPLICATE DETECTED] (Jaccard: {sim:.2f})")
                    print(f"  Item A ({records[i]['id']}): {records[i]['user_prompt'][:100]}")
                    print(f"  Item B ({records[j]['id']}): {records[j]['user_prompt'][:100]}")

    print("\n" + "=" * 60)
    print("Deduplication Summary:")
    print(f"  Total Rows Audited:     {len(records)}")
    print(f"  Exact Prompt Clusters:  {exact_duplicates}")
    print(f"  Near-Duplicate Pairs:   {near_duplicates}")
    print(f"  Conflicting Answers:    {conflicting_answers}")

    if exact_duplicates == 0 and conflicting_answers == 0:
        print("\n[VERIFIED] Zero exact duplicates and zero conflicting answers found.")
        return 0
    else:
        print("\n[FAILED] Duplicate or conflicting records detected. Resolve before training.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
