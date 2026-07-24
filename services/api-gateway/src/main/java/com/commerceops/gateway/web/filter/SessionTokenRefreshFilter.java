package com.commerceops.gateway.web.filter;

import com.commerceops.gateway.security.AuthSessionTokens;
import com.commerceops.gateway.service.AuthBffService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refreshes the BFF access token near expiry after Spring Session loads the session
 * and before Spring Security resolves the bearer token from it.
 */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER + 10)
@ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "oidc")
public class SessionTokenRefreshFilter extends OncePerRequestFilter {

    private final AuthBffService authBffService;

    public SessionTokenRefreshFilter(AuthBffService authBffService) {
        this.authBffService = authBffService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthSessionTokens.SESSION_ATTR) != null) {
            authBffService.refreshIfNeeded(session);
        }
        chain.doFilter(request, response);
    }
}
