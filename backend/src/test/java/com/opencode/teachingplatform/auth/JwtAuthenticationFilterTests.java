package com.opencode.teachingplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthenticationFilter} 单元测试。
 *
 * <p>过滤器只信任 token 中的 {@code uid} / {@code sid}，角色、学校、显示名、用户状态全部来自数据库会话关联的
 * {@link SysUser}。本测试用真实 {@link JwtTokenService} 签发 token，用 Mockito 模拟会话仓储。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private UserLoginSessionRepository userLoginSessionRepository;

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET);

    private JwtAuthenticationFilter filter;

    @org.junit.jupiter.api.BeforeEach
    void setUpFilter() {
        // @Mock 字段由 Mockito 扩展在实例构造之后注入，过滤器必须在 @BeforeEach 中构造才能拿到真实 mock
        filter = new JwtAuthenticationFilter(jwtTokenService, userLoginSessionRepository, new ObjectMapper());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleClaimInOldTokenIsIgnored() throws Exception {
        SysUser teacher = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.ACTIVE);
        UserLoginSession session = activeSession(teacher);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(session));

        String tamperedToken = token(builder -> builder
                .subject("t9001")
                .claim("uid", 1L)
                .claim("sid", "session-1")
                .claim("role", UserRole.SCHOOL_ADMIN.name()));
        MockHttpServletResponse response = invokeFilter(tamperedToken);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        CurrentUser principal = (CurrentUser) authentication.getPrincipal();
        assertEquals(UserRole.TEACHER, principal.role());
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertEquals(List.of("ROLE_TEACHER"), authorities);
    }

    @Test
    void currentUserFieldsComeFromDatabase() throws Exception {
        SysUser teacher = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.ACTIVE);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(activeSession(teacher)));

        String tokenWithDifferentSubject = token(builder -> builder
                .subject("tampered-sub")
                .claim("uid", 1L)
                .claim("sid", "session-1")
                .claim("displayName", "伪造姓名")
                .claim("schoolId", 99L));
        invokeFilter(tokenWithDifferentSubject);

        CurrentUser principal = (CurrentUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(1L, principal.id());
        assertEquals("t9001", principal.username());
        assertEquals("演示教师", principal.displayName());
        assertEquals(1L, principal.schoolId());
        assertEquals("session-1", principal.sessionKey());
    }

    @Test
    void disabledUserReturnsUnauthorized() throws Exception {
        SysUser disabled = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.DISABLED);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(activeSession(disabled)));

        MockHttpServletResponse response = invokeFilter(standardToken(1L, "session-1"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40100"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void kickedSessionReturnsSpecificUnauthorizedCode() throws Exception {
        SysUser teacher = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.ACTIVE);
        UserLoginSession session = activeSession(teacher);
        session.setStatus(LoginSessionStatus.KICKED);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(session));

        MockHttpServletResponse response = invokeFilter(standardToken(1L, "session-1"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40102"));
    }

    @Test
    void loggedOutSessionReturnsUnauthorizedWithoutSaving() throws Exception {
        SysUser teacher = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.ACTIVE);
        UserLoginSession session = activeSession(teacher);
        session.setStatus(LoginSessionStatus.LOGGED_OUT);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(session));

        MockHttpServletResponse response = invokeFilter(standardToken(1L, "session-1"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40100"));
        verify(userLoginSessionRepository, never()).save(any());
    }

    @Test
    void expiredStatusSessionReturnsUnauthorizedWithoutSaving() throws Exception {
        SysUser teacher = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.ACTIVE);
        UserLoginSession session = activeSession(teacher);
        // 会话已是 EXPIRED 状态（非“到期后首次访问”），不应再次写库，直接按 40100 拒绝
        session.setStatus(LoginSessionStatus.EXPIRED);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(session));

        MockHttpServletResponse response = invokeFilter(standardToken(1L, "session-1"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40100"));
        verify(userLoginSessionRepository, never()).save(any());
    }

    @Test
    void expiredSessionIsMarkedExpired() throws Exception {
        SysUser teacher = user(1L, "t9001", "演示教师", UserRole.TEACHER, UserStatus.ACTIVE);
        UserLoginSession session = activeSession(teacher);
        session.setExpiresAt(OffsetDateTime.now().minusMinutes(5));
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(session));

        MockHttpServletResponse response = invokeFilter(standardToken(1L, "session-1"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40100"));
        assertEquals(LoginSessionStatus.EXPIRED, session.getStatus());
        assertNotNull(session.getInvalidatedAt());
        verify(userLoginSessionRepository).save(session);
    }

    @Test
    void missingUidOrSidReturnsUnauthorizedWithoutRepositoryCall() throws Exception {
        MockHttpServletResponse missingUid = invokeFilter(token(builder -> builder
                .subject("t9001")
                .claim("sid", "session-1")));
        MockHttpServletResponse missingSid = invokeFilter(token(builder -> builder
                .subject("t9001")
                .claim("uid", 1L)));

        assertEquals(401, missingUid.getStatus());
        assertTrue(missingUid.getContentAsString().contains("40100"));
        assertEquals(401, missingSid.getStatus());
        assertTrue(missingSid.getContentAsString().contains("40100"));
        verifyNoInteractions(userLoginSessionRepository);
    }

    @Test
    void sessionUserMismatchReturnsUnauthorized() throws Exception {
        SysUser otherUser = user(2L, "other", "其他教师", UserRole.TEACHER, UserStatus.ACTIVE);
        when(userLoginSessionRepository.findBySessionKey("session-1")).thenReturn(Optional.of(activeSession(otherUser)));

        MockHttpServletResponse response = invokeFilter(standardToken(1L, "session-1"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40100"));
    }

    /**
     * 调用过滤器；成功路径应放行到后续 filter chain。
     */
    private MockHttpServletResponse invokeFilter(String rawToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + rawToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        if (response.getStatus() == 200) {
            verify(filterChain).doFilter(request, response);
        } else {
            verifyNoInteractions(filterChain);
        }
        return response;
    }

    private String standardToken(Long userId, String sessionKey) {
        return token(builder -> builder
                .subject("t9001")
                .claim("uid", userId)
                .claim("sid", sessionKey));
    }

    private String token(java.util.function.Consumer<io.jsonwebtoken.JwtBuilder> customizer) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key);
        customizer.accept(builder);
        return builder.compact();
    }

    private SysUser user(Long id, String username, String displayName, UserRole role, UserStatus status) {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return id;
            }
        };
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setSchoolId(1L);
        user.setStatus(status);
        return user;
    }

    private UserLoginSession activeSession(SysUser user) {
        UserLoginSession loginSession = new UserLoginSession();
        loginSession.setUser(user);
        loginSession.setSessionKey("session-1");
        loginSession.setStatus(LoginSessionStatus.ACTIVE);
        loginSession.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return loginSession;
    }
}
