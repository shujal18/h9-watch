package com.h9promax.ble

import android.Manifest
import com.h9promax.ble.util.PermissionUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUtilsTest {

    @Test
    fun `api 23 uses fine and coarse location`() {
        val perms = PermissionUtils.bleScanPermissionsFor(23).toList()
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(perms.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(!perms.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun `api 30 uses fine and coarse location`() {
        val perms = PermissionUtils.bleScanPermissionsFor(30).toList()
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(perms.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(!perms.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun `api 31 uses bluetooth scan and connect`() {
        val perms = PermissionUtils.bleScanPermissionsFor(31).toList()
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(!perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun `api 34 uses bluetooth scan and connect`() {
        val perms = PermissionUtils.bleScanPermissionsFor(34).toList()
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }
}