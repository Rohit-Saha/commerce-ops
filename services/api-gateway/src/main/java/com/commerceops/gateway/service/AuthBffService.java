package com.commerceops.gateway.service;

import com.commerceops.gateway.config.BffProperties;
import com.commerceops.gateway.security.AuthSessionTokens;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "oidc")
public class AuthBffService {

    private static final Logger log = LoggerFactory.getLogger(AuthBffService.class);

    public static final String CLIENT_ADMIN = "admin-ui";
    public static final String CLIENT_STOREFRONT = "storefront";
    public static final String REG_ADMIN = "admin-ui-bff";
    public static final String REG_STOREFRONT = "storefront-bff";

    private final ClientRegistrationRepository clientRegistrations;
    private final JwtDecoder jwtDecoder;
    private final BffProperties bffProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AuthBffService(
            ClientRegistrationRepository clientRegistrations,
            JwtDecoder jwtDecoder,
            BffProperties bffProperties,
            ObjectMapper objectMapper) {
        this.clientRegistrations = clientRegistrations;
        this.jwtDecoder = jwtDecoder;
        this.bffProperties = bffProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String registrationIdForClient(String client) {
        if (CLIENT_ADMIN.equalsIgnoreCase(client) || REG_ADMIN.equalsIgnoreCase(client)) {
            return REG_ADMIN;
        }
        if (CLIENT_STOREFRONT.equalsIgnoreCase(client) || REG_STOREFRONT.equalsIgnoreCase(client)) {
            return REG_STOREFRONT;
        }
        throw new IllegalArgumentException("Unknown auth client: " + client);
    }

    public String frontendUrlForRegistration(String registrationId) {
        if (REG_ADMIN.equals(registrationId)) {
            return trimTrailingSlash(bffProperties.adminFrontendUrl());
        }
        if (REG_STOREFRONT.equals(registrationId)) {
            return trimTrailingSlash(bffProperties.storefrontFrontendUrl());
        }
        return trimTrailingSlash(bffProperties.adminFrontendUrl());
    }

    public String buildAuthorizationRedirect(HttpServletRequest request, String client, boolean register) {
        String registrationId = registrationIdForClient(client);
        ClientRegistration registration = requireRegistration(registrationId);
        String state = UUID.randomUUID().toString();
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthSessionTokens.OAUTH_STATE_ATTR, state);
        session.setAttribute(AuthSessionTokens.OAUTH_REGISTRATION_ATTR, registrationId);
        session.setAttribute(AuthSessionTokens.OAUTH_REGISTER_ATTR, register);

        String redirectUri = resolveRedirectUri(request, registration);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(registration.getProviderDetails().getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", registration.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", String.join(" ", registration.getScopes()))
                .queryParam("state", state);
        if (register) {
            builder.queryParam("kc_action", "register");
        }
        return builder.build(true).toUriString();
    }

