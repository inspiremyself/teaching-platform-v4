package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.dto.ChangePasswordRequest;
import com.opencode.teachingplatform.auth.dto.LoginRequest;
import com.opencode.teachingplatform.auth.dto.UpdateProfileRequest;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.auth.service.AuthService;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.infra.audit.SecurityAuditEvent;
import com.opencode.teachingplatform.auth.LoginSessionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.UserStatus;
import com.opencode.teachingplatform.school.entity.School;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
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

    /**
     * 统一 stub 密码编码：旧密码升级与改密都会调用 encode，mock 默认返回 null 会掩盖升级断言。
     */
    @org.junit.jupiter.api.BeforeEach
    void stubPasswordEncoding() {
        org.mockito.Mockito.lenient()
                .when(passwordEncoder.encode(anyString()))
                .thenReturn("{bcrypt}$2a$10$stub");
    }

    @Test
    void disabledUserCannotLogin() {
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
        user.setStatus(UserStatus.DISABLED);
        user.setSchoolId(1L);
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(user));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER)));

        assertEquals(40103, exception.getCode());
        assertEquals("该账号已被停用，请联系管理员", exception.getMessage());
        verify(auditLogService).logSecurity(argThat(event ->
                event.action() == AuditAction.LOGIN_FAILURE && event.userId() != null));
    }

    @Test
    void loginUnknownUser_writesFailureWithoutUserId() {
        when(sysUserRepository.findAllByUsernameAndRole("nobody", UserRole.TEACHER)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> authService.login(new LoginRequest("nobody", "123456", UserRole.TEACHER)));

        verify(auditLogService).logSecurity(argThat(event ->
                event.action() == AuditAction.LOGIN_FAILURE && event.userId() == null));
    }

    @Test
    void loginFailure_writesLoginFailure() {
        SysUser user = activeTeacher();
        user.setSchoolId(1L);
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(user));
        when(passwordEncoder.matches("wrong", "{noop}123456")).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.login(new LoginRequest("t9001", "wrong", UserRole.TEACHER)));

        verify(auditLogService).logSecurity(argThat(event ->
                event.action() == AuditAction.LOGIN_FAILURE && event.userId() != null && event.userId().equals(1L)));
    }

    @Test
    void duplicateLoginRequiresForceConfirmation() {
        SysUser user = activeTeacher();
        UserLoginSession existingSession = new UserLoginSession();
        existingSession.setUser(user);
        existingSession.setStatus(LoginSessionStatus.ACTIVE);
        existingSession.setExpiresAt(OffsetDateTime.now().plusHours(2));

        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(user));
        when(passwordEncoder.matches("123456", "{noop}123456")).thenReturn(true);
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

        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(user));
        when(passwordEncoder.matches("123456", "{noop}123456")).thenReturn(true);
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

    @Test
    void changePasswordKeepsCurrentSessionAndKicksOtherSessions() {
        SysUser user = activeTeacher();
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "{noop}123456")).thenReturn(true);
        when(sysUserRepository.save(user)).thenReturn(user);

        authService.changePassword(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, 1L, "current-session"),
                new ChangePasswordRequest("123456", "654321")
        );

        verify(userLoginSessionRepository).kickActiveSessionsByUserIdExceptSessionKey(
                eq(1L), eq("current-session"), eq(LoginSessionStatus.ACTIVE), eq(LoginSessionStatus.KICKED), any(OffsetDateTime.class));
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserId(
                anyLong(), any(), any(), any());
    }

    @Test
    void changePasswordWithoutSessionKeyKicksAllSessions() {
        SysUser user = activeTeacher();
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "{noop}123456")).thenReturn(true);
        when(sysUserRepository.save(user)).thenReturn(user);

        authService.changePassword(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                new ChangePasswordRequest("123456", "654321")
        );

        verify(userLoginSessionRepository).kickActiveSessionsByUserId(
                eq(1L), eq(LoginSessionStatus.ACTIVE), eq(LoginSessionStatus.KICKED), any(OffsetDateTime.class));
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserIdExceptSessionKey(
                anyLong(), any(), any(), any(), any());
    }

    @Test
    void changePasswordWithWrongOldPasswordDoesNotKickSessions() {
        SysUser user = activeTeacher();
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "{noop}123456")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.changePassword(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, 1L, "current-session"),
                new ChangePasswordRequest("wrong", "654321")
        ));

        assertEquals(40010, exception.getCode());
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserId(
                anyLong(), any(), any(), any());
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserIdExceptSessionKey(
                anyLong(), any(), any(), any(), any());
    }

    @Test
    void loginAcrossSchoolsWithoutSchoolCodeReturns40903() {
        SysUser first = teacher(1L, 1L);
        SysUser second = teacher(2L, 2L);
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(first, second));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER)));

        assertEquals(40903, exception.getCode());
        assertEquals("该用户名在多个学校存在，请填写学校编码后重试", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginWithSchoolCodeSelectsMatchingSchoolUser() {
        SysUser first = teacher(1L, 1L);
        SysUser second = teacher(2L, 2L);
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(first, second));
        when(schoolRepository.findByCode("SCH-2")).thenReturn(Optional.of(school(2L, "SCH-2")));
        when(passwordEncoder.matches("123456", "{noop}123456")).thenReturn(true);
        stubSuccessfulSession(second);

        Map<String, Object> result = authService.login(
                new LoginRequest("t9001", "123456", UserRole.TEACHER, false, "  SCH-2  "));

        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertEquals(2L, user.get("id"));
        assertEquals("token", result.get("token"));
    }

    @Test
    void loginWithUnknownSchoolCodeReturns40101() {
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER))
                .thenReturn(List.of(teacher(1L, 1L)));
        when(schoolRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER, false, "NOPE")));

        assertEquals(40101, exception.getCode());
    }

    @Test
    void noopLoginUpgradesPasswordHashToBcrypt() {
        SysUser user = teacher(1L, 1L);
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(user));
        when(passwordEncoder.matches("123456", "{noop}123456")).thenReturn(true);
        stubSuccessfulSession(user);

        authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER));

        assertTrue(user.getPasswordHash().startsWith("{bcrypt}"));
    }

    @Test
    void plaintextLoginUpgradesPasswordHashToBcrypt() {
        SysUser user = teacher(1L, 1L);
        user.setPasswordHash("123456");
        when(sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER)).thenReturn(List.of(user));
        stubSuccessfulSession(user);

        authService.login(new LoginRequest("t9001", "123456", UserRole.TEACHER));

        assertTrue(user.getPasswordHash().startsWith("{bcrypt}"));
    }

    private void stubSuccessfulSession(SysUser user) {
        when(sysUserRepository.save(user)).thenReturn(user);
        when(userLoginSessionRepository.save(any(UserLoginSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.calculateExpirationAt(any(Instant.class)))
                .thenReturn(OffsetDateTime.now().plusDays(1));
        when(jwtTokenService.issueToken(eq(user), any(String.class), any(Instant.class))).thenReturn("token");
    }

    private SysUser teacher(Long id, Long schoolId) {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return id;
            }
        };
        user.setUsername("t9001");
        user.setPasswordHash("{noop}123456");
        user.setDisplayName("演示教师");
        user.setRole(UserRole.TEACHER);
        user.setStatus(UserStatus.ACTIVE);
        user.setSchoolId(schoolId);
        return user;
    }

    private School school(Long id, String code) {
        School school = new School() {
            @Override
            public Long getId() {
                return id;
            }
        };
        school.setCode(code);
        school.setName("学校" + id);
        return school;
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
