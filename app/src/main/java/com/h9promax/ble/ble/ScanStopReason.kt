package com.h9promax.ble.ble

enum class ScanStopReason(val label: String) {
    USER("USER"),
    TIMEOUT("TIMEOUT"),
    ERROR("ERROR")
}