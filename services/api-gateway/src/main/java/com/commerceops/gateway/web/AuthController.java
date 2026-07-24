package com.commerceops.gateway.web;

import com.commerceops.common.web.ApiMessage;
import com.commerceops.gateway.config.GatewayProperties;
import com.commerceops.gateway.config.OidcSecurityConfig;
import com.commerceops.gateway.config.SecurityProperties;
import com.commerceops.gateway.security.AuditLog;
import com.commerceops.gateway.web.dto.AuthMeResponse;
import com.commerceops.gateway.web.dto.LoginRequest;
import com.commerceops.gateway.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GatewayProperties.Admin admin;
    private final SecurityProperties securityProperties;

    public AuthController(GatewayProperties properties, SecurityProperties securityProperties) {
        this.admin = properties.admin();
        this.securityProperties = securityProperties;
    }

    @ApiMessage("Signed in successfully")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        if (securityProperties.oidc()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Password login is disabled in OIDC mode; use GET /api/auth/login?client=...");
        }
        if (!constantTimeEquals(admin.username(), request.username())
                || !constantTimeEquals(admin.password(), request.password())) {
            AuditLog.authFailure("admin-login", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        AuditLog.authSuccess("admin-login", request.username());
        return new LoginResponse(admin.username(), admin.apiKey());
    }

    /**
     * Confirms the caller is authenticated. Legacy: API key filter already ran.
     * OIDC: returns preferred_username from the JWT.
     */
    @GetMapping("/me")
    public AuthMeResponse me() {
        if (securityProperties.oidc()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String name = jwt.getClaimAsString("preferred_username");
                if (name == null || name.isBlank()) {
                    name = jwt.getSubject();
                }
                return new AuthMeResponse(name);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return new AuthMeResponse(admin.username());
    }

    /**
     * Sets an httpOnly cookie with the current access token so EventSource/SSE can authenticate
     * without an Authorization header (OIDC mode).
     */
    @PostMapping("/sse-cookie")
    public void sseCookie(HttpServletRequest request, HttpServletResponse response) {
        if (!securityProperties.oidc()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SSE cookie is only used in OIDC mode");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String token = jwtAuth.getToken().getTokenValue();
        ResponseCookie cookie = ResponseCookie.from(OidcSecurityConfig.SSE_COOKIE, token)
                .httpOnly(true)
                .secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                .path("/api/stream")
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(15))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