    public void handleCallback(HttpServletRequest request, String registrationId, String code, String state) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("Missing session for OAuth callback");
        }
        String expectedState = (String) session.getAttribute(AuthSessionTokens.OAUTH_STATE_ATTR);
        String expectedReg = (String) session.getAttribute(AuthSessionTokens.OAUTH_REGISTRATION_ATTR);
        session.removeAttribute(AuthSessionTokens.OAUTH_STATE_ATTR);
        session.removeAttribute(AuthSessionTokens.OAUTH_REGISTRATION_ATTR);
        session.removeAttribute(AuthSessionTokens.OAUTH_REGISTER_ATTR);

        if (!StringUtils.hasText(expectedState) || !expectedState.equals(state)) {
            throw new IllegalStateException("Invalid OAuth state");
        }
        if (!registrationId.equals(expectedReg)) {
            throw new IllegalStateException("OAuth registration mismatch");
        }

        ClientRegistration registration = requireRegistration(registrationId);
        String redirectUri = resolveRedirectUri(request, registration);
        TokenResponse tokens = exchangeAuthorizationCode(registration, code, redirectUri);
        AuthSessionTokens stored = toSessionTokens(registrationId, tokens);
        session.setAttribute(AuthSessionTokens.SESSION_ATTR, stored);
        log.info("BFF session established for registration={} user={}",
                registrationId, stored.getPreferredUsername());
    }

    public AuthSessionTokens tokensFrom(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(AuthSessionTokens.SESSION_ATTR);
        return value instanceof AuthSessionTokens tokens ? tokens : null;
    }

    public AuthSessionTokens refreshIfNeeded(HttpSession session) {
        AuthSessionTokens tokens = tokensFrom(session);
        if (tokens == null || !StringUtils.hasText(tokens.getRefreshToken())) {
            return tokens;
        }
        if (!tokens.accessTokenExpiringWithin(bffProperties.refreshSkewSeconds())) {
            return tokens;
        }
        try {
            ClientRegistration registration = requireRegistration(tokens.getRegistrationId());
            TokenResponse refreshed = refreshAccessToken(registration, tokens.getRefreshToken());
            AuthSessionTokens updated = toSessionTokens(tokens.getRegistrationId(), refreshed);
            if (!StringUtils.hasText(updated.getRefreshToken())) {
                updated.setRefreshToken(tokens.getRefreshToken());
            }
            session.setAttribute(AuthSessionTokens.SESSION_ATTR, updated);
            return updated;
        } catch (Exception ex) {
            log.warn("Failed to refresh BFF access token: {}", ex.getMessage());
            return tokens;
        }
    }

    public String buildKeycloakLogoutUrl(AuthSessionTokens tokens, String registrationId) {
        ClientRegistration registration = requireRegistration(
                registrationId != null ? registrationId : (tokens != null ? tokens.getRegistrationId() : REG_ADMIN));
        String endSession = registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint") != null
                ? String.valueOf(registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint"))
                : deriveEndSessionEndpoint(registration);

        String postLogout = frontendUrlForRegistration(registration.getRegistrationId()) + "/";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endSession)
                .queryParam("client_id", registration.getClientId())
                .queryParam("post_logout_redirect_uri", postLogout);
        if (tokens != null && StringUtils.hasText(tokens.getIdToken())) {
            builder.queryParam("id_token_hint", tokens.getIdToken());
        }
        return builder.build(true).toUriString();
    }

    private AuthSessionTokens toSessionTokens(String registrationId, TokenResponse tokens) {
        AuthSessionTokens stored = new AuthSessionTokens();
        stored.setRegistrationId(registrationId);
        stored.setAccessToken(tokens.access_token());
        stored.setRefreshToken(tokens.refresh_token());
        stored.setIdToken(tokens.id_token());
        if (tokens.expires_in() != null && tokens.expires_in() > 0) {
            stored.setAccessTokenExpiresAt(Instant.now().plusSeconds(tokens.expires_in()));
        }
        if (StringUtils.hasText(tokens.access_token())) {
            try {
                Jwt jwt = jwtDecoder.decode(tokens.access_token());
                stored.setSubject(jwt.getSubject());
                String username = jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME);
                if (!StringUtils.hasText(username)) {
                    username = jwt.getClaimAsString(StandardClaimNames.EMAIL);
                }
                if (!StringUtils.hasText(username)) {
                    username = jwt.getSubject();
                }
                stored.setPreferredUsername(username);
                if (jwt.getExpiresAt() != null) {
                    stored.setAccessTokenExpiresAt(jwt.getExpiresAt());
                }
            } catch (Exception ex) {
                log.debug("Could not decode access token claims: {}", ex.getMessage());
            }
        }
        return stored;
    }

    private TokenResponse exchangeAuthorizationCode(
            ClientRegistration registration, String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        return postToken(registration.getProviderDetails().getTokenUri(), form);
    }

    private TokenResponse refreshAccessToken(ClientRegistration registration, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        return postToken(registration.getProviderDetails().getTokenUri(), form);
    }

    private TokenResponse postToken(String tokenUri, MultiValueMap<String, String> form) {
        String body = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readValue(body, TokenResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse token response", ex);
        }
    }

    private ClientRegistration requireRegistration(String registrationId) {
        ClientRegistration registration = clientRegistrations.findByRegistrationId(registrationId);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown client registration: " + registrationId);
        }
        return registration;
    }

    private String resolveRedirectUri(HttpServletRequest request, ClientRegistration registration) {
        String template = registration.getRedirectUri();
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (isDefaultPort(request) ? "" : ":" + request.getServerPort());
        String basePath = request.getContextPath() == null ? "" : request.getContextPath();
        return template
                .replace("{baseUrl}", baseUrl + basePath)
                .replace("{registrationId}", registration.getRegistrationId())
                .replace("{baseScheme}", request.getScheme())
                .replace("{baseHost}", request.getServerName())
                .replace("{basePort}", String.valueOf(request.getServerPort()))
                .replace("{basePath}", basePath);
    }

    private static boolean isDefaultPort(HttpServletRequest request) {
        int port = request.getServerPort();
        String scheme = request.getScheme();
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static String deriveEndSessionEndpoint(ClientRegistration registration) {
        String issuer = registration.getProviderDetails().getIssuerUri();
        if (StringUtils.hasText(issuer)) {
            return trimTrailingSlash(issuer) + "/protocol/openid-connect/logout";
        }
        throw new IllegalStateException("No end_session_endpoint for " + registration.getRegistrationId());
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            String access_token,
            String refresh_token,
            String id_token,
            String token_type,
            Long expires_in,
            String scope
    ) {
    }

    /** Encodes a query value (kept for tests / callers that build URLs manually). */
    public static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public Map<String, String> clientHints() {
        return Map.of(
                CLIENT_ADMIN, REG_ADMIN,
                CLIENT_STOREFRONT, REG_STOREFRONT);
    }
}
