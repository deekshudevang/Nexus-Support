#!/usr/bin/env python3
"""
Dataset Statistics and Distribution Analyzer for Gemma 4 Offline Fine-Tuning
Computes provenance breakdown, risk levels, review status, category counts, and length metrics.
"""
import sys
import os
import json
import hashlib
import argparse
from collections import Counter

def compute_sha256(filepath):
    if not os.path.exists(filepath):
        return "N/A"
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()

def analyze_file(filepath):
    if not os.path.exists(filepath):
        print(f"[FAILED] File not found: {filepath}")
        return None

    stats = {
        "path": filepath,
        "sha256": compute_sha256(filepath),
        "total_rows": 0,
        "categories": Counter(),
        "source_types": Counter(),
        "risk_levels": Counter(),
        "review_statuses": Counter(),
        "languages": Counter(),
        "user_char_lens": [],
        "assistant_char_lens": []
    }

    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except Exception:
                continue

            stats["total_rows"] += 1
            stats["categories"][row.get("category", "unknown")] += 1
            stats["source_types"][row.get("source_type", "unknown")] += 1
            stats["risk_levels"][row.get("risk_level", "unknown")] += 1
            stats["review_statuses"][row.get("review_status", "unknown")] += 1
            stats["languages"][row.get("language", "unknown")] += 1

            for msg in row.get("messages", []):
                role = msg.get("role")
                content = msg.get("content", "")
                if role == "user":
                    stats["user_char_lens"].append(len(content))
                elif role == "assistant":
                    stats["assistant_char_lens"].append(len(content))

    return stats

def print_stats(stats):
    if not stats:
        return
    print("=" * 60)
    print(f"Dataset Statistics: {stats['path']}")
    print(f"SHA-256: {stats['sha256']}")
    print(f"Total Rows: {stats['total_rows']}")
    print("=" * 60)

    print("\n[Categories]")
    for cat, cnt in sorted(stats["categories"].items()):
        pct = (cnt / stats["total_rows"] * 100) if stats["total_rows"] else 0
        print(f"  - {cat:25s}: {cnt:4d} ({pct:5.1f}%)")

    print("\n[Provenance / Source Type]")
    for st, cnt in sorted(stats["source_types"].items()):
        pct = (cnt / stats["total_rows"] * 100) if stats["total_rows"] else 0
        print(f"  - {st:25s}: {cnt:4d} ({pct:5.1f}%)")

    print("\n[Risk Level]")
    for rk, cnt in sorted(stats["risk_levels"].items()):
        pct = (cnt / stats["total_rows"] * 100) if stats["total_rows"] else 0
        print(f"  - {rk:25s}: {cnt:4d} ({pct:5.1f}%)")

    print("\n[Review Status]")
    for rs, cnt in sorted(stats["review_statuses"].items()):
        pct = (cnt / stats["total_rows"] * 100) if stats["total_rows"] else 0
        print(f"  - {rs:25s}: {cnt:4d} ({pct:5.1f}%)")

    if stats["user_char_lens"]:
        u_lens = sorted(stats["user_char_lens"])
        print(f"\n[User Message Lengths (chars)]")
        print(f"  Min: {u_lens[0]} | Mean: {sum(u_lens)//len(u_lens)} | Median: {u_lens[len(u_lens)//2]} | Max: {u_lens[-1]}")

    if stats["assistant_char_lens"]:
        a_lens = sorted(stats["assistant_char_lens"])
        print(f"\n[Assistant Message Lengths (chars)]")
        print(f"  Min: {a_lens[0]} | Mean: {sum(a_lens)//len(a_lens)} | Median: {a_lens[len(a_lens)//2]} | Max: {a_lens[-1]}")

def main():
    parser = argparse.ArgumentParser(description="Analyze dataset distribution and statistics")
    parser.add_argument("paths", nargs="+", help="One or more JSONL dataset paths")
    args = parser.parse_args()

    for p in args.paths:
        s = analyze_file(p)
        print_stats(s)

if __name__ == "__main__":
    main()
