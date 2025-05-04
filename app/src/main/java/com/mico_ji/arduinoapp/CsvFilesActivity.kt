package com.mico_ji.arduinoapp

import java.util.Date
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class CsvFilesActivity : AppCompatActivity() {

    private lateinit var csvFiles: List<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_csv_files)

        val folder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "RC_Car_Data")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RC_Car_Data")
        }

        // Get all CSV files sorted by creation date (newest first)
        csvFiles = folder.listFiles { file ->
            file.isFile && file.name.endsWith(".csv")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val listView = findViewById<ListView>(R.id.csvListView)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            csvFiles.map { it.name }
        )
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            openCsvFile(csvFiles[position])
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            showFileOptionsDialog(csvFiles[position])
            true
        }
    }

    private fun openCsvFile(file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Open CSV file")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(chooser)
            } else {
                Toast.makeText(this, "No app available to open CSV files", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            showToast("Error opening file")
            Log.e("OpenCSV", "Error: ${e.localizedMessage}")
        }
    }



    private fun shareCsvFile(file: File) {
        try {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share CSV file"))
        } catch (e: Exception) {
            showToast("Error sharing file")
        }
    }




    private fun showFileOptionsDialog(file: File) {
        val options = arrayOf("View", "Delete", "Share", "File Properties")

        AlertDialog.Builder(this)
            .setTitle("File Options")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openCsvFile(file)
                    1 -> confirmDeleteFile(file)
                    2 -> shareCsvFile(file)
                    3 -> showFileProperties(file)
                }
            }
            .create()
            .show()
    }

    private fun deleteFile(file: File) {
        if (file.delete()) {
            // Refresh the list
            csvFiles = csvFiles.filter { it.path != file.path }
            (findViewById<ListView>(R.id.csvListView).adapter as ArrayAdapter<String>).apply {
                clear()
                addAll(csvFiles.map { it.name })
                notifyDataSetChanged()
            }
            Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }


    private fun confirmDeleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteFile(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showFileProperties(file: File) {
        val properties = """
        Name: ${file.name}
        Size: ${file.length()} bytes
        Path: ${file.absolutePath}
        Modified: ${Date(file.lastModified())}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("File Properties")
            .setMessage(properties)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
