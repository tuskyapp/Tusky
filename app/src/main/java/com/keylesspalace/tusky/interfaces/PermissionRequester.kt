package com.keylesspalace.tusky.interfaces

fun interface PermissionRequester {
    fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray)
}
