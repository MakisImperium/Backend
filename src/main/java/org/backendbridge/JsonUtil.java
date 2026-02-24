package org.backendbridge;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtil {
    private JsonUtil() {}

    public static final ObjectMapper OM = new ObjectMapper();
}
