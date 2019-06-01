package com.keylesspalace.tusky.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.keylesspalace.tusky.R

object RickRoll {
    fun shouldRickRoll(context: Context, domain: String) =
            context.resources.getStringArray(R.array.rick_roll_domains).contains(domain)

    fun rickRoll(context: Context) {
        val uri = Uri.parse(context.getString(R.string.rick_roll_url))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
