package com.meshlink.app.data.local.mapper

import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    ciphertext = ciphertext,
    timestamp = timestamp,
    delivered = delivered,
    senderName = senderName,
    routeHistory = if (routeHistory.isEmpty()) emptyList() else routeHistory.split(","),
    status = status,
    isSos = isSos
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    ciphertext = ciphertext,
    timestamp = timestamp,
    delivered = delivered,
    senderName = senderName,
    routeHistory = routeHistory.joinToString(","),
    status = status,
    isSos = isSos
)

fun KnownDeviceEntity.toDomain(): KnownDevice = KnownDevice(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen,
    lastLatitude = lastLatitude,
    lastLongitude = lastLongitude,
    isVerified = isVerified
)

fun KnownDevice.toEntity(): KnownDeviceEntity = KnownDeviceEntity(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen,
    lastLatitude = lastLatitude,
    lastLongitude = lastLongitude,
    isVerified = isVerified
)

fun com.meshlink.app.data.local.entity.SosPacketEntity.toDomain(): com.meshlink.app.domain.model.SosPacketData =
    com.meshlink.app.domain.model.SosPacketData(
        uuid = uuid,
        senderId = senderId,
        senderName = senderName,
        emergencyType = try {
            com.meshlink.app.domain.model.SosPacketData.EmergencyType.valueOf(emergencyType.uppercase())
        } catch (_: Exception) {
            com.meshlink.app.domain.model.SosPacketData.EmergencyType.OTHER
        },
        severity = severity,
        latitude = if (latitude == 0.0 && longitude == 0.0) null else latitude,
        longitude = if (latitude == 0.0 && longitude == 0.0) null else longitude,
        message = message,
        timestamp = timestamp,
        hopCount = hopCount,
        status = com.meshlink.app.domain.model.SosPacketData.SosStatus.fromString(status)
    )

fun com.meshlink.app.domain.model.SosPacketData.toEntity(): com.meshlink.app.data.local.entity.SosPacketEntity =
    com.meshlink.app.data.local.entity.SosPacketEntity(
        uuid = uuid,
        senderId = senderId,
        senderName = senderName,
        emergencyType = emergencyType.name,
        severity = severity,
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        message = message,
        timestamp = timestamp,
        hopCount = hopCount,
        status = status.name
    )

