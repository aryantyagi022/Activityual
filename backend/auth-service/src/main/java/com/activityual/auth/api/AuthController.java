package com.activityual.auth.api;

import com.activityual.auth.domain.*;
import com.activityual.common.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final RefreshTokenRepository refreshes;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public record RegisterReq(@Email String email, @NotBlank @Size(min = 8) String password, @NotBlank String displayName) {}
    public record LoginReq(@Email String email, @NotBlank String password) {}
    public record RefreshReq(@NotBlank String refreshToken) {}
    public record TokenRes(String accessToken, String refreshToken, UUID userId, String email, String displayName) {}

    @PostMapping("/register")
    public ResponseEntity<TokenRes> register(@Valid @RequestBody RegisterReq req) {
        if (users.findByEmail(req.email()).isPresent())
            throw new IllegalArgumentException("Email already registered");
        var user = users.save(UserAccount.builder()
                .email(req.email())
                .passwordHash(encoder.encode(req.password()))
                .displayName(req.displayName())
                .createdAt(Instant.now())
                .build());
        return ResponseEntity.ok(issueTokens(user));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(@Valid @RequestBody LoginReq req) {
        var user = users.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid credentials");
        return ResponseEntity.ok(issueTokens(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(@Valid @RequestBody RefreshReq req) {
        String hash = sha256(req.refreshToken());
        var token = refreshes.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("Refresh token expired or revoked");
        var user = users.findById(token.getUserId()).orElseThrow();
        token.setRevoked(true);
        refreshes.save(token);
        return ResponseEntity.ok(issueTokens(user));
    }

    @GetMapping("/me")
    public UserAccount me(@RequestHeader("X-User-Id") UUID userId) {
        return users.findById(userId).orElseThrow();
    }

    private TokenRes issueTokens(UserAccount u) {
        String access = jwt.issueAccess(u.getId(), u.getEmail());
        String refresh = jwt.issueRefresh(u.getId(), u.getEmail());
        refreshes.save(RefreshToken.builder()
                .userId(u.getId())
                .tokenHash(sha256(refresh))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());
        return new TokenRes(access, refresh, u.getId(), u.getEmail(), u.getDisplayName());
    }

    private static String sha256(String in) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(in.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

