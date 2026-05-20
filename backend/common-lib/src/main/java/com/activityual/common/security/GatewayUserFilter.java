package com.activityual.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class GatewayUserFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_EMAIL   = "X-User-Email";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uid = req.getHeader(HEADER_USER_ID);
        String email = req.getHeader(HEADER_EMAIL);
        if (uid != null && !uid.isBlank()) {
            try {
                CurrentUser.set(new CurrentUser.User(UUID.fromString(uid), email));
            } catch (IllegalArgumentException ignored) { }
        }
        try {
            chain.doFilter(req, res);
        } finally {
            CurrentUser.clear();
        }
    }
}

