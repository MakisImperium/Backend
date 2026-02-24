package org.backendbridge.adminui;

import static org.backendbridge.adminui.AdminIcons.*;
import static org.backendbridge.adminui.AdminUiUtil.esc;
import static org.backendbridge.adminui.AdminUiUtil.escAttr;

/**
 * Shared UI fragments.
 */
public final class AdminComponents {

    private AdminComponents() {}

    public record Th(String label, String type) {}

    private static final String MC_BEDROCK_ICON_URL =
            "https://minecraft.wiki/images/Bedrock_Edition_App_Store_icon_2.png";

    private static String minecraftBedrockLogoImg() {
        return """
            <img class="bb-serverLogoImg"
                 alt="Minecraft"
                 src="%s"
                 loading="eager"
                 referrerpolicy="no-referrer">"""
                .formatted(escAttr(MC_BEDROCK_ICON_URL));
    }

    public static String appShellStart(String active, String serverName, Lang lang) {
        String tabPlayers = "players".equalsIgnoreCase(active) ? "active" : "";
        String tabBans = "bans".equalsIgnoreCase(active) ? "active" : "";
        String tabUsers = "users".equalsIgnoreCase(active) ? "active" : "";
        String tabRoles = "roles".equalsIgnoreCase(active) ? "active" : "";
        String tabStats = "stats".equalsIgnoreCase(active) ? "active" : "";
        String tabAudit = "audit".equalsIgnoreCase(active) ? "active" : "";
        String tabAccount = "account".equalsIgnoreCase(active) ? "active" : "";

        String back = switch ((active == null) ? "" : active.toLowerCase()) {
            case "bans" -> "/admin/bans";
            case "users" -> "/admin/users";
            case "roles" -> "/admin/roles";
            case "stats" -> "/admin/stats";
            case "audit" -> "/admin/audit";
            case "account" -> "/admin/account";
            default -> "/admin/players";
        };

        String deOn = (lang == Lang.DE) ? "active" : "";
        String enOn = (lang == Lang.EN) ? "active" : "";

        String tAccount = (lang == Lang.DE) ? "Konto" : "Account";
        String tLogout = (lang == Lang.DE) ? "Abmelden" : "Logout";
        String tStats = (lang == Lang.DE) ? "Server Stats" : "Server Stats";
        String tBans = "Bans";
        String tUsers = (lang == Lang.DE) ? "Benutzer" : "Users";
        String tRoles = "Roles";
        String tAudit = "Audit";

        return """
            <script>
              (function(){
                try{
                  document.body.classList.add('bb-statsTheme');
                  document.documentElement.setAttribute('data-lang', '%s');
                }catch(e){}
              })();
            </script>

            <div class="shell">
              <header class="top bb-reveal">
                <div class="bb-serverBadge">
                  <span class="bb-serverLogo">%s</span>
                  <span class="bb-serverName">%s</span>
                </div>

                <div style="display:flex; gap:10px; align-items:center; flex-wrap:wrap">
                  <div style="display:flex; gap:6px; align-items:center">
                    <a class="btn %s" href="/admin/lang?set=de&back=%s">DE</a>
                    <a class="btn %s" href="/admin/lang?set=en&back=%s">EN</a>
                  </div>

                  <a class="btn" href="/admin/account">%s %s</a>
                  <a class="btn" href="/admin/logout">%s %s</a>
                </div>
              </header>

              <nav class="menu bb-reveal" aria-label="Admin Navigation">
                <a class="tab %s" href="/admin/players">%s <span>%s</span></a>
                <a class="tab %s" href="/admin/stats">%s <span>%s</span></a>
                <a class="tab %s" href="/admin/bans">%s <span>%s</span></a>
                <a class="tab %s" href="/admin/users">%s <span>%s</span></a>
                <a class="tab %s" href="/admin/roles">%s <span>%s</span></a>
                <a class="tab %s" href="/admin/audit">%s <span>%s</span></a>
                <a class="tab %s" href="/admin/account">%s <span>%s</span></a>
              </nav>

              <main class="content">
            """
                .formatted(
                        escAttr(lang.cookieValue()),
                        minecraftBedrockLogoImg(),
                        esc(serverName),

                        deOn, escAttr(back),
                        enOn, escAttr(back),

                        iconUsers(), esc(tAccount),
                        iconDoor(), esc(tLogout),

                        tabPlayers, iconMinecraft(), esc(serverName),
                        tabStats, iconChart(), esc(tStats),
                        tabBans, iconShield(), esc(tBans),
                        tabUsers, iconUsers(), esc(tUsers),
                        tabRoles, iconShield(), esc(tRoles),
                        tabAudit, iconAudit(), esc(tAudit),
                        tabAccount, iconUsers(), esc(tAccount)
                );
    }

    public static String appShellEnd() {
        return """
              </main>
            </div>
            """;
    }

    public static String heroCenter(String title, String subtitle) {
        return """
            <section class="hero bb-reveal">
              <h1><span>%s</span></h1>
              <div class="bb-heroSub">%s</div>
            </section>
            """.formatted(esc(title), esc(subtitle));
    }

    public static String tableStart(String title, String headRightHtml, Th... headers) {
        StringBuilder th = new StringBuilder();
        for (Th h : headers) th.append("<th>").append(esc(h.label())).append("</th>");

        return """
            <div class="card bb-reveal bb-tableCard">
              <div class="cardHead">
                <div><b>%s</b></div>
                <div style="display:flex; gap:10px; align-items:center; flex-wrap:wrap">
                  %s
                  <div class="bb-tableTools" data-bb-tabletools></div>
                </div>
              </div>
              <div class="pad" style="padding:0">
                <table>
                  <thead><tr>%s</tr></thead>
                  <tbody>
            """.formatted(esc(title), headRightHtml == null ? "" : headRightHtml, th);
    }

    public static String tableEnd() {
        return """
                  </tbody>
                </table>
              </div>
            </div>
            """;
    }

    public static String moderationForm(String xuid, boolean hasActiveBan, Lang lang) {
        String x = escAttr(xuid);

        String tReason = (lang == Lang.DE) ? "Grund" : "Reason";
        String tReasonPh = (lang == Lang.DE) ? "Grund..." : "Reason...";
        String tDuration = (lang == Lang.DE) ? "Dauer Stunden (optional)" : "Duration hours (optional)";
        String tExample = (lang == Lang.DE) ? "z.B. 24" : "e.g. 24";
        String tBan = "Ban";
        String tUnban = "Unban";

        if (!hasActiveBan) {
            return """
                <form method="post" action="/admin/player/ban" class="form" style="max-width:520px;">
                  <input type="hidden" name="xuid" value="%s">
                  <div class="label">%s</div>
                  <input class="inp" type="text" name="reason" placeholder="%s" required>
                  <div class="label">%s</div>
                  <input class="inp" type="number" name="hours" min="1" max="8760" placeholder="%s">
                  <div style="display:flex; justify-content:flex-end;">
                    <button class="btn danger" type="submit">%s</button>
                  </div>
                </form>
                """.formatted(x, esc(tReason), escAttr(tReasonPh), esc(tDuration), escAttr(tExample), esc(tBan));
        }

        return """
            <form method="post" action="/admin/player/unban" class="form" style="max-width:520px;">
              <input type="hidden" name="xuid" value="%s">
              <div style="display:flex; justify-content:flex-end;">
                <button class="btn primary" type="submit">%s</button>
              </div>
            </form>
            """.formatted(x, esc(tUnban));
    }
}