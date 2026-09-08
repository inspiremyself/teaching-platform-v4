package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.dto.LoginRequest;
import com.opencode.teachingplatform.auth.dto.UpdateProfileRequest;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.auth.service.AuthService;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.school.repository.SchoolRepository;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private UserLoginSessionRepository userLoginSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void disabledUserCannotLogin() {
        SysUser user = new SysUser();
        user.setUsername("t9001");
        user.setPasswordHash("{noop}123456");
        user.setDisplayName("演示教师");
        user.setRole(UserRole.TEACHER);
        user.setStatus(UserStatus.DISABLED);
        when(sysUserRepository.findByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER)));

        assertEquals(40101, exception.getCode());
        assertEquals("用户名、密码或角色错误", exception.getMessage());
    }

    @Test
    void duplicateLoginRequiresForceConfirmation() {
        SysUser user = activeTeacher();
        UserLoginSession existingSession = new UserLoginSession();
        existingSession.setUser(user);
        existingSession.setStatus(LoginSessionStatus.ACTIVE);
        existingSession.setExpiresAt(OffsetDateTime.now().plusHours(2));

        when(sysUserRepository.findByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "123456")).thenReturn(true);
        when(userLoginSessionRepository.findFirstByUser_IdAndStatusOrderByCreatedAtDesc(1L, LoginSessionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSession));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER))
        );

        assertEquals(40901, exception.getCode());
        assertEquals("该账号已在其他设备登录，继续登录将使旧设备下线", exception.getMessage());
    }

    @Test
    void forceLoginKicksExistingSessionAndIssuesNewToken() {
        SysUser user = activeTeacher();
        UserLoginSession existingSession = new UserLoginSession();
        existingSession.setUser(user);
        existingSession.setStatus(LoginSessionStatus.ACTIVE);
        existingSession.setExpiresAt(OffsetDateTime.now().plusHours(2));
        existingSession.setSessionKey("old-session");

        when(sysUserRepository.findByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "123456")).thenReturn(true);
        when(userLoginSessionRepository.findFirstByUser_IdAndStatusOrderByCreatedAtDesc(1L, LoginSessionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSession));
        when(sysUserRepository.save(user)).thenReturn(user);
        when(userLoginSessionRepository.save(any(UserLoginSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.calculateExpirationAt(any(Instant.class))).thenReturn(OffsetDateTime.now().plusDays(1));
        when(jwtTokenService.issueToken(eq(user), any(String.class), any(Instant.class))).thenReturn("fresh-token");

        Map<String, Object> result = authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER, true));

        assertEquals("fresh-token", result.get("token"));
        assertEquals(LoginSessionStatus.KICKED, existingSession.getStatus());
        assertTrue(existingSession.getInvalidatedAt() != null);
        verify(userLoginSessionRepository, atLeastOnce()).save(any(UserLoginSession.class));
    }

    @Test
    void updateProfileTrimsAndPersistsFields() {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return 1L;
            }
        };
        user.setUsername("t9001");
        user.setDisplayName("演示教师");
        user.setRole(UserRole.TEACHER);
        user.setStatus(UserStatus.ACTIVE);

        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sysUserRepository.save(user)).thenReturn(user);

        var profile = authService.updateProfile(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                new UpdateProfileRequest(" 王老师 ", "teacher@university.edu", "  ", "周一 14:00-16:00", " 负责课程与实验指导 ")
        );

        assertEquals("王老师", user.getDisplayName());
        assertEquals("teacher@university.edu", user.getEmail());
        assertNull(user.getPhone());
        assertEquals("周一 14:00-16:00", profile.get("officeHours"));
    }

    @Test
    void teacherPermissionsExcludeClassWrite() {
        SysUser user = activeTeacher();
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(user));

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = authService.getCurrentUserProfile(new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER));

        assertThat((List<String>) profile.get("permissions"))
                .contains("CLASS_READ")
                .doesNotContain("CLASS_WRITE");
    }

    private SysUser activeTeacher() {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return 1L;
            }
        };
        user.setUsername("t9001");
        user.setPasswordHash("{noop}123456");
        user.setDisplayName("演示教师");
        user.setRole(UserRole.TEACHER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
