package com.commerceops.gateway.web;

import com.commerceops.gateway.security.AuthSessionTokens;
import com.commerceops.gateway.security.AuditLog;
import com.commerceops.gateway.service.AuthBffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * OIDC BFF endpoints: browser talks only to the gateway; Keycloak redirects stay server-side.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "oidc")
public class AuthBffController {

    private final AuthBffService authBffService;

    public AuthBffController(AuthBffService authBffService) {
        this.authBffService = authBffService;
    }

    /**
     * Starts Authorization Code flow. {@code client} is {@code admin-ui} or {@code storefront}.
     * Optional {@code register=true} opens Keycloak registration (storefront).
     */
    @GetMapping("/login")
    public void login(
            @RequestParam("client") String client,
            @RequestParam(value = "register", defaultValue = "false") boolean register,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            String redirect = authBffService.buildAuthorizationRedirect(request, client, register);
            response.sendRedirect(redirect);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/callback/{registrationId}")
    public void callback(
            @PathVariable String registrationId,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (StringUtils.hasText(error)) {
            AuditLog.authFailure("bff-callback", error + ": " + errorDescription);
            String frontend = authBffService.frontendUrlForRegistration(registrationId);
            response.sendRedirect(frontend + "/?auth=error");
            return;
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing code or state");
        }
        try {
            authBffService.handleCallback(request, registrationId, code, state);
            AuthSessionTokens tokens = authBffService.tokensFrom(request.getSession(false));
            String username = tokens != null ? tokens.getPreferredUsername() : "unknown";
            AuditLog.authSuccess("bff-login", username);
            response.sendRedirect(authBffService.frontendUrlForRegistration(registrationId) + "/");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            AuditLog.authFailure("bff-callback", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    /** Clears the Redis-backed session cookie (SPA logout). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            AuthSessionTokens tokens = authBffService.tokensFrom(session);
            String user = tokens != null ? tokens.getPreferredUsername() : null;
            session.invalidate();
            AuditLog.authSuccess("bff-logout", user != null ? user : "anonymous");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Full SSO logout: invalidate session then redirect to Keycloak end_session, then frontend.
     */
    @GetMapping("/logout")
    public void logoutRedirect(
            @RequestParam(value = "client", defaultValue = "admin-ui") String client,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String registrationId;
        try {
            registrationId = authBffService.registrationIdForClient(client);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        HttpSession session = request.getSession(false);
        AuthSessionTokens tokens = null;
        if (session != null) {
            tokens = authBffService.tokensFrom(session);
            session.invalidate();
        }
        response.sendRedirect(authBffService.buildKeycloakLogoutUrl(tokens, registrationId));
    }
}
