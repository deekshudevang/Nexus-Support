# Dataset Engineering & Curation Guide (`docs/ai/DATASET_GUIDE.md`)

**Document Version:** 1.0.0
**Phase:** 2 (Dataset Engineering)
**Status:** VERIFIED
**Target Architecture:** Gemma 4 E2B (`google/gemma-4-E2B-it`)

---

## 1. Principles of Dataset Quality

Fine-tuning for edge AI must prioritize precision over volume:
- **A small, clean, reviewed dataset beats a large noisy one.**
- **Emergency facts belong in RAG, not in weights.** Fine-tuning trains behavioral traits: formatting, professional triage tone, explicit abstention when sources are missing, citation attribution, and faithful mesh message summarization.
- **Strict provenance:** Every row carries immutable metadata tracking its author, origin, license, and risk classification.
- **Zero synthetic hallucination:** Synthetic examples are permanently labeled `source_type: "synthetic"` and are never presented as verified empirical facts.

---

## 2. Dataset Roles & Split Isolation

To prevent data contamination and overfitting:

1. **Training (`data/train.jsonl`):**
   - Exclusively used for gradient updates.
   - Evaluated periodically during training steps for training loss curves.

2. **Validation (`data/validation.jsonl`):**
   - Used for hyperparameter tuning, learning rate scheduling, and checkpoint selection (`checkpoint-best`).
   - Never used for gradient backpropagation.

3. **Frozen Test (`data/test.jsonl`):**
   - Held-out, frozen evaluation set used strictly for one-time final comparison between Baseline B0 and Fine-Tuned F1.
   - Every read access must be recorded in `reports/test_split_access.log` (timestamp, candidate ID, dataset SHA-256).

4. **Development Evaluation Cases (`data/evaluation_cases.jsonl`):**
   - Fixed, repeatable regression benchmarks covering safety abstention, mesh entity preservation, code generation unit tests, and injection defenses.

---

## 3. JSONL Schema Specification

Each line in a dataset split must be a valid JSON object matching this schema:

```json
{
  "id": "emg_cpr_001",
  "category": "emergency_guidance",
  "messages": [
    {
      "role": "user",
      "content": "A person collapsed and is unresponsive. What do I do right now?"
    },
    {
      "role": "assistant",
      "content": "Check responsiveness and call emergency services immediately.\n\n1. Check the scene for safety.\n2. Tap the person's shoulder and shout 'Are you OK?'.\n3. Call emergency services.\n4. Place the person on their back on a firm surface.\n5. Place the heel of one hand on the center of the chest, interlock fingers with your other hand.\n6. Push hard and fast: at least 2 inches deep, 100-120 compressions/minute.\n7. Continue until emergency personnel take over.\n\n[Source: CPR (Cardiopulmonary Resuscitation), v1.0 (AOSP Bundled)]\n\n[Medical Disclaimer: Nexus-Support provides offline guidance. Always summon local emergency personnel if possible.]"
    }
  ],
  "source_type": "curated_from_source",
  "source_ref": "cpr:steps-1-10:v1.0",
  "language": "en",
  "risk_level": "safety_critical",
  "review_status": "reviewed",
  "reviewer": "REV_EMG_MD_01",
  "license": "Apache-2.0",
  "dataset_version": "1.0.0",
  "created_at": "2026-10-16T19:00:00Z"
}
```

### Required Fields:
- `id` (string, unique): Format `<category_prefix>_<slug>_<index>`.
- `category` (string enum): `general_conversation`, `coding_study`, `emergency_guidance`, `mesh_summarization`, `uncertainty_refusal`, `rag_conversation`, `general_retention`, `image_qa`.
- `messages` (list of objects): Strictly alternating `user` and `assistant` turns with non-empty string content.
- `source_type` (string enum): `human_written`, `curated_from_source`, `synthetic`.
- `source_ref` (string or null): Specific document, section, and version/date. Mandatory if `risk_level == "safety_critical"`.
- `language` (string): BCP 47 code (default: `"en"`).
- `risk_level` (string enum): `"low"` or `"safety_critical"`.
- `review_status` (string enum): `"unreviewed"`, `"reviewed"`, or `"rejected"`.
- `reviewer` (string): Reviewer identifier or approved audit ID. Mandatory if `risk_level == "safety_critical"`.
- `license` (string): Distribution license (`"Apache-2.0"`).
- `dataset_version` (string): Semantic version (`"1.0.0"`).
- `created_at` (string): ISO 8601 UTC timestamp.

---

## 4. Category Guidelines

1. **Emergency Guidance (`emergency_guidance`):**
   - Grounded strictly in the 10 approved guides from `PROJECT_CONFIG.md`.
   - Never invents medications, drug dosages, or invasive procedures.
   - Always concludes with source attribution and medical disclaimers.

2. **Mesh Message Summarization (`mesh_summarization`):**
   - Tests extraction and condensation of multi-hop mesh broadcasts.
   - **Absolute preservation rule:** Exact counts, numbers, names, timestamps, coordinates, and injury descriptions must never be altered or omitted.
   - Clear visual distinction between `VERIFIED DISPATCH` vs `UNVERIFIED PEER REPORT`.

3. **Uncertainty and Refusal (`uncertainty_refusal`):**
   - Explores scenarios where context is absent, conflicting, or out of domain.
   - Model explicitly states: *"I do not have verified emergency guidance on this in my local sources."*
   - Refuses dangerous or adversarial prompts (bomb making, cyber exploit payloads, poison synthesis) using standard safety guard responses.

4. **RAG-Style Conversations (`rag_conversation`):**
   - Simulates queries with retrieved context enclosed in `<context>...</context>` delimiters.
   - Tests adherence to retrieved passages and resistance against indirect prompt injections embedded inside retrieved text.

5. **Coding & Study Help (`coding_study`):**
   - Concise, practical software engineering, mathematics, and science explanations without unnecessary filler.

6. **General Retention Replay (`general_retention`):**
   - Multi-turn conversational reasoning to prevent catastrophic forgetting of base Gemma 4 capabilities.

7. **Image Understanding (`image_qa`):**
   - Stored in a separate file `data/image_qa.jsonl`.
   - Text descriptions are never treated as visual training.

---

## 5. Reviewer Quality Checklist

Before marking any example `review_status: "reviewed"`:
- [ ] Prompt represents a realistic, non-contrived disaster or user query.
- [ ] Assistant response is factually accurate against `PROJECT_CONFIG.md` authoritative sources.
- [ ] No PII, phone numbers, real personal identities, or credentials present.
- [ ] Assistant message does not claim real-world rescue dispatch.
- [ ] Source citation and disclaimers are present on all emergency responses.
- [ ] Formatting is clean Markdown without broken tags or unescaped characters.
