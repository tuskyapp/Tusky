package com.keylesspalace.tusky;

/**
 * This is just a wrapper class for a String.
 *
 * It was designed to get around the limitation of a Json deserializer which only allows custom
 * deserializing based on types, when special handling for a specific field was what was actually
 * desired (in this case, display names). So, it was most expedient to just make up a type.
 */
public class StringWithEmoji {
    public String value;

    public StringWithEmoji(String value) {
        this.value = value;
    }
}
