package com.mico_ji.arduinoapp


import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mico_ji.arduinoapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the ViewBinding object
        var binding = ActivityMainBinding.inflate(layoutInflater)

        // Set the root view of the layout
        setContentView(binding.root)

        // Setting up listeners
        binding.imageRemoteCar.setOnClickListener {
            // Start RemoteCarActivity
            startActivity(Intent(this, Remote_Control::class.java))
        }

        binding.imageSensorGraph.setOnClickListener {
            // Start SensorGraphActivity
            startActivity(Intent(this, Live_Data::class.java))
        }
    }
}
