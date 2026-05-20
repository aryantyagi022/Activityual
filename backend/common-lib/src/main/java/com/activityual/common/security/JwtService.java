package com.activityual.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_EMAIL   = "email";
    public static final String CLAIM_TYPE    = "typ";

    private final SecretKey key;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public JwtService(String secret, long accessTtlMinutes, long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String issueAccess(UUID userId, String email) {
        return build(userId, email, "access", Instant.now().plus(accessTtlMinutes, ChronoUnit.MINUTES));
    }

    public String issueRefresh(UUID userId, String email) {
        return build(userId, email, "refresh", Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
    }

    private String build(UUID userId, String email, String type, Instant exp) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(CLAIM_USER_ID, userId.toString(), CLAIM_EMAIL, email, CLAIM_TYPE, type))
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}

