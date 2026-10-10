package com.meshlink.app.mesh.repository

import com.meshlink.app.mesh.transport.SosBeaconCodec
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Canonical failing test for Defect F.
 *
 * Three invariants that any correct messageId scheme must satisfy:
 *   INV-1: same beacon seen by two nodes at different local times → same id
 *           (id must come from beacon bytes only, not receiver wall clock)
 *   INV-2: still-advertising beacon re-received 11 min later → same id
 *           (the beacon bytes have not changed)
 *   INV-3: two different emissions (19:00 and 20:00 UTC, same device, same params)
 *           → different ids
 *
 * The current 18-byte wire format clamps timestampDelta to UShort (max 65535 s ≈ 18:12 UTC).
 * Both emissions after that time encode delta=65535, so all 18 bytes are identical →
 * CRC16 is identical → no field in the beacon distinguishes them → INV-3 cannot be
 * satisfied within this format without changing the unit of timestampDelta.
 *
 * INV-1 requires the id to be derived solely from the decoded beacon fields.
 * INV-2 is satisfied automatically if INV-1 is satisfied (bytes don't change on re-ad).
 * INV-3 FAILS with the current format — this test documents that failure.
 */
class SosMessageIdTest {

    /**
     * Derives a messageId from beacon fields only — no receiver wall clock.
     * Matches the production id rule in NearbyRepositoryImpl:
     *   "sos-<deviceIdHash>-<timestampDelta>"
     * This satisfies INV-1 (stable across nodes) and INV-2 (stable on re-advertisement)
     * but cannot satisfy INV-3 once delta is clamped.
     */
    private fun messageIdFromBeacon(beacon: SosBeaconCodec.SosBeacon): String =
        "sos-${beacon.deviceIdHash}-${beacon.timestampDelta}"

    /**
     * KNOWN OPEN DEFECT — DEFECT_REGISTER.md row F.
     *
     * INV-3: Two emissions at 19:00 and 20:00 UTC from the same device with the same
     * parameters MUST produce different messageIds (they are separate emergency events).
     *
     * Root cause: timestampDelta is seconds since midnight UTC stored in a UShort (2 bytes,
     * max 65535). 65535 s ≈ 18:12:15 UTC. Any emission after that time encodes delta=65535,
     * making all 18 beacon bytes identical across different activations → same id → the
     * second activation is incorrectly deduplicated and silently dropped.
     *
     * This test is intentionally LEFT FAILING to keep the CI suite red until a wire-format
     * version (e.g. a version nibble + per-activation nonce) is designed and deployed.
     * Do NOT @Ignore or delete this test.
     */
    @Test
    fun inv3_KNOWN_OPEN_DEFECT_F_twoEmissionsAfter1812_mustHaveDifferentIds() {
        val midnight = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
        val time1 = midnight + 68_400_000L          // 19:00:00 UTC → delta clamped to 65535
        val time2 = midnight + 72_000_000L          // 20:00:00 UTC → delta clamped to 65535

        val deviceId = UUID.randomUUID().toString()

        val bytes1 = SosBeaconCodec.encode(deviceId, 0.0, 0.0,
            SosBeaconCodec.EmergencyType.MEDICAL, 5, time1)
        val bytes2 = SosBeaconCodec.encode(deviceId, 0.0, 0.0,
            SosBeaconCodec.EmergencyType.MEDICAL, 5, time2)

        val beacon1 = SosBeaconCodec.decode(bytes1)!!
        val beacon2 = SosBeaconCodec.decode(bytes2)!!

        // Confirm the root cause: both deltas are clamped to 65535 by the wire format
        assertEquals("delta should be clamped to 65535 at 19:00", 65535, beacon1.timestampDelta)
        assertEquals("delta should be clamped to 65535 at 20:00", 65535, beacon2.timestampDelta)
        assertEquals("all decoded beacon fields are identical", beacon1, beacon2)

        // INV-3: MUST NOT be equal — but currently IS. This assertion fails on purpose.
        assertNotEquals(
            "INV-3 FAIL (Defect F): two different SOS activations at 19:00 and 20:00 UTC " +
                "produce the same messageId because timestampDelta wraps at 65535 s (18:12 UTC). " +
                "Fix requires a wire-format version with a per-activation nonce.",
            messageIdFromBeacon(beacon1),
            messageIdFromBeacon(beacon2)
        )
    }

    /**
     * INV-1 + INV-2: same beacon bytes decoded on two nodes (or re-received 11 min later)
     * must give the same id. This PASSES because messageIdFromBeacon uses only beacon fields.
     */
    @Test
    fun inv1_and_inv2_sameBeaconBytesAlwaysGiveSameId() {
        val midnight = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
        val time1 = midnight + 3_600_000L  // 01:00:00 — well within range

        val deviceId = UUID.randomUUID().toString()
        val bytes = SosBeaconCodec.encode(deviceId, 0.0, 0.0,
            SosBeaconCodec.EmergencyType.MEDICAL, 5, time1)

        // Decode once on "node A" and once on "node B" (or 11 min later) — same bytes
        val beaconA = SosBeaconCodec.decode(bytes)!!
        val beaconB = SosBeaconCodec.decode(bytes)!!

        assertEquals(
            "INV-1/INV-2: same beacon bytes must give same messageId regardless of when/where decoded",
            messageIdFromBeacon(beaconA),
            messageIdFromBeacon(beaconB)
        )
    }

    // CRC-16/CCITT — mirrors SosBeaconCodec.crc16 (private there; duplicated here to verify scope)
    private fun crc16(data: ByteArray, offset: Int, length: Int): Short {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc.toShort()
    }

    /**
     * V1 FORMAT SPEC — backward-compatible.
     *
     * v1 beacon layout (19 bytes):
     *   [0-15]  payload fields (identical to v0)
     *   [16-17] CRC-16 over bytes [0-15] ONLY — unchanged from v0
     *   [18]    per-activation nonce (ignored by v0 decoders)
     *
     * Because the CRC scope does NOT change, the HEAD (v0) decoder:
     *   1. Passes the size check (19 >= BEACON_SIZE=18)
     *   2. Computes CRC([0-15]) and compares to bytes [16-17] — same as v0 → passes
     *   3. Reads 18 bytes from the buffer and stops; byte [18] (nonce) is silently ignored
     *   → Old phones decode v1 beacons correctly. Emergency alerts are not silently dropped.
     *
     * This test PASSES with the current decoder and must continue to pass after the v1
     * encoder is added.
     */
    @Test
    fun realV1Beacon_crcOver0to15_nonce_at_byte18_accepted_by_HEAD_decoder() {
        val midnight = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
        val time = midnight + 3_600_000L  // 01:00:00 UTC — delta well within UShort range

        val deviceId = "v1-format-spec-test"
        val v0Bytes = SosBeaconCodec.encode(
            deviceId, 12.345, 67.890,
            SosBeaconCodec.EmergencyType.FIRE, 3, time
        )
        assertEquals("v0 beacon must be exactly 18 bytes", 18, v0Bytes.size)

        // Explicitly verify the CRC in the v0 beacon covers exactly bytes [0-15]
        val expectedCrc = crc16(v0Bytes, 0, 16)
        val crcInBeacon = ByteBuffer.wrap(v0Bytes, 16, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(
            "Prerequisite: v0 CRC at [16-17] must cover exactly bytes [0-15]",
            expectedCrc, crcInBeacon
        )

        // Build a real v1 beacon: v0 bytes [0-17] untouched (CRC scope unchanged),
        // nonce byte appended at position [18]
        val nonce: Byte = 0xAB.toByte()
        val v1Bytes = v0Bytes + byteArrayOf(nonce)
        assertEquals("v1 beacon must be exactly 19 bytes", 19, v1Bytes.size)

        // v1 bytes [16-17] still carry CRC over [0-15] — confirm before feeding to decoder
        val crcInV1 = ByteBuffer.wrap(v1Bytes, 16, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(
            "v1 beacon CRC at [16-17] must equal CRC over [0-15] — nonce at [18] is outside CRC",
            expectedCrc, crcInV1
        )

        // HEAD (v0) decoder must accept the 19-byte v1 beacon
        val decoded = SosBeaconCodec.decode(v1Bytes)
        assertNotEquals(
            "HEAD decoder must accept a real v1 beacon (CRC over [0-15], nonce at [18] ignored)",
            null, decoded
        )

        // All fields must be identical to v0 decode
        val fromV0 = SosBeaconCodec.decode(v0Bytes)!!
        assertEquals("deviceIdHash", fromV0.deviceIdHash, decoded!!.deviceIdHash)
        assertEquals("latitude", fromV0.latitude, decoded.latitude)
        assertEquals("longitude", fromV0.longitude, decoded.longitude)
        assertEquals("emergencyType", fromV0.emergencyType, decoded.emergencyType)
        assertEquals("severity", fromV0.severity, decoded.severity)
        assertEquals("timestampDelta", fromV0.timestampDelta, decoded.timestampDelta)
    }

    /**
     * DROPPED DESIGN DOCUMENTED: CRC over bytes 0-17 is rejected by the HEAD decoder.
     *
     * An earlier proposal extended the CRC to cover bytes [0-17] (payload + old CRC bytes)
     * and stored the result at [16-17]. The HEAD (v0) decoder then:
     *   1. Computes CRC([0-15]) → the original value
     *   2. Compares to bytes [16-17] → CRC([0-17]) — a DIFFERENT value
     *   → Returns null. Old phones reject all v1 emergency beacons.
     *
     * That design is backward incompatible and was dropped. The correct design (above)
     * keeps the CRC scope over [0-15] and places the nonce at [18] outside the CRC.
     *
     * This test PASSES (asserts null) and documents the rejection as a guard.
     */
    @Test
    fun extendedCrcDesign_crcOver0to17_is_rejected_by_HEAD_decoder() {
        val midnight = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
        val time = midnight + 3_600_000L  // 01:00:00 UTC

        val deviceId = "extended-crc-compat-test"
        val v0Bytes = SosBeaconCodec.encode(
            deviceId, 12.345, 67.890,
            SosBeaconCodec.EmergencyType.MEDICAL, 2, time
        )

        // Build the broken v1: compute CRC over ALL 18 v0 bytes [0-17] (which include the
        // original CRC at [16-17]), then overwrite [16-17] with this extended CRC value
        val extendedCrc = crc16(v0Bytes, 0, 18)
        val brokenV1 = v0Bytes.copyOf(19)  // 19 bytes; [18] left as 0
        val crcBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        crcBuf.putShort(extendedCrc)
        brokenV1[16] = crcBuf.array()[0]
        brokenV1[17] = crcBuf.array()[1]
        brokenV1[18] = 0x55  // nonce placeholder

        // Sanity: the extended CRC must differ from CRC over [0-15], otherwise this
        // test is vacuous (it would mean changing the scope had no effect)
        val originalCrc = crc16(v0Bytes, 0, 16)
        assertNotEquals(
            "Test setup: CRC([0-17]) must differ from CRC([0-15]) for this test to be meaningful",
            originalCrc, extendedCrc
        )

        // HEAD decoder computes CRC([0-15]) and compares to bytes [16-17]=CRC([0-17]) → mismatch → null
        val decoded = SosBeaconCodec.decode(brokenV1)
        assertEquals(
            "Dropped design: CRC over [0-17] at bytes [16-17] is rejected by HEAD decoder. " +
                "Old phones cannot verify v1 beacons if the CRC scope changes. " +
                "Correct v1 keeps CRC over [0-15] and puts the nonce at byte [18] outside it.",
            null, decoded
        )
    }
}
