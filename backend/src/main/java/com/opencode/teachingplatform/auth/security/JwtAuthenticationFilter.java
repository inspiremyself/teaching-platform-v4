package com.opencode.teachingplatform.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int SESSION_KICKED_CODE = 40102;
    private static final String SESSION_KICKED_MESSAGE = "账号已在其他设备登录";

    private final JwtTokenService jwtTokenService;
    private final UserLoginSessionRepository userLoginSessionRepository;
    private final SysUserRepository sysUserRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserLoginSessionRepository userLoginSessionRepository,
            SysUserRepository sysUserRepository,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userLoginSessionRepository = userLoginSessionRepository;
        this.sysUserRepository = sysUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                Claims claims = jwtTokenService.parse(token);
                UserRole role = UserRole.valueOf(claims.get("role", String.class));
                Long userId = ((Number) claims.get("uid")).longValue();
                String sessionKey = claims.get("sid", String.class);
                Object schoolIdClaim = claims.get("schoolId");
                if (schoolIdClaim == null) {
                    writeUnauthorized(response, 40100, "未登录或登录已过期");
                    return;
                }
                Long schoolId = ((Number) schoolIdClaim).longValue();

                UserLoginSession loginSession = userLoginSessionRepository.findBySessionKey(sessionKey).orElse(null);
                if (loginSession == null || loginSession.getUser() == null || !userId.equals(loginSession.getUser().getId())) {
                    writeUnauthorized(response, 40100, "未登录或登录已过期");
                    return;
                }

                if (sysUserRepository.findById(userId)
                        .map(user -> user.getStatus() != UserStatus.ACTIVE)
                        .orElse(true)) {
                    writeUnauthorized(response, 40100, "未登录或登录已过期");
                    return;
                }

                if (loginSession.getStatus() == LoginSessionStatus.KICKED) {
                    writeUnauthorized(response, SESSION_KICKED_CODE, SESSION_KICKED_MESSAGE);
                    return;
                }

                if (loginSession.getStatus() != LoginSessionStatus.ACTIVE) {
                    writeUnauthorized(response, 40100, "未登录或登录已过期");
                    return;
                }

                OffsetDateTime now = OffsetDateTime.now();
                if (loginSession.getExpiresAt().isBefore(now)) {
                    loginSession.setStatus(LoginSessionStatus.EXPIRED);
                    loginSession.setInvalidatedAt(now);
                    userLoginSessionRepository.save(loginSession);
                    writeUnauthorized(response, 40100, "未登录或登录已过期");
                    return;
                }

                CurrentUser currentUser = new CurrentUser(
                        userId,
                        claims.getSubject(),
                        claims.get("displayName", String.class),
                        role,
                        schoolId,
                        sessionKey
                );
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (ExpiredJwtException ex) {
                writeUnauthorized(response, 40100, "未登录或登录已过期");
                return;
            } catch (JwtException | IllegalArgumentException ex) {
                writeUnauthorized(response, 40100, "未登录或登录已过期");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.of(code, message, null)));
    }
}
