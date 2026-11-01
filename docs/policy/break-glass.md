# Policy: Emergency Medical Break-Glass Access

> [!CAUTION]
> **HUMAN APPROVAL GATE REQUIRED**
> This policy governs emergency access to sensitive personal medical profiles (blood group, chronic conditions, emergency medications) by first responders in life-threatening scenarios where the patient is incapacitated. Implementation and activation are strictly blocked until explicit human ADR approval.

## 1. Ethical & Regulatory Principles
- **Minimal Disclosure:** Only data critical to acute trauma triage (Blood Type, Severe Allergies, DNR status) may be decrypted.
- **Strict Role Verification:** Only authenticated Emergency Responders (`ROLE_RESPONDER` or `ROLE_INCIDENT_COMMANDER`) can invoke break-glass keys.
- **Non-Repudiation & Audit:** Every break-glass decryption generates an immutable cryptographic audit record containing:
  - Responder Device ID & Identity Signature.
  - Target Incident ID.
  - Reason / Justification.
  - Tamper-evident timestamp & GPS location.
  - Local persistence and mandatory sync to central backend upon first gateway contact.

## 2. Human Approval Checklist
- [ ] Medical privacy compliance review (HIPAA / GDPR Art. 9 vital interest exception).
- [ ] Responder identity validation workflow.
- [ ] Explicit ADR approval.

