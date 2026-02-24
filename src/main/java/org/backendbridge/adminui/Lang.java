package org.backendbridge.adminui;

public enum Lang {
    DE, EN;

    public static Lang fromStringOrNull(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "de", "de-de", "german" -> DE;
            case "en", "en-us", "en-gb", "english" -> EN;
            default -> null;
        };
    }

    public static Lang fromCookieOrDefault(String rawCookieValue) {
        Lang l = fromStringOrNull(rawCookieValue);
        return (l == null) ? DE : l;
    }

    public String cookieValue() {
        return this == DE ? "de" : "en";
    }
}