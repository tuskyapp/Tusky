package com.keylesspalace.tusky.service

import android.content.Context
import android.os.Build

interface ServiceClient {
    fun sendToot(tootToSend: TootToSend)
}

class ServiceClientImpl(private val context: Context) : ServiceClient {
    override fun sendToot(tootToSend: TootToSend) {
        val intent = SendTootService.sendTootIntent(context, tootToSend)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}