package org.backendbridge.adminui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.zip.CRC32;

/**
 * Admin UI utilities:
 * - escaping
 * - formatting
 * - deterministic avatars
 */
public final class AdminUiUtil {

    private AdminUiUtil() {}

    public static String toIso(Timestamp ts) {
        return ts == null ? "" : ts.toInstant().toString();
    }

    public static String toIsoNullable(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    public static String urlEncode(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) return "0s";
        long s = seconds;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;

        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String escAttr(String s) {
        if (s == null) return "";
        return esc(s).replace("'", "&#39;");
    }

    public static long stableHash(String s) {
        if (s == null) return 0;
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    public static String minecraftAvatarUrl32(String seed) {
        String[] skins = {
                "Steve", "Alex", "Notch", "Jeb_", "Dinnerbone",
                "Herobrine", "Grumm", "Creeper", "Enderman", "Zombie"
        };

        long h = stableHash(seed);
        int idx = (int) (Math.floorMod(h, skins.length));
        String name = skins[idx];

        return "https://minotar.net/avatar/" + urlEncode(name) + "/32";
    }
}