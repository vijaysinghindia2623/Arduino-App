# Arduino Bluetooth Rover Car & Android App

This project is a Bluetooth-controlled rover car built using **Arduino Uno**, **L293D Motor Driver Shield**, **HC-05 Bluetooth module**, and **DHT11 temperature sensor**, integrated with an Android application that controls the rover and displays live sensor data.

## 🔧 Features

### Hardware
- Rover car controlled via commands sent over Bluetooth
- HC-05 module for wireless communication
- DHT11 sensor to read live temperature data
- L293D driver shield to control motors

### Android App
- Control rover movement (forward, backward, left, right, stop)
- Establish Bluetooth communication with Arduino
- Receive live sensor data via serial Bluetooth
- Plot real-time graph of sensor readings
- Export sensor logs to a CSV file

## 📱 App Screenshots
Rover Car Module -> 
![1](https://github.com/user-attachments/assets/e67c65d1-23e0-42e8-b393-e262df4d2d47)


Live Sensor Module ->
![2](https://github.com/user-attachments/assets/a4318830-b3e0-49cd-a09a-ecf0ade7e944)


Live Senosr Module -> CSV Manager (works when even when no Bluetooth connected ->
![3](https://github.com/user-attachments/assets/1d926a22-b863-4825-a682-61c88f52b55e)


## 🚗 Hardware Components Used
- Arduino Uno
- L293D Motor Driver Shield
- HC-05 Bluetooth Module
- DHT11 Sensor
- DC Motors
- Chassis & Wheels
- Jumper wires and battery pack

## 🧠 How It Works

1. The Android app connects to the Arduino via Bluetooth.
2. Users can control the rover using button commands sent over serial communication.
3. The DHT11 sensor reads temperature data and sends it to the Android app.
4. The app logs incoming data and plots it live on a graph, once the toggle button is set 'ON'.
5. Users can export the recorded data as a `.csv` file.

  
