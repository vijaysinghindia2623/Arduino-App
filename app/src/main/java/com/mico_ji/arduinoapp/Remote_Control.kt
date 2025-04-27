package com.mico_ji.arduinoapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.ToggleButton
import java.io.InputStream

class Remote_Control : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var hc05Device: BluetoothDevice? = null
    private var isConnected: Boolean = false
    private lateinit var toggleButton: ToggleButton
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)

        btnForward = findViewById<Button>(R.id.btnForward)
        btnBackward = findViewById<Button>(R.id.btnBackward)
        btnLeft = findViewById<Button>(R.id.btnLeft)
        btnRight = findViewById<Button>(R.id.btnRight)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val statusText = findViewById<TextView>(R.id.tvConnectionStatus)
        val layout = findViewById<View>(R.id.layout)
        toggleButton = findViewById<ToggleButton>(R.id.toggleButton)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Initialize UI with disconnected state //
        updateConnectionState(false)
        // Button unusable at the start
        toggleButton.isEnabled = false
        btnForward.isEnabled = false
        btnLeft.isEnabled = false
        btnBackward.isEnabled = false
        btnRight.isEnabled = false

        btnConnect.setOnClickListener {
            if (!isConnected) {
                connectToBluetooth()
            } else {
                disconnectBluetooth()
            }
        }

        // Set touch listeners for movement controls
        btnForward.setOnTouchListener { _, event -> handleTouch(event, 'F') }
        btnBackward.setOnTouchListener { _, event -> handleTouch(event, 'B') }
        btnLeft.setOnTouchListener { _, event -> handleTouch(event, 'L') }
        btnRight.setOnTouchListener { _, event -> handleTouch(event, 'R') }

        // Toggle touch
        toggleButton.setOnClickListener {
            toggleCommand()
        }
    }

    private fun handleTouch(event: MotionEvent, command: Char): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isConnected) sendCommand(command)
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isConnected) sendCommand('S') // Send stop command when released
                true
            }
            else -> false
        }
    }

    private fun connectToBluetooth() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported")
            return
        }
        // Bluetooth supported but not Turned on
        if (!bluetoothAdapter.isEnabled) {
            showToast("Bluetooth is disabled. Please enable it.")
            return
        }

        // Request permissions for Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 1)
                return
            }
        }

        // Get the paired devices and find HC-05
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        hc05Device = pairedDevices?.find { it.name == "HC-05" }

        if (hc05Device == null) {
            showToast("HC-05 not found. Pair it first!")
            return
        }

        try {
            bluetoothSocket = hc05Device!!.createRfcommSocketToServiceRecord(
                UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            )
            bluetoothSocket?.connect()
            isConnected = true
            enableControls(true)
            updateConnectionState(true)
            showToast("Connected to HC-05 (${hc05Device!!.address})")
        } catch (e: IOException) {
            showToast("Connection Failed")
            enableControls(false)
            updateConnectionState(false)
        }
    }

    private fun disconnectBluetooth() {
        try {
            bluetoothSocket?.close()
            isConnected = false
            enableControls(false)
            updateConnectionState(false)
            showToast("Disconnected from HC-05")
        } catch (e: IOException) {
            showToast("Error while disconnecting")
        }
    }

    private fun sendCommand(command: Char) {
        if (!isConnected) {
            // showToast("Not connected to Bluetooth")
            return
        }

        try {
            bluetoothSocket?.outputStream?.write(command.code)
        } catch (e: IOException) {
            showToast("Failed to send command")
            isConnected = false
            updateConnectionState(false)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateConnectionState(connected: Boolean) {
        val statusText = findViewById<TextView>(R.id.tvConnectionStatus)
        val layout = findViewById<View>(R.id.layout)
        val btnConnect = findViewById<Button>(R.id.btnConnect)

        if (connected) {
            statusText.text = "Connected"
            layout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnConnect.text = "Disconnect Bluetooth"
        } else {
            statusText.text = "Disconnected"
            layout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            btnConnect.text = "Connect Bluetooth"
        }
    }

    private fun toggleCommand() {
        if (toggleButton.isChecked) {
            sendCommand('F')
        } else {
            sendCommand('S')
        }
    }

    private fun enableControls(connected: Boolean) {
        if (connected) {
            toggleButton.isEnabled = true
            btnForward.isEnabled = true
            btnLeft.isEnabled = true
            btnBackward.isEnabled = true
            btnRight.isEnabled = true
        } else {
            toggleButton.isEnabled = false
            btnForward.isEnabled = false
            btnLeft.isEnabled = false
            btnBackward.isEnabled = false
            btnRight.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectBluetooth()
    }
}
