package com.mico_ji.arduinoapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class Live_Data : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var hc05Device: BluetoothDevice? = null
    private var isConnected: Boolean = false
    private lateinit var toggleButton: ToggleButton
    private lateinit var scrollView: ScrollView
    private lateinit var graphView: GraphView
    private lateinit var btnViewCsv: Button
    private lateinit var btnClearData: Button

    private val REQUEST_CODE_STORAGE_PERMISSION = 1001
    private val REQUEST_CODE_MANAGE_STORAGE = 1002

    private val FILE_NAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // Graph variables
    private lateinit var series: LineGraphSeries<DataPoint>
    private var graphLastXValue = 0.0
    private val MAX_DATA_POINTS = 100
    private val dataPoints = LinkedList<DataPoint>()
    private var readingThread: Thread? = null
    private var isReading = false
    private var currentCsvFile: File? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val statusText = findViewById<TextView>(R.id.tvConnectionStatus)
        val layout = findViewById<View>(R.id.layout)
        graphView = findViewById(R.id.graphv)

        // Configure graph view
        setupGraphView()

        toggleButton = findViewById(R.id.toggleButton)
        scrollView = findViewById(R.id.scrollView)
        btnViewCsv = findViewById(R.id.btnExportCsv) // Reusing the same button ID
        btnClearData = findViewById(R.id.btnClearData)

        @Suppress("DEPRECATION")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Initialize UI with disconnected state
        updateConnectionState(false)
        toggleButton.isEnabled = false
        scrollView.visibility = View.GONE
        graphView.visibility = View.GONE
        btnClearData.visibility = View.GONE
        toggleButton.visibility = View.GONE

        btnConnect.setOnClickListener {
            if (!isConnected) {
                connectToBluetooth()
            } else {
                disconnectBluetooth()
            }
        }

        toggleButton.setOnClickListener {
            if (toggleButton.isChecked) {
                startReadingData()
            } else {
                stopReadingData()
            }
        }

        btnViewCsv.setOnClickListener {
            checkStoragePermissions()
        }

        btnClearData.setOnClickListener {
            clearData()
        }
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                }
            } else {
                startCsvViewActivity()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
            } else {
                startCsvViewActivity()
            }
        }
    }

    private fun startCsvViewActivity() {
        startActivity(Intent(this, CsvFilesActivity::class.java))
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

    private fun enableToggle(connected: Boolean) {
        if (connected) {
            toggleButton.isEnabled = true
            toggleButton.visibility = View.VISIBLE
            scrollView.visibility = View.VISIBLE
            graphView.visibility = View.VISIBLE
            btnViewCsv.visibility = View.VISIBLE
            btnClearData.visibility = View.VISIBLE
        } else {
            scrollView.visibility = View.GONE
            graphView.visibility = View.GONE
            btnClearData.visibility = View.GONE
            toggleButton.visibility = View.GONE
        }
    }

    private fun connectToBluetooth() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            showToast("Bluetooth is disabled. Please enable it.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            enableToggle(true)
            updateConnectionState(true)
            showToast("Connected to HC-05 (${hc05Device!!.address})")
        } catch (e: IOException) {
            showToast("Connection Failed")
            enableToggle(false)
            updateConnectionState(false)
        }
    }

    private fun disconnectBluetooth() {
        stopReadingData()
        try {
            bluetoothSocket?.close()
            isConnected = false
            enableToggle(false)
            updateConnectionState(false)
            showToast("Disconnected from HC-05")
        } catch (e: IOException) {
            showToast("Error while disconnecting")
        }
    }

    private fun setupGraphView() {
        series = LineGraphSeries(arrayOf())
        graphView.addSeries(series)

        series.color = Color.BLUE
        series.isDrawBackground = true
        series.backgroundColor = Color.argb(50, 0, 0, 255)
        series.thickness = 5

        graphView.viewport.isScalable = true
        graphView.viewport.isScrollable = true
        graphView.viewport.setScalableY(true)
        graphView.viewport.setScrollableY(true)

        graphView.viewport.isXAxisBoundsManual = true
        graphView.viewport.setMinX(0.0)
        graphView.viewport.setMaxX(20.0)

        graphView.viewport.isYAxisBoundsManual = true
        graphView.viewport.setMinY(0.0)
        graphView.viewport.setMaxY(30.0)

        graphView.title = "Real-Time Sensor Data"
        graphView.titleColor = Color.BLACK
        graphView.titleTextSize = 30f
        graphView.gridLabelRenderer.apply {
            verticalAxisTitle = "Value"
            horizontalAxisTitle = "Time (s)"
            numVerticalLabels = 5
            numHorizontalLabels = 6
            gridColor = Color.LTGRAY
            horizontalLabelsColor = Color.BLACK
            verticalLabelsColor = Color.BLACK
        }
    }

    private fun startReadingData() {
        if (!isConnected) {
            showToast("Not connected to Bluetooth")
            toggleButton.isChecked = false
            return
        }

        if (isReading) return

        isReading = true
        readingThread = thread(start = true) {
            readData()
        }
    }

    private fun stopReadingData() {
        isReading = false
        readingThread?.interrupt()
        readingThread = null
    }

    @SuppressLint("SetTextI18n")
    private fun readData() {
        if (!isConnected) {
            showToast("Not connected")
            return
        }

        try {
            val inputStream: InputStream? = bluetoothSocket?.inputStream
            val buffer = ByteArray(1024)
            var bytes: Int

            while (isReading && isConnected && toggleButton.isChecked) {
                bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val receivedData = String(buffer, 0, bytes).trim()

                    try {
                        val value = receivedData.toDouble()

                        runOnUiThread {
                            printData(receivedData)
                            logToCsv(receivedData)
                            addDataPoint(value)
                        }
                    } catch (e: NumberFormatException) {
                        runOnUiThread {
                            printData(receivedData)
                        }
                    }
                }
                Thread.sleep(1000)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                showToast("Error reading data from Bluetooth")
                if (isConnected) {
                    disconnectBluetooth()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                showToast("An unexpected error occurred")
            }
        }
    }

    private fun addDataPoint(value: Double) {
        graphLastXValue += 1.0
        val newDataPoint = DataPoint(graphLastXValue, value)
        dataPoints.add(newDataPoint)

        if (dataPoints.size > MAX_DATA_POINTS) {
            dataPoints.removeFirst()
        }

        series.resetData(dataPoints.toTypedArray())

        if (graphLastXValue > graphView.viewport.getMaxX(false)) {
            graphView.viewport.setMaxX(graphLastXValue)
            graphView.viewport.setMinX(graphLastXValue - 30)
        }

        val maxY = dataPoints.maxOfOrNull { it.y } ?: 100.0
        val minY = dataPoints.minOfOrNull { it.y } ?: 0.0
        graphView.viewport.setMaxY(maxY + 10)
        graphView.viewport.setMinY(minY - 10)
    }

    private fun printData(dataPrint: String) {
        val dataBoxText = findViewById<TextView>(R.id.data)
        val currentDateTime = Date()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormat.format(currentDateTime)

        dataBoxText.append("[$currentTime]  $dataPrint\n")

        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun logToCsv(value: String) {
        try {
            val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "RC_Car_Data")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RC_Car_Data")
            }

            if (!folder.exists()) folder.mkdirs()

            if (currentCsvFile == null) {
                val fileName = "sensor_live_${FILE_NAME_FORMAT.format(Date())}.csv"
                currentCsvFile = File(folder, fileName)

                if (!currentCsvFile!!.exists()) {
                    currentCsvFile!!.createNewFile()
                    currentCsvFile!!.appendText("Date,Time,I/P Value\n")
                }
            }

            val now = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            val date = dateFormat.format(now)
            val time = timeFormat.format(now)

            currentCsvFile!!.appendText("$date,$time,$value\n")
        } catch (e: Exception) {
            Log.e("CSV Logging", "Failed to write to CSV: ${e.localizedMessage}")
        }
    }

    private fun clearData() {
        dataPoints.clear()
        series.resetData(arrayOf())
        graphLastXValue = 0.0
        graphView.viewport.setMinX(0.0)
        graphView.viewport.setMaxX(30.0)
        findViewById<TextView>(R.id.data).text = "Waiting for sensor data..."
        showToast("Data cleared")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCsvViewActivity()
                } else {
                    showToast("Storage permission denied")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        startCsvViewActivity()
                    } else {
                        showToast("Storage access denied")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearData()
        disconnectBluetooth()
    }
}