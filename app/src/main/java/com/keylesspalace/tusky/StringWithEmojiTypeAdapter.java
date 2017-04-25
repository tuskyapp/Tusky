package com.keylesspalace.tusky;

import com.emojione.Emojione;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/** This is a type-based workaround to allow for shortcode conversion when loading display names. */
class StringWithEmojiTypeAdapter implements JsonDeserializer<StringWithEmoji> {
    @Override
    public StringWithEmoji deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {
        String value = json.getAsString();
        if (value != null) {
            return new StringWithEmoji(Emojione.shortnameToUnicode(value, false));
        } else {
            return new StringWithEmoji("");
        }
    }
}
