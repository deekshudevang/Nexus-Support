package com.meshlink.app.mesh.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact binary SOS beacon codec for BLE advertising payloads.
 *
 * Inspired by RESCUE-MESH's SosBeaconCodec — encodes an SOS signal into 18 bytes
 * that fit inside a BLE manufacturer data field (max 24 bytes in advertisement).
 *
 * Wire format (18 bytes total):
 *   [0-3]   deviceIdHash  — CRC32 of deviceId string (4 bytes)
 *   [4-7]   latitude      — float, IEEE 754 (4 bytes)
 *   [8-11]  longitude     — float, IEEE 754 (4 bytes)
 *   [12]    emergencyType — enum ordinal (1 byte)
 *   [13]    severity      — 1-5 scale (1 byte)
 *   [14-15] timestampDelta — seconds since midnight UTC, unsigned short (2 bytes)
 *   [16-17] crc16         — CRC-16/CCITT over bytes 0-15 for integrity (2 bytes)
 */
object SosBeaconCodec {

    const val BEACON_SIZE = 18
    /** BLE manufacturer ID — use 0xFFFF (reserved for testing/development). */
    const val MANUFACTURER_ID = 0xFFFF

    enum class EmergencyType(val code: Byte) {
        MEDICAL(0), FIRE(1), FLOOD(2), EARTHQUAKE(3),
        SECURITY(4), TRAPPED(5), LOST(6), OTHER(7);

        companion object {
            fun fromCode(code: Byte): EmergencyType =
                entries.firstOrNull { it.code == code } ?: OTHER
        }
    }

    data class SosBeacon(
        val deviceIdHash: Int,
        val latitude: Float,
        val longitude: Float,
        val emergencyType: EmergencyType,
        val severity: Int,
        val timestampDelta: Int
    )

    fun encode(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        emergencyType: EmergencyType,
        severity: Int
    ): ByteArray {
        val buf = ByteBuffer.allocate(BEACON_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(crc32(deviceId.toByteArray()))
        buf.putFloat(latitude.toFloat())
        buf.putFloat(longitude.toFloat())
        buf.put(emergencyType.code)
        buf.put(severity.coerceIn(1, 5).toByte())

        // Seconds since midnight UTC
        val nowMs = System.currentTimeMillis()
        val midnightMs = nowMs - (nowMs % 86_400_000L)
        val delta = ((nowMs - midnightMs) / 1000).toInt().coerceIn(0, 65535)
        buf.putShort(delta.toShort())

        // CRC-16 over first 16 bytes
        val payload = buf.array()
        val crc = crc16(payload, 0, 16)
        buf.putShort(crc)

        return payload
    }

    fun decode(data: ByteArray): SosBeacon? {
        if (data.size < BEACON_SIZE) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Verify CRC-16 integrity
        val expectedCrc = crc16(data, 0, 16)
        val actualCrc = ByteBuffer.wrap(data, 16, 2).order(ByteOrder.LITTLE_ENDIAN).short
        if (expectedCrc != actualCrc) return null

        val hash = buf.int
        val lat = buf.float
        val lon = buf.float
        val type = EmergencyType.fromCode(buf.get())
        val sev = buf.get().toInt()
        val delta = buf.short.toInt() and 0xFFFF

        return SosBeacon(hash, lat, lon, type, sev, delta)
    }

    private fun crc32(data: ByteArray): Int {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value.toInt()
    }

    private fun crc16(data: ByteArray, offset: Int, length: Int): Short {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc.toShort()
    }
}
