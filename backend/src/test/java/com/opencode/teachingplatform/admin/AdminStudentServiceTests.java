package com.opencode.teachingplatform.admin;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ResetStudentPasswordRequest;
import com.opencode.teachingplatform.admin.service.AdminStudentService;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.auth.LoginSessionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.UserStatus;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminStudentService} 校管重置学生密码的会话失效单元测试。
 *
 * <p>重置密码后应批量踢掉该学生全部 {@code ACTIVE} 会话，且不影响其他用户；
 * 这正是 TD5 / TD6 的语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminStudentServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_ID = 2L;
    private static final Long OTHER_STUDENT_ID = 3L;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private ClassRoomRepository classRoomRepository;

    @Mock
    private ClassMemberRepository classMemberRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserLoginSessionRepository userLoginSessionRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminStudentService adminStudentService;

    @Test
    void resetPasswordKicksAllActiveSessionsOfTargetStudent() {
        givenStudent(STUDENT_ID);

        Map<String, Object> result = adminStudentService.resetPassword(
                schoolAdmin(), STUDENT_ID, new ResetStudentPasswordRequest("newpass123"));

        assertEquals(true, result.get("reset"));
        // 必须以该学生 id 为过滤条件批量 ACTIVE → KICKED
        verify(userLoginSessionRepository).kickActiveSessionsByUserId(
                eq(STUDENT_ID),
                eq(LoginSessionStatus.ACTIVE),
                eq(LoginSessionStatus.KICKED),
                any(OffsetDateTime.class)
        );
        // 只执行一次批量更新，不做“保留当前会话”的排除式更新
        verify(userLoginSessionRepository, times(1)).kickActiveSessionsByUserId(
                anyLong(), any(), any(), any());
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserIdExceptSessionKey(
                anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void resetPasswordDoesNotKickSessionsOfOtherUsers() {
        givenStudent(STUDENT_ID);

        adminStudentService.resetPassword(schoolAdmin(), STUDENT_ID, new ResetStudentPasswordRequest("newpass123"));

        // 校管本人与其他学生的会话不受影响
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserId(
                eq(ADMIN_ID), any(), any(), any());
        verify(userLoginSessionRepository, never()).kickActiveSessionsByUserId(
                eq(OTHER_STUDENT_ID), any(), any(), any());
    }

    private void givenStudent(Long studentId) {
        SysUser student = new SysUser() {
            @Override
            public Long getId() {
                return studentId;
            }
        };
        student.setSchoolId(1L);
        student.setUsername("20260001");
        student.setDisplayName("演示学生");
        student.setRole(UserRole.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        student.setPasswordHash("{noop}123456");

        when(sysUserRepository.findByIdAndSchoolIdAndRole(studentId, 1L, UserRole.STUDENT))
                .thenReturn(Optional.of(student));
        when(classMemberRepository.findFirstByStudentUserId(studentId))
                .thenReturn(Optional.of(new ClassMember()));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}$2a$10$stub");
    }

    private CurrentUser schoolAdmin() {
        return new CurrentUser(ADMIN_ID, "admin9001", "演示校管", UserRole.SCHOOL_ADMIN, 1L, "admin-session");
    }
}
