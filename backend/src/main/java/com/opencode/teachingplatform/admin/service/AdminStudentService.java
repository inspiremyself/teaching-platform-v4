package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeStudentStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateStudentRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateStudentRequest;
import com.opencode.teachingplatform.student.dto.StudentRequests.ImportStudentsRequest;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.student.dto.StudentRequests.ImportStudentRow;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminStudentService {

    private final SysUserRepository sysUserRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final AuditLogService auditLogService;

    public AdminStudentService(
            SysUserRepository sysUserRepository,
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            AuditLogService auditLogService
    ) {
        this.sysUserRepository = sysUserRepository;
        this.classRoomRepository = classRoomRepository;
        this.classMemberRepository = classMemberRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudents(CurrentUser currentUser, Long classId) {
        Long schoolId = currentUser.schoolId();
        List<ClassRoom> classes = classId == null
                ? classRoomRepository.findBySchoolId(schoolId)
                : List.of(requireClass(currentUser, classId));
        List<Long> classIds = classes.stream().map(ClassRoom::getId).toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return classMemberRepository.findByClassIdIn(classIds).stream()
                .map(member -> toView(member.getStudentUserId(), member.getClassId()))
                .toList();
    }

    @Transactional
    public Map<String, Object> createStudent(CurrentUser currentUser, CreateStudentRequest request) {
        Long schoolId = currentUser.schoolId();
        ClassRoom targetClass = requireClass(currentUser, request.classId());
        if (sysUserRepository.existsBySchoolIdAndUsername(schoolId, request.username())) {
            throw new BusinessException(40900, "用户名已存在");
        }
        SysUser student = buildStudent(schoolId, request.username(), request.displayName(), request.password());
        SysUser saved = saveStudentOrThrow(student);
        linkStudentToClass(saved.getId(), targetClass.getId());
        auditLogService.log(currentUser, AuditAction.STUDENT_CREATE, AuditResourceType.STUDENT, saved.getId(), Map.of(
                "username", saved.getUsername(),
                "classId", targetClass.getId()
        ));
        return toView(saved.getId(), targetClass.getId());
    }

    @Transactional
    public Map<String, Object> importStudents(CurrentUser currentUser, ImportStudentsRequest request) {
        ClassRoom targetClass = requireClass(currentUser, request.classId());
        List<Map<String, Object>> failures = new ArrayList<>();
        Set<String> seenUsernames = new HashSet<>();
        int successCount = 0;

        for (int index = 0; index < request.rows().size(); index++) {
            ImportStudentRow row = request.rows().get(index);
            String username = row.username();
            String reason = null;
            if (!seenUsernames.add(username) || sysUserRepository.existsBySchoolIdAndUsername(currentUser.schoolId(), username)) {
                reason = "username already exists";
            }

            if (reason != null) {
                failures.add(Map.of("row", index + 1, "reason", reason));
                continue;
            }

            try {
                SysUser saved = saveStudentOrThrow(buildStudent(currentUser.schoolId(), username, row.displayName(), row.password()));
                linkStudentToClass(saved.getId(), targetClass.getId());
                auditLogService.log(currentUser, AuditAction.STUDENT_CREATE, AuditResourceType.STUDENT, saved.getId(), Map.of(
                        "username", saved.getUsername(),
                        "classId", targetClass.getId(),
                        "source", "import"
                ));
                successCount++;
            } catch (BusinessException ex) {
                failures.add(Map.of("row", index + 1, "reason", "username already exists"));
            }
        }

        auditLogService.log(currentUser, AuditAction.STUDENT_IMPORT, AuditResourceType.CLASS, targetClass.getId(), Map.of(
                "classId", targetClass.getId(),
                "successCount", successCount,
                "failureCount", failures.size()
        ));

        return Map.of(
                "successCount", successCount,
                "failureCount", failures.size(),
                "failures", failures
        );
    }

    @Transactional
    public Map<String, Object> updateStudent(CurrentUser currentUser, Long studentId, UpdateStudentRequest request) {
        SysUser student = requireStudent(currentUser, studentId);
        ClassRoom targetClass = requireClass(currentUser, request.classId());
        student.setDisplayName(request.displayName().trim());
        sysUserRepository.save(student);

        ClassMember membership = classMemberRepository.findFirstByStudentUserId(studentId)
                .orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        membership.setClassId(targetClass.getId());
        classMemberRepository.save(membership);
        auditLogService.log(currentUser, AuditAction.STUDENT_UPDATE, AuditResourceType.STUDENT, studentId, Map.of(
                "displayName", student.getDisplayName(),
                "classId", targetClass.getId()
        ));
        return toView(studentId, targetClass.getId());
    }

    @Transactional
    public Map<String, Object> changeStatus(CurrentUser currentUser, Long studentId, ChangeStudentStatusRequest request) {
        SysUser student = requireStudent(currentUser, studentId);
        student.setStatus(parseStatus(request.status()));
        sysUserRepository.save(student);
        auditLogService.log(currentUser, AuditAction.STUDENT_STATUS, AuditResourceType.STUDENT, studentId, Map.of(
                "status", student.getStatus().name()
        ));
        Long classId = classMemberRepository.findFirstByStudentUserId(studentId)
                .map(ClassMember::getClassId)
                .orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        return toView(studentId, classId);
    }

    private ClassRoom requireClass(CurrentUser currentUser, Long classId) {
        return classRoomRepository.findByIdAndSchoolId(classId, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该资源"));
    }

    private SysUser requireStudent(CurrentUser currentUser, Long studentId) {
        SysUser student = sysUserRepository.findByIdAndSchoolIdAndRole(studentId, currentUser.schoolId(), UserRole.STUDENT)
                .orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        classMemberRepository.findFirstByStudentUserId(studentId)
                .orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        return student;
    }

    private UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40000, "学生状态不合法");
        }
    }

    private SysUser buildStudent(Long schoolId, String username, String displayName, String password) {
        SysUser user = new SysUser();
        user.setSchoolId(schoolId);
        user.setUsername(username.trim());
        user.setPasswordHash("{noop}" + password);
        user.setRole(UserRole.STUDENT);
        user.setDisplayName(displayName.trim());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return user;
    }

    private SysUser saveStudentOrThrow(SysUser student) {
        try {
            return sysUserRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "用户名已存在");
        }
    }

    private void linkStudentToClass(Long studentId, Long classId) {
        ClassMember member = classMemberRepository.findFirstByStudentUserId(studentId).orElseGet(ClassMember::new);
        member.setClassId(classId);
        member.setStudentUserId(studentId);
        if (member.getJoinedAt() == null) {
            member.setJoinedAt(OffsetDateTime.now());
        }
        classMemberRepository.save(member);
    }

    private Map<String, Object> toView(Long studentId, Long classId) {
        SysUser student = sysUserRepository.findById(studentId).orElseThrow();
        ClassRoom classRoom = classRoomRepository.findById(classId).orElseThrow();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", student.getId());
        view.put("username", student.getUsername());
        view.put("displayName", student.getDisplayName());
        view.put("classId", classId);
        view.put("className", classRoom.getName());
        view.put("classCode", classRoom.getCode());
        view.put("status", student.getStatus().name());
        return view;
    }
}
