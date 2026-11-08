package com.opencode.teachingplatform.admin;

import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateSchoolRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSchoolStatusRequest;
import com.opencode.teachingplatform.admin.dto.PlatformSchoolItem;
import com.opencode.teachingplatform.admin.service.PlatformSchoolService;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.UserStatus;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.infra.audit.AuditResourceType;
import com.opencode.teachingplatform.school.SchoolStatus;
import com.opencode.teachingplatform.school.entity.School;
import com.opencode.teachingplatform.school.repository.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSchoolServiceTests {

    private final CurrentUser platformAdmin =
            new CurrentUser(6L, "platform9001", "演示平台超管", UserRole.PLATFORM_ADMIN, null, "session");

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PlatformSchoolService platformSchoolService;

    @Test
    void createSchoolNormalizesValuesBeforeValidationPersistAndAudit() {
        School saved = school(9L, "SCH-9");
        when(schoolRepository.findByCode("SCH-9")).thenReturn(Optional.empty());
        when(schoolRepository.saveAndFlush(any(School.class))).thenReturn(saved);
        when(sysUserRepository.existsBySchoolIdAndUsername(9L, "admin9")).thenReturn(false);
        when(passwordEncoder.encode("pass1234")).thenReturn("{bcrypt}hash");

        PlatformSchoolItem item = platformSchoolService.createSchool(
                platformAdmin, new CreateSchoolRequest("  新学校  ", "  SCH-9  ", "  admin9  ", "pass1234"));

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).saveAndFlush(schoolCaptor.capture());
        assertEquals("新学校", schoolCaptor.getValue().getName());
        assertEquals("SCH-9", schoolCaptor.getValue().getCode());
        assertEquals(SchoolStatus.ACTIVE, schoolCaptor.getValue().getStatus());

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserRepository).save(userCaptor.capture());
        assertEquals(9L, userCaptor.getValue().getSchoolId());
        assertEquals("admin9", userCaptor.getValue().getUsername());
        assertEquals("admin9", userCaptor.getValue().getDisplayName());
        assertEquals("{bcrypt}hash", userCaptor.getValue().getPasswordHash());
        assertEquals(UserRole.SCHOOL_ADMIN, userCaptor.getValue().getRole());
        assertEquals(UserStatus.ACTIVE, userCaptor.getValue().getStatus());

        assertEquals("admin9", item.adminUsername());
        verify(auditLogService).log(
                eq(platformAdmin), eq(AuditAction.SCHOOL_CREATE), eq(AuditResourceType.SCHOOL), eq(9L),
                argThat(detail -> Long.valueOf(9L).equals(detail.get("targetSchoolId"))
                        && "SCH-9".equals(detail.get("code"))
                        && "admin9".equals(detail.get("adminUsername"))));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(schoolRepository, sysUserRepository);
        inOrder.verify(schoolRepository).findByCode("SCH-9");
        inOrder.verify(schoolRepository).saveAndFlush(any(School.class));
        inOrder.verify(sysUserRepository).existsBySchoolIdAndUsername(9L, "admin9");
        inOrder.verify(sysUserRepository).save(any(SysUser.class));
    }

    @Test
    void createSchoolRejectsDuplicateCode() {
        when(schoolRepository.findByCode("SCH-9")).thenReturn(Optional.of(school(9L, "SCH-9")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> platformSchoolService.createSchool(
                        platformAdmin, new CreateSchoolRequest("新学校", "SCH-9", "admin9", "pass1234")));

        assertEquals(40904, exception.getCode());
        verify(schoolRepository, never()).saveAndFlush(any(School.class));
    }

    @Test
    void createSchoolRejectsDuplicateAdminUsernameBeforeSavingUser() {
        School saved = school(9L, "SCH-9");
        when(schoolRepository.findByCode("SCH-9")).thenReturn(Optional.empty());
        when(schoolRepository.saveAndFlush(any(School.class))).thenReturn(saved);
        when(sysUserRepository.existsBySchoolIdAndUsername(9L, "admin9")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> platformSchoolService.createSchool(
                        platformAdmin, new CreateSchoolRequest("新学校", "SCH-9", "admin9", "pass1234")));

        assertEquals(40905, exception.getCode());
        verify(sysUserRepository, never()).save(any(SysUser.class));
    }

    @Test
    void demoSchoolCannotBeDisabled() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> platformSchoolService.updateSchoolStatus(
                        platformAdmin, 1L, new UpdateSchoolStatusRequest(SchoolStatus.DISABLED)));

        assertEquals(40028, exception.getCode());
        verify(schoolRepository, never()).findById(any());
    }

    @Test
    void updateStatusWritesTargetSchoolIdAndDoesNotTouchAccounts() {
        School school = school(9L, "SCH-9");
        school.setStatus(SchoolStatus.ACTIVE);
        when(schoolRepository.findById(9L)).thenReturn(Optional.of(school));
        when(schoolRepository.save(school)).thenReturn(school);
        when(sysUserRepository.findBySchoolIdAndRole(9L, UserRole.SCHOOL_ADMIN)).thenReturn(List.of());

        PlatformSchoolItem item = platformSchoolService.updateSchoolStatus(
                platformAdmin, 9L, new UpdateSchoolStatusRequest(SchoolStatus.DISABLED));

        assertThat(school.getStatus()).isEqualTo(SchoolStatus.DISABLED);
        assertThat(item.adminUsername()).isEmpty();
        verify(sysUserRepository, never()).save(any(SysUser.class));
        verify(auditLogService).log(
                eq(platformAdmin), eq(AuditAction.SCHOOL_STATUS), eq(AuditResourceType.SCHOOL), eq(9L),
                argThat(detail -> Long.valueOf(9L).equals(detail.get("targetSchoolId"))
                        && "DISABLED".equals(detail.get("status"))));
    }

    @Test
    void listSchoolsUsesAscendingIdOrderAndSmallestAdminId() {
        School first = school(1L, "DEMO-SCHOOL");
        School second = school(9L, "SCH-9");
        when(schoolRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(first, second));
        when(sysUserRepository.findBySchoolIdAndRole(9L, UserRole.SCHOOL_ADMIN))
                .thenReturn(List.of(admin(20L, "later"), admin(10L, "first")));
        when(sysUserRepository.findBySchoolIdAndRole(1L, UserRole.SCHOOL_ADMIN)).thenReturn(List.of());

        List<PlatformSchoolItem> items = platformSchoolService.listSchools();

        assertThat(items).extracting(PlatformSchoolItem::id).containsExactly(1L, 9L);
        assertThat(items.get(0).adminUsername()).isEmpty();
        assertThat(items.get(1).adminUsername()).isEqualTo("first");
    }

    private School school(Long id, String code) {
        School school = new School() {
            @Override
            public Long getId() {
                return id;
            }
        };
        school.setName("学校" + id);
        school.setCode(code);
        school.setStatus(SchoolStatus.ACTIVE);
        return school;
    }

    private SysUser admin(Long id, String username) {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return id;
            }
        };
        user.setUsername(username);
        return user;
    }
}
