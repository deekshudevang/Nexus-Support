package com.meshlink.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.Portnums
import com.google.protobuf.ByteString
import com.meshlink.app.domain.model.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Interface representing the hardware transport for Meshtastic nodes.
 * Extends [MeshTransport] for unified mesh routing integration.
 */
interface MeshtasticTransport : MeshTransport {
    val receivedPackets: SharedFlow<MeshProtos.MeshPacket>
    val connectionState: SharedFlow<Boolean>

    fun startScanning()
    fun stopScanning()
    fun disconnect()
    fun sendPacket(packet: MeshProtos.MeshPacket)
}

/**
 * Converts a domain [MeshPacket] to a Meshtastic protobuf [MeshProtos.MeshPacket].
 */
fun MeshPacket.toMeshtasticProto(): MeshProtos.MeshPacket {
    val protoBuilder = MeshProtos.MeshPacket.newBuilder()
        .setId(if (messageId.isNotEmpty()) messageId.hashCode() else UUID.randomUUID().hashCode())
        .setFrom(originId.hashCode())
        .setTo(if (finalDestId == MeshPacket.BROADCAST_DEST) 0xFFFFFFFF.toInt() else finalDestId.hashCode())
        .setRxTime((timestamp / 1000L).toInt())
        .setHopLimit(maxHops.coerceAtMost(7))
        .setPriority(priority)

    val dataBuilder = MeshProtos.Data.newBuilder()
        .setPayload(ByteString.copyFromUtf8(content))
        .setPortnum(Portnums.PortNum.TEXT_MESSAGE_APP)

    protoBuilder.setDecoded(dataBuilder.build())
    return protoBuilder.build()
}

/**
 * BLE implementation for communicating with a Meshtastic hardware node.
 * Uses official Meshtastic Bluetooth Service & Characteristic UUIDs:
 * - Service:   6ba1b218-15a8-461f-9fa8-5dcae273eafd
 * - FromRadio: 2c55e69e-4993-11ed-b878-0242ac120002
 * - ToRadio:   f75c76d2-129e-4dad-a1dd-7866124401e7
 * - FromNum:   ed9da18c-a800-4f66-a670-aa7547e34453
 */
@SuppressLint("MissingPermission")
class MeshtasticBleTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) : MeshtasticTransport {

    companion object {
        // Official Meshtastic BLE Service & Characteristic UUIDs
        val MESHTASTIC_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val FROM_RADIO_UUID: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        val TO_RADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val FROM_NUM_UUID: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MAX_RECONNECT_DELAY_MS = 30_000L
    }

    override val transportType: TransportType = TransportType.MESHTASTIC

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null

    private var isScanning = false
    private var isExplicitDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    override val connectionState: SharedFlow<Boolean> = _connectionState

