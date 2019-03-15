package com.keylesspalace.tusky.json

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.keylesspalace.tusky.entity.Notification

import java.lang.reflect.Type

class NotificationTypeAdapter : JsonDeserializer<Notification.Type> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Notification.Type {
        return Notification.Type.byString(json.asString)
    }

}
