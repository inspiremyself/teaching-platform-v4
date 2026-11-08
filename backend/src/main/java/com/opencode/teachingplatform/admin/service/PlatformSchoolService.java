package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateSchoolRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSchoolStatusRequest;
import com.opencode.teachingplatform.admin.dto.PlatformSchoolItem;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 平台级学校管理。开通学校与首位校管在同一事务内落地，任一步失败整体回滚；
 * 状态变更只改学校状态，不删除账号、不改动登录会话。
 */
@Service
public class PlatformSchoolService {

    private final SchoolRepository schoolRepository;
    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public PlatformSchoolService(
            SchoolRepository schoolRepository,
            SysUserRepository sysUserRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.schoolRepository = schoolRepository;
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<PlatformSchoolItem> listSchools() {
        return schoolRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(school -> toItem(school, firstAdminUsername(school.getId())))
                .toList();
    }

    @Transactional
    public PlatformSchoolItem createSchool(CurrentUser currentUser, CreateSchoolRequest request) {
        // 校验、入库、审计、返回统一使用归一化值，避免带空格的编码绕过冲突校验后撞唯一约束。
        String name = request.name().trim();
        String code = request.code().trim();
        String adminUsername = request.adminUsername().trim();
        if (schoolRepository.findByCode(code).isPresent()) {
            throw new BusinessException(40904, "学校编码已存在");
        }
        School school = new School();
        school.setName(name);
        school.setCode(code);
        school.setStatus(SchoolStatus.ACTIVE);
        School saved = schoolRepository.saveAndFlush(school);
        if (sysUserRepository.existsBySchoolIdAndUsername(saved.getId(), adminUsername)) {
            throw new BusinessException(40905, "该校已存在同名账号");
        }
        SysUser admin = new SysUser();
        admin.setSchoolId(saved.getId());
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        admin.setRole(UserRole.SCHOOL_ADMIN);
        admin.setDisplayName(adminUsername);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePassword(false);
        sysUserRepository.save(admin);
        auditLogService.log(
                currentUser,
                AuditAction.SCHOOL_CREATE,
                AuditResourceType.SCHOOL,
                saved.getId(),
                Map.of("targetSchoolId", saved.getId(), "code", saved.getCode(), "adminUsername", adminUsername)
        );
        return toItem(saved, adminUsername);
    }

    @Transactional
    public PlatformSchoolItem updateSchoolStatus(CurrentUser currentUser, Long schoolId, UpdateSchoolStatusRequest request) {
        if (schoolId == 1L && request.status() == SchoolStatus.DISABLED) {
            throw new BusinessException(40028, "演示学校不可停用");
        }
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new BusinessException(40400, "学校不存在"));
        school.setStatus(request.status());
        School saved = schoolRepository.save(school);
        auditLogService.log(
                currentUser,
                AuditAction.SCHOOL_STATUS,
                AuditResourceType.SCHOOL,
                schoolId,
                Map.of("targetSchoolId", schoolId, "status", request.status().name())
        );
        return toItem(saved, firstAdminUsername(schoolId));
    }

    private String firstAdminUsername(Long schoolId) {
        return sysUserRepository.findBySchoolIdAndRole(schoolId, UserRole.SCHOOL_ADMIN).stream()
                .min(Comparator.comparing(SysUser::getId))
                .map(SysUser::getUsername)
                .orElse("");
    }

    private PlatformSchoolItem toItem(School school, String adminUsername) {
        return new PlatformSchoolItem(
                school.getId(),
                school.getName(),
                school.getCode(),
                school.getStatus(),
                adminUsername,
                school.getCreatedAt()
        );
    }
}
