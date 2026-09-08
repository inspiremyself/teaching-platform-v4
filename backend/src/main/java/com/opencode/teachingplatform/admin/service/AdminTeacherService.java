package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeTeacherStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateTeacherRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateTeacherRequest;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminTeacherService {

    private final SysUserRepository sysUserRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final AuditLogService auditLogService;

    public AdminTeacherService(
            SysUserRepository sysUserRepository,
            ClassTeacherRepository classTeacherRepository,
            AuditLogService auditLogService
    ) {
        this.sysUserRepository = sysUserRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeachers(CurrentUser currentUser, String status) {
        Long schoolId = currentUser.schoolId();
        List<SysUser> teachers = status == null || status.isBlank()
                ? sysUserRepository.findBySchoolIdAndRole(schoolId, UserRole.TEACHER)
                : sysUserRepository.findBySchoolIdAndRoleAndStatus(schoolId, UserRole.TEACHER, parseStatus(status));
        return teachers.stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> createTeacher(CurrentUser currentUser, CreateTeacherRequest request) {
        Long schoolId = currentUser.schoolId();
        if (sysUserRepository.existsBySchoolIdAndUsername(schoolId, request.username())) {
            throw new BusinessException(40900, "用户名已存在");
        }
        SysUser teacher = new SysUser();
        teacher.setSchoolId(schoolId);
        teacher.setUsername(request.username().trim());
        teacher.setDisplayName(request.displayName().trim());
        teacher.setPasswordHash("{noop}" + request.password());
        teacher.setRole(UserRole.TEACHER);
        teacher.setStatus(UserStatus.ACTIVE);
        teacher.setMustChangePassword(false);
        try {
            SysUser saved = sysUserRepository.saveAndFlush(teacher);
            auditLogService.log(currentUser.id(), "TEACHER_CREATE", "TEACHER", saved.getId(), Map.of("username", saved.getUsername()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "用户名已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateTeacher(CurrentUser currentUser, Long id, UpdateTeacherRequest request) {
        SysUser teacher = requireTeacher(currentUser, id);
        teacher.setDisplayName(request.displayName().trim());
        SysUser saved = sysUserRepository.save(teacher);
        auditLogService.log(currentUser.id(), "TEACHER_UPDATE", "TEACHER", saved.getId(), Map.of("displayName", saved.getDisplayName()));
        return toView(saved);
    }

    @Transactional
    public Map<String, Object> changeStatus(CurrentUser currentUser, Long id, ChangeTeacherStatusRequest request) {
        SysUser teacher = requireTeacher(currentUser, id);
        teacher.setStatus(parseStatus(request.status()));
        SysUser saved = sysUserRepository.save(teacher);
        auditLogService.log(currentUser.id(), "TEACHER_STATUS", "TEACHER", saved.getId(), Map.of("status", saved.getStatus().name()));
        return toView(saved);
    }

    private SysUser requireTeacher(CurrentUser currentUser, Long id) {
        return sysUserRepository.findByIdAndSchoolIdAndRole(id, currentUser.schoolId(), UserRole.TEACHER)
                .orElseThrow(() -> new BusinessException(40400, "教师不存在"));
    }

    private UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40000, "教师状态不合法");
        }
    }

    private Map<String, Object> toView(SysUser teacher) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", teacher.getId());
        view.put("username", teacher.getUsername());
        view.put("displayName", teacher.getDisplayName());
        view.put("status", teacher.getStatus().name());
        view.put("classCount", classTeacherRepository.countDistinctActiveClassesByTeacherUserId(teacher.getId()));
        return view;
    }
}
