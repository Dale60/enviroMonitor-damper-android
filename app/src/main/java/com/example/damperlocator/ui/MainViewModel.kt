package com.example.damperlocator.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _screen = MutableStateFlow<Screen>(Screen.Scan)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResultUi>>(emptyList())
    val scanResults: StateFlow<List<ScanResultUi>> = _scanResults.asStateFlow()

    private val _bestCandidate = MutableStateFlow<ScanResultUi?>(null)
    val bestCandidate: StateFlow<ScanResultUi?> = _bestCandidate.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _filterMode = MutableStateFlow(FilterMode.BEACONS)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.SIGNAL)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val appContext = getApplication<Application>()
    private val labelPrefs = appContext.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)
    private val _labels = MutableStateFlow(loadLabels())
    val labels: StateFlow<Map<String, String>> = _labels.asStateFlow()
    private val photoPrefs = appContext.getSharedPreferences(PHOTO_PREFS, Context.MODE_PRIVATE)
    private val _photos = MutableStateFlow(loadPhotos())
    val photos: StateFlow<Map<String, String>> = _photos.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deviceStates = mutableMapOf<String, DeviceState>()
    private val lock = Any()

    private var pendingBestAddress: String? = null
    private var pendingBestHits = 0

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var cleanupJob: Job? = null

    fun startScanning() {
        if (_isScanning.value) {
            return
        }
        if (!hasScanPermission()) {
            return
        }
        val adapter = bluetoothAdapter() ?: return
        if (!adapter.isEnabled) {
            return
        }
        scanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                stopScanning()
            }
        }

        scanCallback = callback
        try {
            scanner?.startScan(emptyList(), settings, callback)
            _isScanning.value = true
            startCleanup()
        } catch (_: SecurityException) {
            stopScanning()
        }
    }

    fun stopScanning() {
        cleanupJob?.cancel()
        cleanupJob = null
        if (_isScanning.value) {
            try {
                scanCallback?.let { scanner?.stopScan(it) }
            } catch (_: SecurityException) {
            }
        }
        _isScanning.value = false
    }

    fun selectDevice(device: ScanResultUi) {
        _screen.value = Screen.Identify(device)
    }

    fun identify(device: ScanResultUi) {
        stopScanning()
        if (!hasConnectPermission()) {
            _screen.value = Screen.Scan
            return
        }
        val adapter = bluetoothAdapter() ?: run {
            _screen.value = Screen.Scan
            return
        }
        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            _screen.value = Screen.Scan
            return
        }

        scope.launch {
            connectAndIdentify(remote)
            _screen.value = Screen.Scan
        }
    }

    fun backToScan() {
        _screen.value = Screen.Scan
    }

    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
        updateResults()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        updateResults()
    }

    fun setLabel(address: String, label: String) {
        val key = address.uppercase()
        val trimmed = label.trim()
        val updated = _labels.value.toMutableMap()
        if (trimmed.isBlank()) {
            updated.remove(key)
            labelPrefs.edit().remove(key).apply()
        } else {
            updated[key] = trimmed
            labelPrefs.edit().putString(key, trimmed).apply()
        }
        _labels.value = updated
        updateResults()
    }

    fun setPhoto(address: String, path: String?) {
        val key = address.uppercase()
        val updated = _photos.value.toMutableMap()
        if (path.isNullOrBlank()) {
            updated.remove(key)
            photoPrefs.edit().remove(key).apply()
        } else {
            updated[key] = path
            photoPrefs.edit().putString(key, path).apply()
        }
        _photos.value = updated
        updateResults()
    }

    fun exportLabelsJson(): String {
        val json = JSONObject()
        val labelsJson = JSONObject()
        for ((key, value) in _labels.value) {
            labelsJson.put(key, value)
        }
        json.put("labels", labelsJson)
        return json.toString()
    }

    fun importLabelsJson(raw: String) {
        val parsed = JSONObject(raw)
        val labelsJson = if (parsed.has("labels")) {
            parsed.getJSONObject("labels")
        } else {
            parsed
        }
        val updated = mutableMapOf<String, String>()
        val keys = labelsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = labelsJson.optString(key, "")
            if (value.isNotBlank()) {
                updated[key.uppercase()] = value.trim()
            }
        }
        labelPrefs.edit().clear().apply()
        val editor = labelPrefs.edit()
        for ((key, value) in updated) {
            editor.putString(key, value)
        }
        editor.apply()
        _labels.value = updated
        updateResults()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = appContext.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord
        val device = result.device ?: return
        val address = device.address.uppercase()
        val now = System.currentTimeMillis()
        val manufacturerData = record?.manufacturerSpecificData
        val hasManufacturerData = manufacturerData?.size()?.let { it > 0 } == true
        val hasNordicData =
            manufacturerData?.indexOfKey(NORDIC_COMPANY_ID)?.let { it >= 0 } == true

        synchronized(lock) {
            val state = deviceStates[address] ?: DeviceState(
                address = address,
                name = device.name ?: record?.deviceName
            )
            state.name = device.name ?: record?.deviceName ?: state.name
            state.lastSeenMs = now
            state.addRssi(result.rssi)
            state.hasManufacturerData = state.hasManufacturerData || hasManufacturerData
            state.hasNordicData = state.hasNordicData || hasNordicData
            deviceStates[address] = state
        }

        updateResults()
    }

    private fun startCleanup() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                updateResults()
            }
        }
    }

    private fun updateResults() {
        val now = System.currentTimeMillis()
        val activeStates = mutableListOf<DeviceState>()
        synchronized(lock) {
            val iterator = deviceStates.values.iterator()
            while (iterator.hasNext()) {
                val state = iterator.next()
                if (now - state.lastSeenMs > STALE_MS) {
                    iterator.remove()
                } else {
                    activeStates.add(state)
                }
            }
        }

        val filteredStates = when (_filterMode.value) {
            FilterMode.ALL -> activeStates
            FilterMode.BEACONS -> activeStates.filter {
                it.name?.startsWith(DAMP_PREFIX, ignoreCase = true) == true
            }
            FilterMode.NORDIC -> activeStates.filter { it.hasNordicData }
        }

        val labels = _labels.value
        val photos = _photos.value
        val allUiResults = filteredStates.map { it.toUi(labels[it.address], photos[it.address]) }
        val uiResults = sortUi(allUiResults, _sortMode.value).take(MAX_RESULTS)

        _scanResults.value = uiResults
        updateBestCandidate(allUiResults)
    }

    private fun updateBestCandidate(results: List<ScanResultUi>) {
        if (results.isEmpty()) {
            _bestCandidate.value = null
            pendingBestAddress = null
            pendingBestHits = 0
            return
        }
        val currentBest = results.maxByOrNull { it.averageRssi }
        val previous = _bestCandidate.value
        val previousStillVisible = previous?.address?.let { address ->
            results.any { it.address == address }
        } == true
        if (previous != null && !previousStillVisible) {
            _bestCandidate.value = null
            pendingBestAddress = null
            pendingBestHits = 0
        }
        if (previous != null && currentBest != null && previous.address == currentBest.address) {
            return
        }
        val requiredHits = BEST_CANDIDATE_STABLE_HITS
        if (currentBest == null) {
            _bestCandidate.value = null
            pendingBestAddress = null
            pendingBestHits = 0
            return
        }
        if (pendingBestAddress == currentBest.address) {
            pendingBestHits += 1
        } else {
            pendingBestAddress = currentBest.address
            pendingBestHits = 1
        }
        if (pendingBestHits >= requiredHits) {
            _bestCandidate.value = currentBest
            pendingBestAddress = null
            pendingBestHits = 0
        }
    }

    private suspend fun connectAndIdentify(device: android.bluetooth.BluetoothDevice) {
        val done = CompletableDeferred<Unit>()
        val closed = AtomicBoolean(false)

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.safeClose(closed)
                    done.complete(Unit)
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.safeClose(closed)
                    done.complete(Unit)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.disconnect()
                    return
                }
                val service = gatt.getService(IDENTIFY_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(IDENTIFY_CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    gatt.disconnect()
                    return
                }
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = byteArrayOf(IDENTIFY_PAYLOAD)
                val wrote = gatt.writeCharacteristic(characteristic)
                if (!wrote) {
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                gatt.disconnect()
            }
        }

        val gatt = try {
            device.connectGatt(appContext, false, callback)
        } catch (_: SecurityException) {
            null
        }

        if (gatt == null) {
            return
        }

        withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            done.await()
        }

        if (closed.compareAndSet(false, true)) {
            gatt.disconnect()
            gatt.close()
        }
    }

    private fun BluetoothGatt.safeClose(closed: AtomicBoolean) {
        if (closed.compareAndSet(false, true)) {
            close()
        }
    }


    private data class DeviceState(
        val address: String,
        var name: String?,
        var hasManufacturerData: Boolean = false,
        var hasNordicData: Boolean = false,
        val rssiWindow: ArrayDeque<Int> = ArrayDeque(),
        var lastSeenMs: Long = 0L
    ) {
        fun addRssi(rssi: Int) {
            if (rssiWindow.size >= RSSI_WINDOW_SIZE) {
                rssiWindow.removeFirst()
            }
            rssiWindow.addLast(rssi)
        }

        fun averageRssi(): Int {
            if (rssiWindow.isEmpty()) {
                return DEFAULT_RSSI
            }
            val total = rssiWindow.sum()
            return total / rssiWindow.size
        }

        fun toUi(label: String?, photoPath: String?): ScanResultUi {
            return ScanResultUi(
                address = address,
                name = name,
                label = label,
                photoPath = photoPath,
                averageRssi = averageRssi(),
                lastSeenMs = lastSeenMs
            )
        }
    }

    private fun loadLabels(): Map<String, String> {
        val stored = mutableMapOf<String, String>()
        for ((key, value) in labelPrefs.all) {
            if (value is String && value.isNotBlank()) {
                stored[key] = value
            }
        }
        return stored
    }

    private fun loadPhotos(): Map<String, String> {
        val stored = mutableMapOf<String, String>()
        for ((key, value) in photoPrefs.all) {
            if (value is String && value.isNotBlank()) {
                stored[key] = value
            }
        }
        return stored
    }

    private fun sortUi(list: List<ScanResultUi>, mode: SortMode): List<ScanResultUi> {
        return when (mode) {
            SortMode.SIGNAL -> list.sortedByDescending { it.averageRssi }
            SortMode.LABEL -> list.sortedWith(
                compareBy(
                    { (it.label ?: it.name ?: it.address).lowercase() },
                    { it.address }
                )
            )
            SortMode.ADDRESS -> list.sortedBy { it.address }
        }
    }

    private companion object {
        private const val RSSI_WINDOW_SIZE = 5
        private const val DEFAULT_RSSI = -100
        private const val STALE_MS = 10_000L
        private const val CLEANUP_INTERVAL_MS = 1_000L
        private const val MAX_RESULTS = 20
        private const val BEST_CANDIDATE_STABLE_HITS = 3
        private const val IDENTIFY_TIMEOUT_MS = 7_000L
        private const val IDENTIFY_PAYLOAD: Byte = 0x01
        private const val NORDIC_COMPANY_ID = 0x0059
        private const val DAMP_PREFIX = "DAMP"
        private const val LABEL_PREFS = "device_labels"
        private const val PHOTO_PREFS = "device_photos"
        private val IDENTIFY_SERVICE_UUID =
            UUID.fromString("9f2a0001-2c3d-4e5f-8899-aabbccddeeff")
        private val IDENTIFY_CHARACTERISTIC_UUID =
            UUID.fromString("9f2a0002-2c3d-4e5f-8899-aabbccddeeff")
    }
}
