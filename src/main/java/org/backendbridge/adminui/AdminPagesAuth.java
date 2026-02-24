package org.backendbridge.adminui;

import org.backendbridge.repo.AdminRepository;

import java.util.List;
import java.util.Map;

import static org.backendbridge.adminui.AdminComponents.*;
import static org.backendbridge.adminui.AdminIcons.*;
import static org.backendbridge.adminui.AdminPageChrome.pageEndWithAppScript;
import static org.backendbridge.adminui.AdminPageChrome.pageStart;
import static org.backendbridge.adminui.AdminUiUtil.*;

/**
 * Admin UI pages (SSR) – auth + user/role management + audit.
 *
 * Timestamp formatting:
 * - Always render ISO into data-iso attributes (and data-sort for tables)
 * - AdminPageChrome's global formatter applies Lang-based TZ + locale format.
 */
final class AdminPagesAuth {

    private AdminPagesAuth() {}

    // ---------------- Login ----------------

    static String login(String serverName, Lang lang, String err) {
        String tLoginFailedTitle = (lang == Lang.DE) ? "Login fehlgeschlagen" : "Login failed";
        String tLoginFailedBody = (lang == Lang.DE) ? "Username oder Passwort ist falsch." : "Username or password is wrong.";
        String tAdminLogin = "Admin Login";
        String tUsername = "Username";
        String tPassword = (lang == Lang.DE) ? "Passwort" : "Password";
        String tButton = "LOGIN";

        String errorBox = "";
        if ("bad_credentials".equalsIgnoreCase(err)) {
            errorBox = """
                <div class="alert">
                  <b>%s</b>
                  <div style="opacity:.75">%s</div>
                </div>
                """.formatted(esc(tLoginFailedTitle), esc(tLoginFailedBody));
        }

        String deOn = (lang == Lang.DE) ? "active" : "";
        String enOn = (lang == Lang.EN) ? "active" : "";

        StringBuilder html = new StringBuilder(40_000);
        html.append(pageStart("Login • " + esc(serverName)));

        html.append("""
            <div class="bb-loginWrap">
              <section class="bb-loginCard bb-reveal">
                <div class="bb-loginLeft">
                  <div class="bb-illus" aria-hidden="true">
                    <div class="bb-illusIcon">
                      %s
                    </div>
                  </div>
                </div>

                <div class="bb-loginRight">
                  <div style="display:flex; justify-content:flex-end; gap:6px; margin-bottom:10px">
                    <a class="btn %s" href="/admin/lang?set=de&back=/admin/login">DE</a>
                    <a class="btn %s" href="/admin/lang?set=en&back=/admin/login">EN</a>
                  </div>

                  <div class="bb-loginTitle">%s</div>
                  %s

                  <form method="post" action="/admin/login" class="form" style="gap:12px">
                    <label class="bb-floatField">
                      <span class="bb-inputIcon">%s</span>
                      <input name="username" placeholder=" " autocomplete="username" required>
                      <span class="bb-floatLabel">%s</span>
                    </label>

                    <label class="bb-floatField">
                      <span class="bb-inputIcon">%s</span>
                      <input type="password" name="password" placeholder=" " autocomplete="current-password" required>
                      <span class="bb-floatLabel">%s</span>
                    </label>

                    <button class="bb-loginBtn" type="submit">%s</button>
                  </form>
                </div>
              </section>
            </div>
            """.formatted(
                iconDesktop(),
                deOn, enOn,
                esc(tAdminLogin),
                errorBox,
                iconMail(), esc(tUsername),
                iconLock(), esc(tPassword),
                esc(tButton)
        ));

        html.append(pageEndWithAppScript());
        return html.toString();
    }

    static String messageBox(Lang lang, String ok, String err) {
        String tOk = "OK";
        String tError = (lang == Lang.DE) ? "Fehler" : "Error";

        if (ok != null && !ok.isBlank()) {
            return """
                <div class="alert"><b>%s</b><div style="opacity:.8">%s</div></div>
                """.formatted(esc(tOk), esc(ok));
        }
        if (err != null && !err.isBlank()) {
            return """
                <div class="alert"><b>%s</b><div style="opacity:.8">%s</div></div>
                """.formatted(esc(tError), esc(err));
        }
        return "";
    }

    // ---------------- Account ----------------

