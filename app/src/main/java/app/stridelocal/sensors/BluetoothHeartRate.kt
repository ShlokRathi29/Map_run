package app.stridelocal.sensors

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

data class HeartRateSensor(val name: String, val address: String)

class BluetoothHeartRateScanner(context: Context) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onDevice: (HeartRateSensor) -> Unit, onStopped: () -> Unit) {
        stop()
        val activeScanner = scanner ?: run { onStopped(); return }
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                onDevice(HeartRateSensor(device.name?.takeIf { it.isNotBlank() } ?: "Heart-rate sensor", device.address))
            }
            override fun onScanFailed(errorCode: Int) { onStopped() }
        }
        callback = scanCallback
        try {
            activeScanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
            Handler(Looper.getMainLooper()).postDelayed({ stop(); onStopped() }, SCAN_DURATION_MS)
        } catch (_: SecurityException) { onStopped() }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val activeCallback = callback ?: return
        try { scanner?.stopScan(activeCallback) } catch (_: SecurityException) { }
        callback = null
    }

    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private const val SCAN_DURATION_MS = 15_000L
    }
}

class BluetoothHeartRateConnection(
    private val context: Context,
    address: String,
    private val onBpm: (Int, Long) -> Unit,
) {
    private var gatt: BluetoothGatt? = null
    private var measurement: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) close()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            measurement = gatt.getService(BluetoothHeartRateScanner.HEART_RATE_SERVICE)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT) ?: return
            gatt.setCharacteristicNotification(measurement, true)
            measurement?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION)?.let { descriptor ->
                if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            readMeasurement(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            readMeasurement(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(address: String) {
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            gatt = adapter?.getRemoteDevice(address)?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) { }
    }

    init { connect(address) }

    private fun readMeasurement(value: ByteArray?) {
        if (value == null || value.size < 2) return
        val flags = value[0].toInt() and 0xff
        val bpm = if (flags and 0x01 != 0 && value.size >= 3) {
            (value[1].toInt() and 0xff) or ((value[2].toInt() and 0xff) shl 8)
        } else value[1].toInt() and 0xff
        if (bpm in 25..250) onBpm(bpm, System.currentTimeMillis())
    }

    @SuppressLint("MissingPermission")
    fun close() { try { gatt?.disconnect(); gatt?.close() } catch (_: SecurityException) { } }

    companion object {
        private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
