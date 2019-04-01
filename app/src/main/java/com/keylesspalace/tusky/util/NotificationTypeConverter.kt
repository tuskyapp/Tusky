package com.keylesspalace.tusky.util

import com.keylesspalace.tusky.entity.Notification
import org.json.JSONArray

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 01/04/2019.
 */

fun serialize(data: Set<Notification.Type>?): String{
    val array = JSONArray()
    data?.forEach {
        array.put(it.presentation)
    }
    return array.toString()
}

fun desirialize(data:String?):Set<Notification.Type>{
    val ret = HashSet<Notification.Type>()
    data?.let {
        val array = JSONArray(data)
        for (i in 0..(array.length() - 1)) {
            val item = array.getString(i)
            val type = Notification.Type.byString(item)
            if (type != Notification.Type.UNKNOWN)
                ret.add(type)
        }
    }
    return ret
}