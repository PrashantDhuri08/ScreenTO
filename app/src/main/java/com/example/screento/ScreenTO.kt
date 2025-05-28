package com.example.screento

// Replace with your package name

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi

@TargetApi(Build.VERSION_CODES.N) // TileService requires API 24 (Nougat)
class ScreenTimeoutTileService : TileService() {

    // Define the timeout values in milliseconds
    // Adjust these values as needed
    private val timeoutValues = listOf(
        15_000,    // 15 seconds
        30_000,    // 30 seconds
        60_000,    // 1 minute
        120_000,   // 2 minutes
        300_000,   // 5 minutes
        600_000,   // 10 minutes
        1_800_000, // 30 minutes


    )



    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartListening() {
        super.onStartListening()
        Log.d("TimeoutTile", "Starting to listen")
        updateTile() // Update tile state when it becomes visible
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d("TimeoutTile", "Stopping listening")
        // Optional: Cleanup if needed
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onTileAdded() {
        super.onTileAdded()
        Log.d("TimeoutTile", "Tile added by user")
        // Optional: Initial setup when user adds the tile
        updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d("TimeoutTile", "Tile removed by user")
        // Optional: Cleanup when user removes the tile
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onClick() {
        super.onClick()
        Log.d("TimeoutTile", "Tile clicked")

        if (!hasWriteSettingsPermission()) {
            promptForWriteSettingsPermission()
            return // Don't proceed if permission is missing
        }

        // Permission granted, proceed to change timeout
        changeTimeout()
        updateTile() // Update the tile immediately after click
    }

    // --- Helper Functions ---

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateTile() {
        val tile = qsTile ?: return // Get the tile object

        if (!hasWriteSettingsPermission()) {
            // Indicate permission is needed
            tile.label = getString(R.string.tile_label) // App Name or "Timeout"
            tile.subtitle = getString(R.string.permission_needed)
            tile.state = Tile.STATE_INACTIVE // Indicate it needs interaction
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_timeout_off) // Use a distinct icon
        } else {
            // Permission granted, show current timeout
            val currentTimeout = getCurrentTimeout()
            val timeoutString = formatTimeout(currentTimeout)

            Log.d("TimeoutTile", "Updating tile. Current timeout: $currentTimeout ms ($timeoutString)")

            tile.label = getString(R.string.tile_label) // Consistent label
            tile.subtitle = timeoutString // Show current value
            tile.state = Tile.STATE_ACTIVE // Tile is functional
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_timeout_on) // Use the active icon
        }

        tile.updateTile() // Apply the changes
    }

    private fun changeTimeout() {
        val currentTimeout = getCurrentTimeout()
        val currentIndex = timeoutValues.indexOf(currentTimeout)
        val nextIndex = (currentIndex + 1) % timeoutValues.size
        val nextTimeout = timeoutValues[nextIndex]

        Log.d("TimeoutTile", "Changing timeout from $currentTimeout ms to $nextTimeout ms")

        try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                nextTimeout
            )
            // Optional: Show a brief confirmation
            val confirmationMsg = getString(R.string.timeout_set_to, formatTimeout(nextTimeout))
            Toast.makeText(applicationContext, confirmationMsg, Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            Log.e("TimeoutTile", "SecurityException: Failed to write screen timeout setting.", e)
            Toast.makeText(applicationContext, R.string.error_setting_timeout, Toast.LENGTH_LONG).show()
            // This catch might be redundant if hasWriteSettingsPermission() works correctly,
            // but it's good practice for robustness.
        } catch (e: Exception) {
            Log.e("TimeoutTile", "Exception: Failed to write screen timeout setting.", e)
            Toast.makeText(applicationContext, R.string.error_setting_timeout, Toast.LENGTH_LONG).show()
        }
    }

    private fun getCurrentTimeout(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Settings.SettingNotFoundException) {
            Log.w("TimeoutTile", "Screen timeout setting not found, using default (30s)", e)
            30000 // Default to 30 seconds if setting is not found
        }
    }

    private fun formatTimeout(timeoutMs: Int): String {
        return when (timeoutMs) {
            -1 -> getString(R.string.timeout_never) // Handle "never" specifically
            in 1..59_999 -> "${timeoutMs / 1000}${getString(R.string.unit_seconds)}" // Seconds
            else -> "${timeoutMs / 60_000}${getString(R.string.unit_minutes)}" // Minutes
        }
    }

    private fun hasWriteSettingsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(this)
        } else {
            // WRITE_SETTINGS permission is automatically granted before Marshmallow (API 23)
            // if declared in the manifest, but TileService requires API 24 anyway.
            // This check is more for completeness/potential backports if needed.
            true // Or check using PackageManager if targeting lower APIs (not relevant for TileService)
        }
    }

    private fun promptForWriteSettingsPermission() {
        Toast.makeText(
            applicationContext,
            R.string.grant_write_settings_prompt,
            Toast.LENGTH_LONG
        ).show()

        // Create Intent to open the Write Settings screen for this app
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required when starting Activity from Service
        }

        try {
            // Using startActivityAndCollapse only works on Android S+ (API 31) and when called from onClick
            // For broader compatibility, use regular startActivity
            startActivity(intent)

            // Optional: Collapse the QS panel after launching the settings activity
            // Requires API 31+
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //     startActivityAndCollapse(intent)
            // } else {
            //     startActivity(intent)
            //     // You might manually request panel collapse on older versions if needed,
            //     // but it's often okay to leave it open.
            // }

        } catch (e: Exception) {
            Log.e("TimeoutTile", "Failed to launch Write Settings activity", e)
            Toast.makeText(applicationContext, R.string.error_opening_settings, Toast.LENGTH_LONG).show()
        }
    }
}