package com.example.mydemo260908

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private data class ControlState(
        var leftX: Int = 0,
        var leftY: Int = 0,
        var rightX: Int = 0,
        var rightY: Int = 0,
        var leftGear: Int = 1,
        var rightGear: Int = 1,
        var masterEnabled: Boolean = false,
    )

    private val controlState = ControlState()
    private val outputHandler = Handler(Looper.getMainLooper())
    private lateinit var outputText: TextView
    private lateinit var serialController: UsbSerialController
    private var packetSequence = 0
    private var serialStatus = UsbSerialController.Status(UsbSerialController.State.DISCONNECTED)

    private val outputTask = object : Runnable {
        override fun run() {
            val frame = buildControlFrame()
            outputText.text = ControlPacket.toHex(frame)
            serialController.send(frame)
            outputHandler.postDelayed(this, OUTPUT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        hideSystemBars()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        outputText = findViewById(R.id.outputText)
        serialController = UsbSerialController(this) {
            serialStatus = it
            renderStatus()
        }
        configureJoysticks()
        configureControls()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        serialController.start()
        outputHandler.post(outputTask)
    }

    override fun onStop() {
        outputHandler.removeCallbacks(outputTask)
        serialController.stop(buildControlFrame(enabled = false))
        super.onStop()
    }

    override fun onDestroy() {
        serialController.destroy()
        super.onDestroy()
    }

    private fun configureJoysticks() {
        val leftCoordinates = findViewById<TextView>(R.id.leftCoordinates)
        val rightCoordinates = findViewById<TextView>(R.id.rightCoordinates)

        findViewById<JoystickView>(R.id.leftJoystick).onMove = { x, y ->
            controlState.leftX = x
            controlState.leftY = y
            leftCoordinates.text = formatCoordinates(x, y)
        }

        findViewById<JoystickView>(R.id.rightJoystick).onMove = { x, y ->
            controlState.rightX = x
            controlState.rightY = y
            rightCoordinates.text = formatCoordinates(x, y)
        }
    }

    private fun configureControls() {
        findViewById<GearSelectorView>(R.id.leftGearButton).onGearChanged = {
            controlState.leftGear = it
        }
        findViewById<GearSelectorView>(R.id.rightGearButton).onGearChanged = {
            controlState.rightGear = it
        }
        findViewById<MasterSwitchView>(R.id.masterSwitch).onStateChanged = {
            setControllerEnabled(it)
        }
        findViewById<TextView>(R.id.statusText).setOnClickListener {
            serialController.connectOrRequestPermission()
        }
        setControllerEnabled(false)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setControllerEnabled(enabled: Boolean) {
        controlState.masterEnabled = enabled

        val leftJoystick = findViewById<JoystickView>(R.id.leftJoystick)
        val rightJoystick = findViewById<JoystickView>(R.id.rightJoystick)
        leftJoystick.isEnabled = enabled
        rightJoystick.isEnabled = enabled
        leftJoystick.alpha = if (enabled) 1f else DISABLED_ALPHA
        rightJoystick.alpha = if (enabled) 1f else DISABLED_ALPHA

        if (!enabled) {
            leftJoystick.center()
            rightJoystick.center()
        }

        renderStatus()
    }

    private fun renderStatus() {
        val controlLabel = getString(
            if (controlState.masterEnabled) R.string.control_on_short else R.string.control_off_short,
        )
        val serialLabel = getString(
            when (serialStatus.state) {
                UsbSerialController.State.DISCONNECTED -> R.string.serial_disconnected
                UsbSerialController.State.WAITING_PERMISSION -> R.string.serial_waiting_permission
                UsbSerialController.State.CONNECTING -> R.string.serial_connecting
                UsbSerialController.State.CONNECTED -> R.string.serial_connected
                UsbSerialController.State.PERMISSION_DENIED -> R.string.serial_permission_denied
                UsbSerialController.State.ERROR -> R.string.serial_error
            },
        )
        val statusColor = getColor(
            when (serialStatus.state) {
                UsbSerialController.State.CONNECTED -> R.color.status_on
                UsbSerialController.State.WAITING_PERMISSION,
                UsbSerialController.State.CONNECTING -> R.color.action_a
                UsbSerialController.State.PERMISSION_DENIED,
                UsbSerialController.State.ERROR -> R.color.action_b
                UsbSerialController.State.DISCONNECTED -> R.color.status_off
            },
        )
        val statusText = findViewById<TextView>(R.id.statusText)
        val statusDot = findViewById<View>(R.id.statusDot)
        statusText.text = getString(R.string.control_and_serial_status, controlLabel, serialLabel)
        statusText.setTextColor(statusColor)
        statusDot.backgroundTintList = ColorStateList.valueOf(statusColor)
    }

    private fun buildControlFrame(enabled: Boolean = controlState.masterEnabled): ByteArray {
        val frame = ControlPacket.create(
            sequence = packetSequence,
            enabled = enabled,
            leftX = controlState.leftX,
            leftY = controlState.leftY,
            rightX = controlState.rightX,
            rightY = controlState.rightY,
            leftGear = controlState.leftGear,
            rightGear = controlState.rightGear,
        )
        packetSequence = (packetSequence + 1) and 0xFF
        return frame
    }

    private fun formatCoordinates(x: Int, y: Int): String =
        String.format(Locale.US, "X %+04d   Y %+04d", x, y)

    private companion object {
        const val OUTPUT_INTERVAL_MS = 50L
        const val DISABLED_ALPHA = 0.42f
    }
}
