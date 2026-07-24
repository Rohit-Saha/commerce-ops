package com.commerceops.gateway.security;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * OIDC tokens stored in the Spring Session (Redis). Browser only sees an opaque session cookie.
 */
public class AuthSessionTokens implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SESSION_ATTR = "COMMERCE_OIDC_TOKENS";
    public static final String OAUTH_STATE_ATTR = "COMMERCE_OAUTH_STATE";
    public static final String OAUTH_REGISTRATION_ATTR = "COMMERCE_OAUTH_REGISTRATION";
    public static final String OAUTH_REGISTER_ATTR = "COMMERCE_OAUTH_REGISTER";

    private String registrationId;
    private String accessToken;
    private String refreshToken;
    private String idToken;
    private Instant accessTokenExpiresAt;
    private String preferredUsername;
    private String subject;

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public String getPreferredUsername() {
        return preferredUsername;
    }

    public void setPreferredUsername(String preferredUsername) {
        this.preferredUsername = preferredUsername;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean accessTokenExpiringWithin(long skewSeconds) {
        if (accessTokenExpiresAt == null) {
            return true;
        }
        return Instant.now().plusSeconds(Math.max(0, skewSeconds)).isAfter(accessTokenExpiresAt);
    }
}
