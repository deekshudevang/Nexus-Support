#!/usr/bin/env bash
set -eo pipefail

echo "Running Docs Consistency Check..."

# 1. Verify FINAL_AUDIT.md commit hash
AUDIT_FILE="docs/FINAL_AUDIT.md"
if [ ! -f "$AUDIT_FILE" ]; then
    echo "ERROR: $AUDIT_FILE not found."
    exit 1
fi

# Extract the commit hash from the line: **Last verified:** YYYY-MM-DD @ <hash>
# Use grep and awk to parse it
HASH=$(grep -oP "\*\*Last verified:\*\* [0-9]{4}-[0-9]{2}-[0-9]{2} @ \K[a-f0-9]+" "$AUDIT_FILE" || true)

if [ -z "$HASH" ]; then
    # Fallback if someone used "HEAD" instead of a real hash
    HEAD_CHECK=$(grep -c "\*\*Last verified:\*\* .* @ HEAD" "$AUDIT_FILE" || true)
    if [ "$HEAD_CHECK" -gt 0 ]; then
        echo "ERROR: $AUDIT_FILE is stamped with @ HEAD instead of a specific commit hash."
        exit 1
    fi
    
    echo "ERROR: Could not parse commit hash from $AUDIT_FILE."
    echo "Expected format: **Last verified:** YYYY-MM-DD @ <commit-hash>"
    exit 1
fi

echo "Found Audit Hash: $HASH"

# Check if the hash is an ancestor of HEAD (meaning docs were verified on a previous or current commit in this branch)
if ! git merge-base --is-ancestor "$HASH" HEAD; then
    echo "ERROR: The commit $HASH stamped in $AUDIT_FILE is not an ancestor of HEAD!"
    echo "This means the documentation is outdated compared to the current code."
    echo "Please re-run the verification procedures and update $AUDIT_FILE."
    exit 1
fi

echo "✓ FINAL_AUDIT.md is up to date."

# 2. Verify DEVICE_TEST_MATRIX.md citations
MATRIX_FILE="docs/testing/DEVICE_TEST_MATRIX.md"
if [ ! -f "$MATRIX_FILE" ]; then
    echo "ERROR: $MATRIX_FILE not found."
    exit 1
fi

# Look for VALIDATED without Field Test or SIMULATOR context
INVALID_CLAIMS=$(grep -n "VALIDATED" "$MATRIX_FILE" | grep -v "Field Test" | grep -v "SIMULATOR" || true)

if [ ! -z "$INVALID_CLAIMS" ]; then
    echo "ERROR: Uncited validation claims found in $MATRIX_FILE:"
    echo "$INVALID_CLAIMS"
    echo "Every VALIDATED claim must cite either '(Field Test)' or '[SIMULATOR] <TestName>'."
    exit 1
fi

echo "✓ DEVICE_TEST_MATRIX.md citations are strict."

echo "All docs consistency checks passed!"
exit 0
