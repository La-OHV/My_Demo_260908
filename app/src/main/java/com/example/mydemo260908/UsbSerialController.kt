package com.example.mydemo260908

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class UsbSerialController(
    context: Context,
    private val onStatusChanged: (Status) -> Unit,
) {
    enum class State {
        DISCONNECTED,
        WAITING_PERMISSION,
        CONNECTING,
        CONNECTED,
        PERMISSION_DENIED,
        ERROR,
    }

    data class Status(val state: State, val deviceName: String? = null)

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val writePending = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val active = AtomicBoolean(false)

    @Volatile
    private var serialPort: UsbSerialPort? = null
    @Volatile
    private var pendingPermissionDeviceId: Int? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.usbDevice()
                        ?: pendingPermissionDeviceId?.let { requestedId ->
                            usbManager.deviceList.values.firstOrNull { it.deviceId == requestedId }
                        }
                    pendingPermissionDeviceId = null
                    if (device == null) {
                        emit(Status(State.PERMISSION_DENIED))
                    } else if (
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ||
                        usbManager.hasPermission(device)
                    ) {
                        connect(device)
                    } else {
                        emit(Status(State.PERMISSION_DENIED, device.productName))
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> connectOrRequestPermission()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detachedDevice = intent.usbDevice()
                    if (detachedDevice == null || serialPort?.device?.deviceId == detachedDevice.deviceId) {
                        closePort(State.DISCONNECTED)
                    }
                }
            }
        }
    }

    fun start() {
        active.set(true)
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
        connectOrRequestPermission()
    }

    fun stop(finalFrame: ByteArray? = null) {
        active.set(false)
        if (receiverRegistered) {
            appContext.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        closePort(State.DISCONNECTED, finalFrame)
    }

    fun destroy() {
        stop()
        ioExecutor.shutdown()
    }

    fun connectOrRequestPermission() {
        if (!active.get() || serialPort != null || connecting.get() || pendingPermissionDeviceId != null) return
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
        if (driver == null) {
            emit(Status(State.DISCONNECTED))
            return
        }

        val device = driver.device
        if (usbManager.hasPermission(device)) {
            connect(device)
        } else {
            pendingPermissionDeviceId = device.deviceId
            emit(Status(State.WAITING_PERMISSION, device.productName))
            val permissionIntent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                0,
                permissionIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            usbManager.requestPermission(device, pendingIntent)
        }
    }

    fun send(frame: ByteArray) {
        val port = serialPort ?: return
        if (!writePending.compareAndSet(false, true)) return
        ioExecutor.execute {
            try {
                port.write(frame, WRITE_TIMEOUT_MS)
            } catch (_: IOException) {
                closePort(State.ERROR)
            } finally {
                writePending.set(false)
            }
        }
    }

    private fun connect(device: UsbDevice) {
        if (!active.get() || serialPort != null || !connecting.compareAndSet(false, true)) return
        emit(Status(State.CONNECTING, device.productName))
        ioExecutor.execute {
            var connection: UsbDeviceConnection? = null
            var openedPort: UsbSerialPort? = null
            try {
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                connection = usbManager.openDevice(device)
                if (driver == null || connection == null || driver.ports.isEmpty()) {
                    connection?.close()
                    emit(Status(State.ERROR, device.productName))
                    return@execute
                }

                val port = driver.ports.first()
                openedPort = port
                port.open(connection)
                port.setParameters(
                    BAUD_RATE,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE,
                )
                if (active.get()) {
                    serialPort = port
                    openedPort = null
                    connection = null
                    emit(Status(State.CONNECTED, device.productName))
                } else {
                    port.close()
                }
            } catch (_: Exception) {
                openedPort?.let { runCatching { it.close() } }
                connection?.close()
                if (active.get()) emit(Status(State.ERROR, device.productName))
            } finally {
                connecting.set(false)
            }
        }
    }

    private fun closePort(nextState: State, finalFrame: ByteArray? = null) {
        pendingPermissionDeviceId = null
        val port = serialPort
        serialPort = null
        if (port != null) {
            ioExecutor.execute {
                if (finalFrame != null) {
                    runCatching { port.write(finalFrame, WRITE_TIMEOUT_MS) }
                }
                runCatching { port.close() }
            }
        }
        writePending.set(false)
        emit(Status(nextState))
    }

    private fun emit(status: Status) {
        mainHandler.post { onStatusChanged(status) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice

    private companion object {
        const val ACTION_USB_PERMISSION = "com.example.mydemo260908.USB_PERMISSION"
        const val BAUD_RATE = 115200
        const val WRITE_TIMEOUT_MS = 100
    }
}