    static String account(String serverName, Lang lang, String msgHtml, String username) {
        String tTitle = (lang == Lang.DE) ? "Konto" : "Account";
        String tSubtitle = (lang == Lang.DE) ? "Passwort ändern" : "Change your password";
        String tSignedInAs = (lang == Lang.DE) ? "Angemeldet als" : "Signed in as";
        String tChangePassword = (lang == Lang.DE) ? "Passwort ändern" : "Change password";
        String phCurrent = (lang == Lang.DE) ? "aktuelles Passwort" : "current password";
        String phNew = (lang == Lang.DE) ? "neues Passwort (min 8)" : "new password (min 8)";
        String phRepeat = (lang == Lang.DE) ? "neues Passwort wiederholen" : "repeat new password";
        String tUpdate = (lang == Lang.DE) ? "Passwort aktualisieren" : "Update password";

        StringBuilder html = new StringBuilder(80_000);
        html.append(pageStart(tTitle + " • " + esc(serverName)));
        html.append(appShellStart("account", serverName, lang));
        html.append(heroCenter(tTitle, tSubtitle));

        html.append("""
            <section class="card bb-reveal">
              <div class="cardHead"><div><b>%s</b></div></div>
              <div class="pad mono">%s</div>
            </section>
            """.formatted(esc(tSignedInAs), esc(username == null ? "" : username)));

        html.append("""
            <section class="card bb-reveal">
              <div class="cardHead"><div><b>%s</b></div></div>
              <div class="pad">
                %s
                <form method="post" action="/admin/account/password" class="form" style="max-width:520px">
                  <input class="inp" type="password" name="currentPassword" placeholder="%s" required>
                  <input class="inp" type="password" name="newPassword" placeholder="%s" required>
                  <input class="inp" type="password" name="newPassword2" placeholder="%s" required>
                  <div style="display:flex; justify-content:flex-end">
                    <button class="btn primary" type="submit">%s</button>
                  </div>
                </form>
              </div>
            </section>
            """.formatted(
                esc(tChangePassword),
                msgHtml == null ? "" : msgHtml,
                escAttr(phCurrent),
                escAttr(phNew),
                escAttr(phRepeat),
                esc(tUpdate)
        ));

        html.append(appShellEnd());
        html.append(pageEndWithAppScript());
        return html.toString();
    }

    // ---------------- Users ----------------

    /**
     * Users page with non-root user dropdown for role changes and password reset.
     */
    static String users(
            String serverName,
            Lang lang,
            String msgHtml,
            String roleOptionsHtml,
            String userOptionsHtmlNonRoot,
            String userRowsHtml
    ) {
        String tTitle = (lang == Lang.DE) ? "Benutzer" : "Users";
        String tSubtitle = (lang == Lang.DE) ? "Web-Accounts und Rollen" : "Created web accounts and their roles";
        String tNewUser = (lang == Lang.DE) ? "Neuer Benutzer" : "New user";
        String tCreate = (lang == Lang.DE) ? "Erstellen" : "Create";
        String tUpdateRoleReset = (lang == Lang.DE) ? "Rolle ändern / Passwort zurücksetzen" : "Update role / reset password";
        String tSetRole = (lang == Lang.DE) ? "Rolle setzen" : "Set role";
        String tResetPassword = (lang == Lang.DE) ? "Passwort zurücksetzen" : "Reset password";
        String tWebUsers = (lang == Lang.DE) ? "Web Benutzer" : "Web Users";

        String phPass = (lang == Lang.DE) ? "passwort" : "password";
        String phNewPass = (lang == Lang.DE) ? "neues Passwort" : "new password";

        StringBuilder html = new StringBuilder(190_000);
        html.append(pageStart(tTitle + " • " + esc(serverName)));
        html.append(appShellStart("users", serverName, lang));
        html.append(heroCenter(tTitle, tSubtitle));

        html.append("""
            <section class="card bb-reveal" id="bbNewUser">
              <div class="pad">
                %s
                <div style="display:grid; gap:14px; grid-template-columns:repeat(2,minmax(0,1fr));">
                  <div class="card">
                    <div class="cardHead"><div><b>%s</b></div></div>
                    <div class="pad">
                      <form method="post" action="/admin/users/create" class="form">
                        <input class="inp" name="username" placeholder="username" required>
                        <input class="inp" name="password" type="password" placeholder="%s" required>
                        <select class="inp" name="roleId" required>
                          %s
                        </select>
                        <button class="btn primary" type="submit">%s</button>
                      </form>
                    </div>
                  </div>

                  <div class="card">
                    <div class="cardHead"><div><b>%s</b></div></div>
                    <div class="pad">
                      <form method="post" action="/admin/users/role/set" class="form" style="margin-bottom:12px">
                        <select class="inp" name="username" required>
                          %s
                        </select>
                        <select class="inp" name="roleId" required>
                          %s
                        </select>
                        <button class="btn primary" type="submit">%s</button>
                        <div class="mono" style="opacity:.75">root is excluded and cannot be modified.</div>
                      </form>

                      <form method="post" action="/admin/users/reset" class="form">
                        <select class="inp" name="username" required>
                          %s
                        </select>
                        <input class="inp" name="password" type="password" placeholder="%s" required>
                        <button class="btn danger" type="submit">%s</button>
                        <div class="mono" style="opacity:.75">root is excluded and cannot be modified.</div>
                      </form>
                    </div>
                  </div>
                </div>
              </div>
            </section>
            """.formatted(
                msgHtml == null ? "" : msgHtml,
                esc(tNewUser),
                escAttr(phPass),
                roleOptionsHtml == null ? "" : roleOptionsHtml,
                esc(tCreate),

                esc(tUpdateRoleReset),
                userOptionsHtmlNonRoot == null ? "" : userOptionsHtmlNonRoot,
                roleOptionsHtml == null ? "" : roleOptionsHtml,
                esc(tSetRole),

                userOptionsHtmlNonRoot == null ? "" : userOptionsHtmlNonRoot,
                escAttr(phNewPass),
                esc(tResetPassword)
        ));

        html.append(tableStart(tWebUsers, "<a class='btn primary' href='#bbNewUser'>" + esc(tNewUser) + "</a>",
                new Th((lang == Lang.DE) ? "Benutzer" : "User", "text"),
                new Th("Role", "text"),
                new Th("Status", "text")
        ));
        html.append(userRowsHtml == null ? "" : userRowsHtml);
        html.append(tableEnd());

        html.append(appShellEnd());
        html.append(pageEndWithAppScript());
        return html.toString();
    }

