package com.meshlink.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber
import java.util.UUID

/**
 * BLE advertiser/scanner for passive SOS beacon broadcasting.
 *
 * Broadcasts encoded SOS data (from [SosBeaconCodec]) as BLE manufacturer data
 * so that nearby devices can detect the distress signal even without an active
 * Nearby Connections link. Any device scanning for our service UUID will pick up
 * the beacon and decode the SOS payload.
 *
 * Inspired by RESCUE-MESH's BleMeshService beacon advertising approach.
 */
@SuppressLint("MissingPermission") // Caller must ensure BT permissions
class SosBeaconAdvertiser(private val context: Context) {

    companion object {
        /** Custom service UUID for MeshLink SOS beacons. */
        val SOS_SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val _detectedBeacons = MutableSharedFlow<SosBeaconCodec.SosBeacon>(extraBufferCapacity = 32)
    val detectedBeacons: SharedFlow<SosBeaconCodec.SosBeacon> = _detectedBeacons

    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.i("SOS beacon advertising started")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            Timber.e("SOS beacon advertising failed: $errorCode")
            isAdvertising = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.scanRecord?.getManufacturerSpecificData(SosBeaconCodec.MANUFACTURER_ID)?.let { data ->
                val beacon = SosBeaconCodec.decode(data)
                if (beacon != null) {
                    Timber.i("Detected SOS beacon: type=${beacon.emergencyType}, severity=${beacon.severity}")
                    _detectedBeacons.tryEmit(beacon)
                }
            }
        }
    }

    /**
     * Start broadcasting an SOS beacon with the given parameters.
     */
    fun startAdvertising(beaconData: ByteArray) {
        if (isAdvertising) return

        val adv = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Timber.w("BLE advertiser not available")
            return
        }
        advertiser = adv

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // Advertise indefinitely
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .addManufacturerData(SosBeaconCodec.MANUFACTURER_ID, beaconData)
            .build()

        adv.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
        Timber.i("SOS beacon advertising stopped")
    }

    /**
     * Start scanning for SOS beacons from other devices.
     */
    fun startScanning() {
        val scan = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanner = scan

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scan.startScan(listOf(filter), settings, scanCallback)
        Timber.i("SOS beacon scanning started")
    }

    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        Timber.i("SOS beacon scanning stopped")
    }

    fun cleanup() {
        stopAdvertising()
        stopScanning()
    }
}
