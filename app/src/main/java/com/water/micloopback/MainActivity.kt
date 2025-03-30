package com.water.micloopback

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.water.micloopback.ui.theme.MicLoopbackTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var isRunning by mutableStateOf(false)
    private var isBluetoothConnected by mutableStateOf(false)  // Status whether Bluetooth is connected

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_DISCONNECTED) {
                    isRunning = false // Reset the state
                    isBluetoothConnected = false
                    Toast.makeText(context, "Bluetooth Microphone Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        // Register Bluetooth receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)

        setContent {
            MicLoopbackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoopbackControlUI()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the Bluetooth receiver when the activity is destroyed
        unregisterReceiver(bluetoothReceiver)
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                0
            )
        }
    }

    @Composable
    fun LoopbackControlUI() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally // Center the content horizontally
        ) {
            Text(
                text = if (isRunning) "Microphone-Loopback is active" else "Microphone-Loopback is inactive",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally) // Center the text
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val serviceIntent = Intent(this@MainActivity, LoopbackService::class.java)

                    if (isRunning) {
                        stopService(serviceIntent)
                        isRunning = false
                    } else {
                        val bluetoothDevice = getBluetoothMicDevice()
                        if (bluetoothDevice == null) {
                            Toast.makeText(this@MainActivity, "Bluetooth microphone not connected!", Toast.LENGTH_SHORT).show()
                            isBluetoothConnected = false
                            return@Button
                        }

                        startService(serviceIntent)
                        isRunning = true
                        isBluetoothConnected = true  // Set the status to connected
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Stop" else "Start")
            }

            Spacer(modifier = Modifier.height(50.dp))

            // Checking Bluetooth status
            LaunchedEffect(isBluetoothConnected) {
                while (true) {
                    delay(500) // Check every 500ms
                    checkBluetoothConnection() // Checks the Bluetooth status
                }
            }

            // Displaying the status and adjusting the color
            Text(
                text = if (isBluetoothConnected) "Bluetooth Microphone: Connected" else "Bluetooth Microphone: Disconnected",
                style = MaterialTheme.typography.titleLarge,
                color = if (isBluetoothConnected) Color.Green else Color.Red, // Green if connected, red if disconnected
                modifier = Modifier.align(Alignment.CenterHorizontally) // Center the text
            )
        }
    }

    private fun checkBluetoothConnection() {
        val bluetoothDevice = getBluetoothMicDevice()
        isBluetoothConnected = bluetoothDevice != null
    }

    private fun getBluetoothMicDevice(): AudioDeviceInfo? {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }
}
