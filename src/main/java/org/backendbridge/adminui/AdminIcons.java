package org.backendbridge.adminui;

/**
 * Inline SVG icons (no assets needed).
 */
public final class AdminIcons {

    private AdminIcons() {}

    private static String svg(String inner) {
        return "<svg viewBox='0 0 24 24' aria-hidden='true' focusable='false'>" + inner + "</svg>";
    }

    public static String iconCopy() { return svg("<path d='M9 9h10v10H9z'/><path d='M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1'/>"); }
    public static String iconDoor() { return svg("<path d='M10 17l5-5-5-5'/><path d='M15 12H3'/><path d='M21 3v18'/><path d='M21 4H11'/><path d='M21 20H11'/>"); }
    public static String iconUsers() { return svg("<path d='M16 11a3 3 0 1 0-3-3 3 3 0 0 0 3 3Z'/><path d='M4 20v-1a4 4 0 0 1 4-4h1'/><path d='M20 20v-1a4 4 0 0 0-4-4h-1'/>"); }
    public static String iconChart() { return svg("<path d='M4 19V5'/><path d='M20 19H4'/><path d='M8 17v-6'/><path d='M12 17v-9'/><path d='M16 17v-3'/>"); }
    public static String iconShield() { return svg("<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z'/>"); }
    public static String iconTrophy() { return svg("<path d='M8 4h8v3a4 4 0 0 1-8 0V4Z'/><path d='M6 7H4a2 2 0 0 0 2 2'/><path d='M18 7h2a2 2 0 0 1-2 2'/><path d='M12 11v4'/><path d='M9 19h6'/><path d='M10 15h4'/>"); }

    public static String iconDesktop() {
        return svg("<rect x='3' y='4' width='18' height='12' rx='2'/>" +
                "<path d='M8 20h8'/>" +
                "<path d='M12 16v4'/>");
    }

    public static String iconMail() {
        return svg("<path d='M4 6h16v12H4z'/>" +
                "<path d='M4 7l8 6 8-6'/>");
    }

    public static String iconLock() {
        return svg("<rect x='6' y='11' width='12' height='10' rx='2'/>" +
                "<path d='M8 11V9a4 4 0 0 1 8 0v2'/>");
    }

    /** Minimal Minecraft block icon. */
    public static String iconMinecraft() {
        return svg(
                "<rect x='4.5' y='7' width='15' height='13' rx='2'/>" +
                        "<path d='M4.5 11h15'/>" +
                        "<path d='M9 7v4'/>" +
                        "<path d='M12 7v4'/>" +
                        "<path d='M15 7v4'/>"
        );
    }

    /** Audit / log icon. */
    public static String iconAudit() {
        return svg(
                "<path d='M8 6h13'/>" +
                        "<path d='M8 12h13'/>" +
                        "<path d='M8 18h13'/>" +
                        "<path d='M3 6h.01'/>" +
                        "<path d='M3 12h.01'/>" +
                        "<path d='M3 18h.01'/>"
        );
    }
}