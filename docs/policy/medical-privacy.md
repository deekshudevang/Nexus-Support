# Policy: Medical Profile Privacy & Data Isolation

## 1. Zero Metadata Leakage
- Medical data (blood group, allergies, conditions, ICE contacts) must NEVER be transmitted in unencrypted routing envelopes, broadcast headers, or hop metadata.
- Intermediate relay nodes forward encrypted ciphertext packets without possessing the key to inspect payload contents.

## 2. Granular Sharing Controls
- **User-Consented Emergency Sharing:** The survivor selects which fields (e.g., Blood Type only) are attached to an active SOS packet.
- **Recipient-Bound Encryption:** When transmitting to a known medical team, payloads are encrypted specifically to the public key of the assigned medical unit or authorized EOC.