    // ---------------- Roles + Permissions ----------------

    static String rolesWithPermissions(
            String serverName,
            Lang lang,
            String msgHtml,
            List<AdminRepository.WebRole> roles,
            List<AdminRepository.WebPerm> perms,
            Map<Long, Map<String, Boolean>> rolePerms
    ) {
        String tTitle = "Roles";
        String tSubtitle = (lang == Lang.DE)
                ? "Admin hat standardmäßig alles. Neue Rollen starten leer."
                : "Admin has all by default. New roles start empty.";
        String tCreateRole = (lang == Lang.DE) ? "Rolle erstellen" : "Create role";
        String tRoleKeyPh = (lang == Lang.DE) ? "role key (z.B. moderator)" : "role key (e.g. moderator)";
        String tDisplayNamePh = (lang == Lang.DE) ? "anzeigename (z.B. Moderator)" : "display name (e.g. Moderator)";
        String tMatrix = (lang == Lang.DE) ? "Rechte-Matrix" : "Permissions matrix";
        String tSaved = (lang == Lang.DE) ? "Sofort gespeichert" : "Saved instantly";
        String tPerm = (lang == Lang.DE) ? "Berechtigung" : "Permission";
        String tDesc = (lang == Lang.DE) ? "Beschreibung" : "Description";

        StringBuilder html = new StringBuilder(220_000);
        html.append(pageStart(tTitle + " • " + esc(serverName)));
        html.append(appShellStart("roles", serverName, lang));
        html.append(heroCenter(tTitle, tSubtitle));

        html.append("""
            <section class="card bb-reveal" id="bbNewRole">
              <div class="pad">
                %s
                <form method="post" action="/admin/roles/create" class="form" style="max-width:520px">
                  <input class="inp" name="roleKey" placeholder="%s" required>
                  <input class="inp" name="displayName" placeholder="%s" required>
                  <div style="display:flex; justify-content:flex-end">
                    <button class="btn primary" type="submit">%s</button>
                  </div>
                </form>
              </div>
            </section>
            """.formatted(msgHtml == null ? "" : msgHtml, escAttr(tRoleKeyPh), escAttr(tDisplayNamePh), esc(tCreateRole)));

        html.append("""
            <section class="card bb-reveal">
              <div class="cardHead">
                <div><b>%s</b></div>
                <div class="mono" style="opacity:.75">%s</div>
              </div>
              <div class="pad" style="overflow:auto">
                <table class="bb-permTable">
                  <thead>
                    <tr>
                      <th>%s</th>
                      <th>%s</th>
            """.formatted(esc(tMatrix), esc(tSaved), esc(tPerm), esc(tDesc)));

        if (roles != null) {
            for (var r : roles) {
                html.append("<th class='mono'>").append(esc(r.roleKey())).append("</th>");
            }
        }

        html.append("""
                    </tr>
                  </thead>
                  <tbody>
            """);

        if (perms != null) {
            for (var p : perms) {
                String pk = p.permKey();
                html.append("<tr>")
                        .append("<td class='mono'>").append(esc(pk)).append("</td>")
                        .append("<td>").append(esc(p.description() == null ? "" : p.description())).append("</td>");

                if (roles != null) {
                    for (var r : roles) {
                        boolean enabled = rolePerms != null
                                && rolePerms.get(r.id()) != null
                                && Boolean.TRUE.equals(rolePerms.get(r.id()).get(pk));

                        html.append("""
                            <td style="text-align:center" class="%s">
                              <label class="bb-toggle">
                                <input type="checkbox" data-role-id="%s" data-perm-key="%s" %s>
                                <span class="bb-slider"></span>
                              </label>
                            </td>
                            """.formatted(
                                enabled ? "bb-permOn" : "",
                                escAttr(String.valueOf(r.id())),
                                escAttr(pk),
                                enabled ? "checked" : ""
                        ));
                    }
                }

                html.append("</tr>");
            }
        }

        html.append("""
                  </tbody>
                </table>
              </div>
            </section>
            """);

        html.append(appShellEnd());
        html.append(pageEndWithAppScript());
        return html.toString();
    }

