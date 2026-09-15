package com.h9promax.ble.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Permission and Bluetooth state helpers that work across
 * Android 6 (API 23) through modern Android versions.
 */
object PermissionUtils {

    private const val RC_ENABLE_BLUETOOTH = 2001
    private const val RC_BLE_PERMISSIONS = 2002

    /**
     * The runtime permissions required to scan BLE for the given SDK level.
     * Pure so it is unit-testable.
     * - API < 31: ACCESS_FINE_LOCATION
     * - API >= 31: BLUETOOTH_SCAN, BLUETOOTH_CONNECT
     */
    @JvmOverloads
    fun bleScanPermissionsFor(sdkInt: Int): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

    fun bleScanPermissions(): Array<String> = bleScanPermissionsFor(Build.VERSION.SDK_INT)

    fun hasBleScanPermissions(context: Context): Boolean {
        return bleScanPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestBlePermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity, bleScanPermissions(), RC_BLE_PERMISSIONS)
    }

    /**
     * True is any permission denial was "don't ask again" / permanently denied.
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }

    /**
     * Available public API to allow requesting user to enable Bluetooth.
     * (Requires BLUETOOTH_CONNECT on API 31+; the caller always routes this
     * through the permission flow first.)
     */
    @SuppressLint("MissingPermission")
    fun requestEnableBluetooth(activity: Activity) {
        val manager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter != null && !adapter.isEnabled) {
            activity.startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                RC_ENABLE_BLUETOOTH
            )
        }
    }

    fun rcRequestEnableBluetooth(): Int = RC_ENABLE_BLUETOOTH
    fun rcBlePermissions(): Int = RC_BLE_PERMISSIONS

    fun isParcelUuidSupported(): Boolean = true
}