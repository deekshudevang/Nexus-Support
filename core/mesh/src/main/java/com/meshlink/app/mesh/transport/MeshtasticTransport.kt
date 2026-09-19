package com.meshlink.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.geeksville.mesh.MeshProtos
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber
import java.util.UUID

/**
 * Interface representing the hardware transport for Meshtastic.
 */
interface MeshtasticTransport {
    val receivedPackets: SharedFlow<MeshProtos.MeshPacket>
    val connectionState: SharedFlow<Boolean>

    fun startScanning()
    fun stopScanning()
    fun disconnect()
    fun sendPacket(packet: MeshProtos.MeshPacket)
}

/**
 * BLE implementation for communicating with a Meshtastic node.
 * Uses the standard Meshtastic Bluetooth Service UUIDs.
 */
@SuppressLint("MissingPermission") // Caller must ensure BLUETOOTH_CONNECT & BLUETOOTH_SCAN permissions are granted
class MeshtasticBleTransport(
    private val context: Context
) : MeshtasticTransport {

    companion object {
        // Meshtastic Standard UUIDs
        val MESHTASTIC_SERVICE_UUID: UUID = UUID.fromString("cb0b9a0b-a815-4652-9677-efa656114ec")
        val FROM_NUM_UUID: UUID = UUID.fromString("ed9da18c-a8e5-4a67-a320-fbc954288b06")
        val FROM_RADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val TO_RADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7") // ToRadio is usually the same or write char
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val _receivedPackets = MutableSharedFlow<MeshProtos.MeshPacket>(extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<MeshProtos.MeshPacket> = _receivedPackets

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    override val connectionState: SharedFlow<Boolean> = _connectionState

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (device.name?.contains("Meshtastic", ignoreCase = true) == true) {
                    Timber.i("Found Meshtastic device: ${device.name} [${device.address}]")
                    stopScanning()
                    connectToDevice(device)
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.i("Connected to Meshtastic GATT server.")
                    _connectionState.tryEmit(true)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.i("Disconnected from Meshtastic GATT server.")
                    _connectionState.tryEmit(false)
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(MESHTASTIC_SERVICE_UUID)
                if (service != null) {
                    val fromRadioChar = service.getCharacteristic(FROM_RADIO_UUID)
                    if (fromRadioChar != null) {
                        gatt.setCharacteristicNotification(fromRadioChar, true)
                        // In BLE, we also need to write the CCCD descriptor to enable notifications
                        val descriptor = fromRadioChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                } else {
                    Timber.w("Meshtastic service not found on device.")
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == FROM_RADIO_UUID) {
                try {
                    val fromRadio = MeshProtos.FromRadio.parseFrom(characteristic.value)
                    if (fromRadio.hasPacket()) {
                        _receivedPackets.tryEmit(fromRadio.packet)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse FromRadio protobuf")
                }
            }
        }
    }

    override fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            Timber.i("Starting BLE scan for Meshtastic nodes...")
            bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
        } else {
            Timber.w("Bluetooth is disabled or not available.")
        }
    }

    override fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Timber.i("Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    override fun disconnect() {
        stopScanning()
        bluetoothGatt?.disconnect()
    }

    override fun sendPacket(packet: MeshProtos.MeshPacket) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(MESHTASTIC_SERVICE_UUID) ?: return
        val toRadioChar = service.getCharacteristic(TO_RADIO_UUID) ?: return

        val toRadio = MeshProtos.ToRadio.newBuilder().setPacket(packet).build()
        toRadioChar.value = toRadio.toByteArray()
        gatt.writeCharacteristic(toRadioChar)
    }
}
