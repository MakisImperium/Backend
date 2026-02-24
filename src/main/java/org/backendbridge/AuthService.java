package org.backendbridge;

import com.sun.net.httpserver.HttpExchange;

/**
 * Auth for server API requests.
 *
 * If enabled: require Authorization: Bearer <token>.
 */
public final class AuthService {

    private final boolean enabled;
    private final String token;

    public AuthService(AppConfig.ServerAuthCfg cfg) {
        this.enabled = cfg.enabled();
        this.token = cfg.token() == null ? "" : cfg.token().trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAuthorized(HttpExchange ex) {
        if (!enabled) return true;
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null) return false;
        String prefix = "Bearer ";
        if (!h.startsWith(prefix)) return false;
        return token.equals(h.substring(prefix.length()).trim());
    }
}