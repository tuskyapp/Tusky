package tech.bigfig.roma.util

import android.net.Uri
import tech.bigfig.roma.db.AccountEntity
import tech.bigfig.roma.entity.push.*

fun getPublicKey(): String {
    return "BEpPCn0cfs3P0E0fY-gyOuahx5dW5N8quUowlrPyfXlMa6tABLqqcSpOpMnC1-o_UB_s4R8NQsqMLbASjnqSbqw="
}

fun getAuth(): String {
    return "T5bhIIyre5TDC1LyX4mFAQ=="
}

fun getLink(token: String, account: AccountEntity): String {
    val uri = Uri.Builder()
            .scheme("https")
            .authority("pushrelay-roma1-fcm.your.org")
            .appendPath("push")
            .appendPath(token)
            .appendQueryParameter("account",account.username)
            .appendQueryParameter("server",account.domain)
            .build()
    return uri.toString()/*"https://pushrelay-roma1-fcm.your.org/push/$token"*/
}

fun getPushSubscriptionBody(token: String, account: AccountEntity):PushSubscriptionRequest {
    return PushSubscriptionRequest(PushSubscription(getLink(token,account),
            PushKeys(getPublicKey(), getAuth())),
            PushData(PushAlerts(true, true, true, true)))
}