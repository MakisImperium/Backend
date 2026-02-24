package org.backendbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Small JSON helpers.
 *
 * <p>Use:</p>
 * <ul>
 *   <li>{@link #js(String)} for manual JSON snippets</li>
 *   <li>{@link #OM} for serialization/deserialization</li>
 * </ul>
 */
public final class Json {

    private Json() {}

    public static final ObjectMapper OM = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Escapes a Java string as a JSON string literal.
     * Returns {@code "null"} if {@code s} is null.
     */
    public static String js(String s) {
        if (s == null) return "null";
        String esc = s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + esc + "\"";
    }
}