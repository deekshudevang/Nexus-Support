package com.meshlink.app.domain.model

data class Message(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val ciphertext: ByteArray,
    val timestamp: Long,
    val delivered: Boolean,
    /** Human-readable display name of the sender. Empty string if unknown. */
    val senderName: String = "",
    /** Ordered list of relaying deviceIds traversed (for Phase 2 UI path). */
    val routeHistory: List<String> = emptyList(),
    /** 0 = SENT, 1 = RELAYED, 2 = DELIVERED */
    val status: Int = 0,
    /** True if this is an SOS priority message */
    val isSos: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
