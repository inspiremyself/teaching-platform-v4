package com.opencode.teachingplatform.auth.security;

import com.opencode.teachingplatform.auth.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;

    public JwtTokenService(@Value("${app.jwt-secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(SysUser user, String sessionKey) {
        return issueToken(user, sessionKey, Instant.now());
    }

    public String issueToken(SysUser user) {
        return issueToken(user, "legacy-session-" + user.getId(), Instant.now());
    }

    public String issueToken(SysUser user, String sessionKey, Instant issuedAt) {
        Instant expiresAt = calculateExpiration(issuedAt);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("displayName", user.getDisplayName())
                .claim("schoolId", user.getSchoolId())
                .claim("sid", sessionKey)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public Instant calculateExpiration(Instant issuedAt) {
        return issuedAt.plus(1, ChronoUnit.DAYS);
    }

    public OffsetDateTime calculateExpirationAt(Instant issuedAt) {
        return OffsetDateTime.ofInstant(calculateExpiration(issuedAt), ZoneOffset.UTC);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}
