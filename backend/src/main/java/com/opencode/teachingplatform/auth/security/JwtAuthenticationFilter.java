package com.opencode.teachingplatform.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
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

/**
 * JWT 认证过滤器。
 *
 * <p>token 只作为“身份定位凭据”：从中读取 {@code uid} 与 {@code sid}，随后通过登录会话
 * （{@code user_login_session}）关联的数据库用户取得角色、学校、显示名与状态。
 * token 内的任何身份声明都不参与授权，即使被篡改也会被数据库事实覆盖。</p>
 *
 * <p>校验顺序固定为：解析 claims → uid/sid 缺失 → 会话不存在或用户归属不符 → 用户状态非 ACTIVE
 * → 会话 KICKED（40102）→ 会话非 ACTIVE → 会话过期（写 EXPIRED 后拒绝）→ 构造 CurrentUser。
 * 除 KICKED 外统一返回 40100「未登录或登录已过期」。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int SESSION_KICKED_CODE = 40102;
    private static final String SESSION_KICKED_MESSAGE = "账号已在其他设备登录";
    private static final int UNAUTHORIZED_CODE = 40100;
    private static final String UNAUTHORIZED_MESSAGE = "未登录或登录已过期";

    private final JwtTokenService jwtTokenService;
    private final UserLoginSessionRepository userLoginSessionRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserLoginSessionRepository userLoginSessionRepository,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userLoginSessionRepository = userLoginSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                Claims claims = jwtTokenService.parse(token);

                // 1. 只认 uid 与 sid，其余声明一律忽略
                Object uidClaim = claims.get("uid");
                String sessionKey = claims.get("sid", String.class);
                if (!(uidClaim instanceof Number) || sessionKey == null || sessionKey.isBlank()) {
                    writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                    return;
                }
                Long userId = ((Number) uidClaim).longValue();

                // 2. 会话必须存在，且归属与 token 声明一致
                UserLoginSession loginSession = userLoginSessionRepository.findBySessionKey(sessionKey).orElse(null);
                if (loginSession == null || loginSession.getUser() == null
                        || !userId.equals(loginSession.getUser().getId())) {
                    writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                    return;
                }
                SysUser user = loginSession.getUser();

                // 3. 用户状态以数据库为准
                if (user.getStatus() != UserStatus.ACTIVE) {
                    writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                    return;
                }

                // 4. 会话状态：KICKED 使用专用码，其余非 ACTIVE 统一 40100
                if (loginSession.getStatus() == LoginSessionStatus.KICKED) {
                    writeUnauthorized(response, SESSION_KICKED_CODE, SESSION_KICKED_MESSAGE);
                    return;
                }
                if (loginSession.getStatus() != LoginSessionStatus.ACTIVE) {
                    writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                    return;
                }

                // 5. 会话过期：落库标记 EXPIRED 后拒绝
                OffsetDateTime now = OffsetDateTime.now();
                if (loginSession.getExpiresAt().isBefore(now)) {
                    loginSession.setStatus(LoginSessionStatus.EXPIRED);
                    loginSession.setInvalidatedAt(now);
                    userLoginSessionRepository.save(loginSession);
                    writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                    return;
                }

                // 6. 身份字段全部取自数据库用户，仅会话 key 来自 token
                CurrentUser currentUser = new CurrentUser(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getRole(),
                        user.getSchoolId(),
                        sessionKey
                );
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (ExpiredJwtException ex) {
                writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                return;
            } catch (JwtException | IllegalArgumentException ex) {
                writeUnauthorized(response, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 直接写出统一未授权响应，不经过异常处理器。
     *
     * @param response HTTP 响应
     * @param code     业务错误码（40100 / 40102）
     * @param message  提示文案
     */
    private void writeUnauthorized(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.of(code, message, null)));
    }
}