    // ---------------- Audit ----------------

    static String audit(String serverName, Lang lang, String msgHtml, String filtersHtml, String rowsHtml, int resultCount) {
        String tTitle = "Audit";
        String tSubtitle = (lang == Lang.DE) ? "Admin Aktionen (Filter)" : "Admin actions (filterable)";
        String tResults = (lang == Lang.DE) ? "Treffer" : "Results";

        StringBuilder html = new StringBuilder(160_000);
        html.append(pageStart(tTitle + " • " + esc(serverName)));
        html.append(appShellStart("audit", serverName, lang));
        html.append(heroCenter(tTitle, tSubtitle));

        html.append("""
            <section class="card bb-reveal">
              <div class="pad">
                %s
                %s
                <div class="mono" style="opacity:.7; margin-top:10px">%s: %d</div>
              </div>
            </section>
            """.formatted(
                msgHtml == null ? "" : msgHtml,
                filtersHtml == null ? "" : filtersHtml,
                esc(tResults),
                Math.max(0, resultCount)
        ));

        html.append(tableStart(tTitle, null,
                new Th((lang == Lang.DE) ? "Zeit" : "Time", "date"),
                new Th((lang == Lang.DE) ? "Akteur" : "Actor", "text"),
                new Th((lang == Lang.DE) ? "Aktion" : "Action", "text"),
                new Th("Details", "text")
        ));
        html.append(rowsHtml == null ? "" : rowsHtml);
        html.append(tableEnd());

        html.append(appShellEnd());
        html.append(pageEndWithAppScript());
        return html.toString();
    }

    static String auditFilterForm(Lang lang, String actor, String action, String q, String limit) {
        String tActor = "actor";
        String tAction = "action";
        String tQuery = (lang == Lang.DE) ? "suche" : "query";
        String tLimit = "limit";
        String tApply = (lang == Lang.DE) ? "Anwenden" : "Apply";

        String a = actor == null ? "" : actor;
        String ak = action == null ? "" : action;
        String qq = q == null ? "" : q;
        String lim = (limit == null || limit.isBlank()) ? "500" : limit;

        return """
            <form method="get" action="/admin/audit" class="form" style="gap:10px; max-width:900px">
              <div style="display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px">
                <input class="inp" name="actor" placeholder="%s" value="%s">
                <input class="inp" name="action" placeholder="%s" value="%s">
                <input class="inp" name="q" placeholder="%s" value="%s">
                <input class="inp" name="limit" placeholder="%s" value="%s">
              </div>
              <div style="display:flex; justify-content:flex-end">
                <button class="btn primary" type="submit">%s</button>
              </div>
            </form>
            """.formatted(
                escAttr(tActor), escAttr(a),
                escAttr(tAction), escAttr(ak),
                escAttr(tQuery), escAttr(qq),
                escAttr(tLimit), escAttr(lim),
                esc(tApply)
        );
    }

    static String auditRow(String createdAtIso, String actorUsername, String actionKey, String details, long id) {
        String at = createdAtIso == null ? "" : createdAtIso;
        String actor = actorUsername == null ? "" : actorUsername;
        String act = actionKey == null ? "" : actionKey;
        String det = details == null ? "" : details;

        return """
            <tr data-id="%s">
              <td class="mono" data-iso="%s" data-sort="%s"></td>
              <td class="mono">%s</td>
              <td class="mono">%s</td>
              <td>%s</td>
            </tr>
            """.formatted(
                escAttr(String.valueOf(id)),
                escAttr(at),
                escAttr(at),
                esc(actor),
                esc(act),
                esc(det)
        );
    }
}