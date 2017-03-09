package com.keylesspalace.tusky;

import android.text.Spanned;

import com.emojione.Emojione;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

class SpannedTypeAdapter implements JsonDeserializer<Spanned> {
    @Override
    public Spanned deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return HtmlUtils.fromHtml(Emojione.shortnameToUnicode(json.getAsString(), false));
    }
}
