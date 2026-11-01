#!/usr/bin/env python3
"""
Deterministic Stratified Dataset Splitter for Gemma 4 Offline Fine-Tuning
Splits input JSONL into train.jsonl, validation.jsonl, and test.jsonl.
Enforces source-group isolation and category stratification with a fixed seed.
"""
import sys
import os
import json
import random
import hashlib
import argparse
from collections import defaultdict

DEFAULT_TRAIN_RATIO = 0.70
DEFAULT_VAL_RATIO = 0.15
DEFAULT_TEST_RATIO = 0.15
FIXED_SEED = 42

def compute_sha256(filepath):
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()

def main():
    parser = argparse.ArgumentParser(description="Split JSONL dataset into train, val, test")
    parser.add_argument("input_file", help="Path to input curated JSONL file")
    parser.add_argument("--out_dir", default="data", help="Output directory (default: 'data')")
    parser.add_argument("--seed", type=int, default=FIXED_SEED, help=f"Random seed (default: {FIXED_SEED})")
    args = parser.parse_args()

    input_path = args.input_file
    if not os.path.exists(input_path):
        print(f"[FAILED] Input file not found: {input_path}")
        sys.exit(1)

    os.makedirs(args.out_dir, exist_ok=True)
    random.seed(args.seed)

    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — STRATIFIED DATASET SPLIT")
    print("=" * 60)
    print(f"Input:      {input_path}")
    print(f"Output Dir: {args.out_dir}")
    print(f"Seed:       {args.seed}")

    # Group by category and source_ref to avoid source leakage across splits
    category_source_groups = defaultdict(lambda: defaultdict(list))
    total_records = 0

    with open(input_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            total_records += 1
            cat = row.get("category", "general_conversation")
            # Grouping key: source_ref if available, or row id
            group_key = row.get("source_ref") or row.get("id")
            category_source_groups[cat][group_key].append(row)

    print(f"Total Records: {total_records}")
    print(f"Categories:    {len(category_source_groups)}")

    train_rows = []
    val_rows = []
    test_rows = []

    for cat, group_dict in sorted(category_source_groups.items()):
        groups = list(group_dict.values())
        random.shuffle(groups)

        cat_total = sum(len(g) for g in groups)
        target_train = int(cat_total * DEFAULT_TRAIN_RATIO)
        target_val = int(cat_total * DEFAULT_VAL_RATIO)

        curr_train = 0
        curr_val = 0

        for g in groups:
            if curr_train < target_train or (curr_train + len(g) <= target_train + 1):
                train_rows.extend(g)
                curr_train += len(g)
            elif curr_val < target_val or (curr_val + len(g) <= target_val + 1):
                val_rows.extend(g)
                curr_val += len(g)
            else:
                test_rows.extend(g)

    # Deterministic shuffle within splits
    random.shuffle(train_rows)
    random.shuffle(val_rows)
    random.shuffle(test_rows)

    train_path = os.path.join(args.out_dir, "train.jsonl")
    val_path = os.path.join(args.out_dir, "validation.jsonl")
    test_path = os.path.join(args.out_dir, "test.jsonl")

    for path, rows in [(train_path, train_rows), (val_path, val_rows), (test_path, test_rows)]:
        with open(path, "w", encoding="utf-8") as out_f:
            for r in rows:
                out_f.write(json.dumps(r, ensure_ascii=False) + "\n")

    train_hash = compute_sha256(train_path)
    val_hash = compute_sha256(val_path)
    test_hash = compute_sha256(test_path)

    # Write MANIFEST.json
    manifest = {
        "dataset_version": "1.0.0",
        "created_at": "2026-10-16T19:00:00Z",
        "split_seed": args.seed,
        "splits": {
            "train": {
                "path": "data/train.jsonl",
                "count": len(train_rows),
                "sha256": train_hash
            },
            "validation": {
                "path": "data/validation.jsonl",
                "count": len(val_rows),
                "sha256": val_hash
            },
            "test": {
                "path": "data/test.jsonl",
                "count": len(test_rows),
                "sha256": test_hash
            }
        },
        "total_count": len(train_rows) + len(val_rows) + len(test_rows)
    }

    manifest_path = os.path.join(args.out_dir, "MANIFEST.json")
    with open(manifest_path, "w", encoding="utf-8") as mf:
        json.dump(manifest, mf, indent=2)

    print("\nSplit Complete:")
    print(f"  Train:      {len(train_rows)} rows | SHA256: {train_hash}")
    print(f"  Validation: {len(val_rows)} rows | SHA256: {val_hash}")
    print(f"  Test:       {len(test_rows)} rows | SHA256: {test_hash}")
    print(f"  Manifest:   {manifest_path}")

    return 0

if __name__ == "__main__":
    sys.exit(main())
