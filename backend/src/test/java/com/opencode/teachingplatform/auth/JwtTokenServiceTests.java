package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenService} 密钥校验与 token 载荷收敛测试。
 *
 * <p>覆盖：空白/短密钥启动失败、合规密钥可签发、载荷只含 sub / uid / sid / iat / exp。</p>
 */
class JwtTokenServiceTests {

    /** 32 个 ASCII 字符，恰好满足 HMAC-SHA256 最小密钥长度。 */
    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void blankSecretFailsFast() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new JwtTokenService("   "));

        assertTrue(exception.getMessage().contains("app.jwt-secret"));
    }

    @Test
    void shortSecretFailsFast() {
        String thirtyOneBytes = "0123456789abcdef0123456789abcde";

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new JwtTokenService(thirtyOneBytes));

        assertTrue(exception.getMessage().contains("32"));
    }

    @Test
    void validSecretCreatesService() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET);

        assertTrue(service.issueToken(user(), "session-1").length() > 0);
    }

    @Test
    void tokenCarriesOnlyIdentityAndSessionClaims() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET);

        Claims claims = service.parse(service.issueToken(user(), "session-1"));

        assertEquals(Set.of("sub", "uid", "sid", "iat", "exp"), claims.keySet());
        assertEquals("t9001", claims.getSubject());
        assertEquals(1L, ((Number) claims.get("uid")).longValue());
        assertEquals("session-1", claims.get("sid"));
        assertFalse(claims.containsKey("role"));
        assertFalse(claims.containsKey("schoolId"));
        assertFalse(claims.containsKey("displayName"));
    }

    private SysUser user() {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return 1L;
            }
        };
        user.setUsername("t9001");
        user.setDisplayName("演示教师");
        user.setRole(UserRole.TEACHER);
        user.setSchoolId(1L);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
