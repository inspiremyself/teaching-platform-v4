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

/**
 * JWT 签发与解析服务。
 *
 * <p>载荷只保留身份定位所需的最小信息：{@code sub}（用户名，仅供日志追踪）、{@code uid}（用户 ID）、
 * {@code sid}（登录会话 key）、{@code iat} / {@code exp}。角色、学校、显示名等身份字段一律不写入 token，
 * 由 {@link JwtAuthenticationFilter} 在每次请求时从数据库读取，避免 token 声明成为可篡改的身份真相源。</p>
 *
 * <p>密钥启动校验：密钥为空或 UTF-8 字节长度不足 32 时直接抛出 {@link IllegalStateException}，
 * 让配置错误在启动阶段暴露，杜绝仓库内默认可用的生产密钥。</p>
 */
@Service
public class JwtTokenService {

    /** HMAC-SHA256 要求的最小密钥字节数。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    /**
     * @param secret 通过 {@code app.jwt-secret}（通常由环境变量 {@code JWT_SECRET} 注入）提供的密钥原文
     * @throws IllegalStateException 密钥为空白或 UTF-8 字节长度不足 32 时
     */
    public JwtTokenService(@Value("${app.jwt-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt-secret 未配置，请通过环境变量 JWT_SECRET 注入");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt-secret 长度不足，UTF-8 字节长度需不少于 32");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 以当前时刻签发 token。
     *
     * @param user       用户实体（只取用户名与 ID）
     * @param sessionKey 登录会话 key，写入 {@code sid}
     * @return 紧凑格式 JWT
     */
    public String issueToken(SysUser user, String sessionKey) {
        return issueToken(user, sessionKey, Instant.now());
    }

    /**
     * 以指定签发时刻签发 token，便于测试构造已过期/固定时间场景。
     *
     * @param user       用户实体
     * @param sessionKey 登录会话 key
     * @param issuedAt   签发时刻
     * @return 紧凑格式 JWT
     */
    public String issueToken(SysUser user, String sessionKey, Instant issuedAt) {
        Instant expiresAt = calculateExpiration(issuedAt);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("sid", sessionKey)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 计算 token 过期时刻（签发后 1 天）。
     *
     * @param issuedAt 签发时刻
     * @return 过期时刻
     */
    public Instant calculateExpiration(Instant issuedAt) {
        return issuedAt.plus(1, ChronoUnit.DAYS);
    }

    /**
     * 计算 token 过期时刻并转为 UTC 偏移时间，供会话表落库。
     *
     * @param issuedAt 签发时刻
     * @return UTC 偏移时间
     */
    public OffsetDateTime calculateExpirationAt(Instant issuedAt) {
        return OffsetDateTime.ofInstant(calculateExpiration(issuedAt), ZoneOffset.UTC);
    }

    /**
     * 校验签名与有效期并返回载荷。
     *
     * @param token 紧凑格式 JWT
     * @return 载荷 claims
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}