    private val _receivedPackets = MutableSharedFlow<MeshProtos.MeshPacket>(extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<MeshProtos.MeshPacket> = _receivedPackets

    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceName = try { device.name } catch (_: SecurityException) { null }
                if (deviceName?.contains("Meshtastic", ignoreCase = true) == true) {
                    Timber.i("Found Meshtastic device: $deviceName [${device.address}]")
                    stopScanning()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.w("Meshtastic BLE scan failed with error code $errorCode")
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.i("Connected to Meshtastic GATT server. Requesting MTU 512...")
                    reconnectAttempts = 0
                    _connectionState.tryEmit(true)
                    _isAvailable.value = true

                    // Request MTU 512 for full protobuf payloads
                    val mtuRequested = gatt.requestMtu(512)
                    if (!mtuRequested) {
                        Timber.w("requestMtu(512) returned false, discovering services immediately")
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.i("Disconnected from Meshtastic GATT server (status=$status).")
                    _connectionState.tryEmit(false)
                    _isAvailable.value = false
                    try {
                        bluetoothGatt?.close()
                    } catch (e: Exception) {
                        Timber.w(e, "Error closing GATT client")
                    }
                    bluetoothGatt = null

                    if (!isExplicitDisconnect && lastConnectedDevice != null) {
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.i("Meshtastic GATT MTU negotiated: $mtu (status=$status)")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.w("Service discovery failed with status $status")
                return
            }

            val service = gatt.getService(MESHTASTIC_SERVICE_UUID)
            if (service == null) {
                Timber.w("Meshtastic primary service not found on device.")
                return
            }

            // 1. Enable notification on FromNum characteristic (triggers when packets arrive)
            val fromNumChar = service.getCharacteristic(FROM_NUM_UUID)
            if (fromNumChar != null) {
                gatt.setCharacteristicNotification(fromNumChar, true)
                fromNumChar.getDescriptor(CCCD_UUID)?.let { descriptor ->
                    writeDescriptorCompat(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            }

            // 2. Enable notification on FromRadio characteristic if supported
            val fromRadioChar = service.getCharacteristic(FROM_RADIO_UUID)
            if (fromRadioChar != null && (fromRadioChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                gatt.setCharacteristicNotification(fromRadioChar, true)
                fromRadioChar.getDescriptor(CCCD_UUID)?.let { descriptor ->
                    writeDescriptorCompat(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            }
        }

        // Android 13+ (API 33+) callback for characteristic changes
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicUpdate(gatt, characteristic.uuid, value)
        }

        // Legacy callback for Android <= 12
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleCharacteristicUpdate(gatt, characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        // Android 13+ (API 33+) callback for characteristic reads
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicUpdate(gatt, characteristic.uuid, value)
            }
        }

        // Legacy callback for Android <= 12
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                handleCharacteristicUpdate(gatt, characteristic.uuid, characteristic.value ?: byteArrayOf())
            }
        }
    }

    private fun handleCharacteristicUpdate(gatt: BluetoothGatt, uuid: UUID, value: ByteArray) {
        when (uuid) {
            FROM_NUM_UUID -> {
                // When FromNum notifies, trigger read on FromRadio characteristic
                val service = gatt.getService(MESHTASTIC_SERVICE_UUID)
                val fromRadioChar = service?.getCharacteristic(FROM_RADIO_UUID)
                if (fromRadioChar != null) {
                    gatt.readCharacteristic(fromRadioChar)
                }
            }
            FROM_RADIO_UUID -> {
                parseFromRadioPayload(value)
            }
        }
    }

    private fun parseFromRadioPayload(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        try {
            val fromRadio = MeshProtos.FromRadio.parseFrom(bytes)
            if (fromRadio.hasPacket()) {
                val protoPacket = fromRadio.packet
                _receivedPackets.tryEmit(protoPacket)

                val domainPacket = MeshPacket(
                    senderId = protoPacket.from.toString(),
                    receiverId = protoPacket.to.toString(),
                    content = if (protoPacket.hasDecoded()) protoPacket.decoded.payload.toStringUtf8() else "",
                    timestamp = if (protoPacket.rxTime > 0) protoPacket.rxTime * 1000L else System.currentTimeMillis(),
                    type = MeshPacket.PacketType.ROUTED_CHAT,
                    messageId = protoPacket.id.toString(),
                    originId = protoPacket.from.toString(),
                    finalDestId = if (protoPacket.to == 0xFFFFFFFF.toInt()) MeshPacket.BROADCAST_DEST else protoPacket.to.toString(),
                    hopCount = 0,
                    maxHops = protoPacket.hopLimit,
                    priority = protoPacket.priority
                )
                _incomingPackets.tryEmit(domainPacket)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Meshtastic FromRadio protobuf payload")
        }
    }

    private fun writeDescriptorCompat(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun start() {
        startScanning()
    }

    override fun stop() {
        disconnect()
    }

    override fun startScanning() {
        if (!hasBlePermissions()) {
            Timber.w("Meshtastic BLE scan skipped: Missing BLUETOOTH_SCAN or BLUETOOTH_CONNECT permission.")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Timber.w("Meshtastic BLE scan skipped: Bluetooth adapter is disabled or unavailable.")
            return
        }

        if (isScanning) return

        try {
            isExplicitDisconnect = false
            Timber.i("Starting BLE scan for Meshtastic nodes...")
            bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
            isScanning = true
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException while starting BLE scan")
        }
    }

    override fun stopScanning() {
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException while stopping BLE scan")
        } finally {
            isScanning = false
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        lastConnectedDevice = device
        Timber.i("Connecting to Meshtastic device at ${device.address}")

        // Ensure pairing/bonding is initiated if unbonded
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            try {
                device.createBond()
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException initiating bond with Meshtastic device")
            }
        }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val delayMs = (1000L * (1 shl reconnectAttempts.coerceAtMost(5))).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempts++
        Timber.i("Scheduling Meshtastic reconnect in ${delayMs}ms (attempt $reconnectAttempts)")

        reconnectJob = scope.launch {
            delay(delayMs)
            val device = lastConnectedDevice
            if (!isExplicitDisconnect && device != null && bluetoothGatt == null) {
                connectToDevice(device)
            }
        }
    }

    override fun disconnect() {
        isExplicitDisconnect = true
        reconnectJob?.cancel()
        stopScanning()

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error during Meshtastic disconnect")
        } finally {
            bluetoothGatt = null
            _isAvailable.value = false
            _connectionState.tryEmit(false)
        }
    }

    override fun sendPacket(packet: MeshProtos.MeshPacket) {
        val gatt = bluetoothGatt ?: run {
            Timber.w("sendPacket: BluetoothGatt is null (not connected)")
            return
        }
        val service = gatt.getService(MESHTASTIC_SERVICE_UUID) ?: run {
            Timber.w("sendPacket: Meshtastic service not found")
            return
        }
        val toRadioChar = service.getCharacteristic(TO_RADIO_UUID) ?: run {
            Timber.w("sendPacket: ToRadio characteristic not found")
            return
        }

        val toRadio = MeshProtos.ToRadio.newBuilder().setPacket(packet).build()
        val bytes = toRadio.toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(toRadioChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            toRadioChar.value = bytes
            @Suppress("DEPRECATION")
            toRadioChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(toRadioChar)
        }
    }

    override suspend fun send(packet: MeshPacket, targetAddress: String?): Boolean {
        return try {
            val proto = packet.toMeshtasticProto()
            sendPacket(proto)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to send packet across Meshtastic transport")
            false
        }
    }
}
