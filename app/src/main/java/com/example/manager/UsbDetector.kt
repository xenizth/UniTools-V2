package com.example.manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UsbStatus(
    val isConnected: Boolean = false,
    val deviceName: String = "No USB Device Connected",
    val vendorId: Int = 0,
    val productId: Int = 0,
    val isUnisocBrom: Boolean = false,
    val deviceCount: Int = 0,
    // Whether the app currently holds Android's USB-host permission for the active device
    // (UsbManager.hasPermission). This only covers the standard Android USB permission grant —
    // it does NOT mean flashing itself can run without root. spd_dump (the bundled flashing
    // binary) opens the raw USB device node directly via libusb and has no code path that
    // accepts an already-open file descriptor from UsbDeviceConnection, so actually running a
    // flash/backup/unlock operation still requires root today regardless of this flag. This flag
    // exists so the app can properly request and reflect standard Android USB access, which is a
    // real prerequisite for any future non-root flashing path.
    val hasPermission: Boolean = false,
    val permissionRequestInFlight: Boolean = false
)

class UsbDetector(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _status = MutableStateFlow(UsbStatus())
    val status: StateFlow<UsbStatus> = _status.asStateFlow()

    // The device checkConnectedDevices() last picked as "active" (Unisoc match, or first device).
    // Kept so requestPermission() can be called with no arguments from the UI/ViewModel.
    private var activeDevice: UsbDevice? = null

    // Unisoc / Spreadtrum vendor ID is 0x1782 (6018 in decimal)
    companion object {
        const val UNISOC_VENDOR_ID = 0x1782
        private const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    checkConnectedDevices()
                }
                ACTION_USB_PERMISSION -> {
                    // Re-derive status from the current device list/permission state rather than
                    // trusting intent?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    // directly — the device may have been detached while the system dialog was up,
                    // and checkConnectedDevices() already handles that case consistently.
                    checkConnectedDevices()
                }
            }
        }
    }

    fun startListening() {
        checkConnectedDevices()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: Exception) {}
    }

    /**
     * Requests Android's standard USB-host permission for the currently active device, showing
     * the system "Allow app to access USB device?" dialog if it isn't already granted. Safe to
     * call with no device connected (no-op) or permission already held (no-op, system dialog
     * doesn't resurface).
     */
    fun requestPermission() {
        val manager = usbManager ?: return
        val device = activeDevice ?: return
        if (manager.hasPermission(device)) {
            checkConnectedDevices()
            return
        }
        _status.update { it.copy(permissionRequestInFlight = true) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Mutable: the system needs to write EXTRA_PERMISSION_GRANTED/EXTRA_DEVICE into the
            // intent it fires back at us. FLAG_IMMUTABLE here would make the permission grant
            // silently fail to deliver its result on API 31+.
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        manager.requestPermission(device, permissionIntent)
    }

    /** Opens a connection to the active device if Android's USB permission is currently held. */
    fun openActiveDeviceConnection(): UsbDeviceConnection? {
        val manager = usbManager ?: return null
        val device = activeDevice ?: return null
        if (!manager.hasPermission(device)) return null
        return manager.openDevice(device)
    }

    fun checkConnectedDevices() {
        val manager = usbManager ?: return
        val deviceList: Map<String, UsbDevice> = manager.deviceList

        if (deviceList.isEmpty()) {
            activeDevice = null
            _status.value = UsbStatus(
                isConnected = false,
                deviceName = "No USB Device Connected (OTG)",
                deviceCount = 0
            )
            return
        }

        // Search for Unisoc device or take the first connected device
        val unisocDevice = deviceList.values.find { it.vendorId == UNISOC_VENDOR_ID }
        val device = unisocDevice ?: deviceList.values.first()
        activeDevice = device

        val isBrom = device.vendorId == UNISOC_VENDOR_ID
        val nameStr = if (isBrom) {
            "Spreadtrum/Unisoc Device (VID: 0x${Integer.toHexString(device.vendorId)})"
        } else {
            val prod = device.productName ?: "USB Device"
            "$prod (VID: 0x${Integer.toHexString(device.vendorId)}, PID: 0x${Integer.toHexString(device.productId)})"
        }

        _status.value = UsbStatus(
            isConnected = true,
            deviceName = nameStr,
            vendorId = device.vendorId,
            productId = device.productId,
            isUnisocBrom = isBrom,
            deviceCount = deviceList.size,
            hasPermission = manager.hasPermission(device),
            permissionRequestInFlight = false
        )
    }
}
