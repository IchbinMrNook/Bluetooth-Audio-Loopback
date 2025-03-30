package com.water.micloopback

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.water.micloopback.MainActivity

class LoopbackService : Service() {

    private var isLooping = false
    private lateinit var audioRecord: AudioRecord
    private lateinit var audioTrack: AudioTrack
    private lateinit var audioThread: Thread

    override fun onCreate() {
        super.onCreate()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.startBluetoothSco()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val bluetoothDevice = getBluetoothMicDevice()
        if (bluetoothDevice != null) {
            startLoopback(bluetoothDevice)
        } else {
            Log.e("LoopbackService", "No Bluetooth-Microphone was found")
            stopSelf() // If no Bluetooth microphone is found, stop the service immediately
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoopback()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.stopBluetoothSco()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getBluetoothMicDevice(): AudioDeviceInfo? {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startLoopback(bluetoothDevice: AudioDeviceInfo) {
        val sampleRate = 16000 // For stereo sound
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

        val audioRecordBuilder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val method = audioRecordBuilder.javaClass.getMethod("setPreferredDevice", AudioDeviceInfo::class.java)
                method.invoke(audioRecordBuilder, bluetoothDevice)
            } catch (e: Exception) {
                Log.e("LoopbackService", "setPreferredDevice not available", e)
            }
        }

        audioRecord = audioRecordBuilder.build()

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioRecord.startRecording()
        audioTrack.play()
        isLooping = true

        audioThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isLooping) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    audioTrack.write(buffer, 0, read)
                }
            }
        }
        audioThread.start()
    }

    private fun stopLoopback() {
        isLooping = false
        audioThread.join()
        audioRecord.stop()
        audioRecord.release()
        audioTrack.stop()
        audioTrack.release()
    }
}
