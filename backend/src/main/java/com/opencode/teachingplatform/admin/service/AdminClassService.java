package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ClassTeacherItem;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateClassRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateClassRequest;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.entity.ClassTeacher;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.enums.ClassStatus;
import com.opencode.teachingplatform.common.enums.ClassTeacherRole;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.common.exception.BusinessException;
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
public class AdminClassService {

    private final ClassRoomRepository classRoomRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassMemberRepository classMemberRepository;
    private final SysUserRepository sysUserRepository;
    private final AuditLogService auditLogService;

    public AdminClassService(
            ClassRoomRepository classRoomRepository,
            ClassTeacherRepository classTeacherRepository,
            ClassMemberRepository classMemberRepository,
            SysUserRepository sysUserRepository,
            AuditLogService auditLogService
    ) {
        this.classRoomRepository = classRoomRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classMemberRepository = classMemberRepository;
        this.sysUserRepository = sysUserRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listClasses(CurrentUser currentUser, Long teacherUserId) {
        Long schoolId = currentUser.schoolId();
        List<ClassRoom> rooms = teacherUserId == null
                ? classRoomRepository.findBySchoolId(schoolId)
                : classRoomRepository.findBySchoolIdAndTeacherUserId(schoolId, teacherUserId);
        return rooms.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClass(CurrentUser currentUser, Long id) {
        ClassRoom room = requireClass(currentUser, id);
        return toView(room);
    }

    @Transactional
    public Map<String, Object> createClass(CurrentUser currentUser, CreateClassRequest request) {
        Long schoolId = currentUser.schoolId();
        if (classRoomRepository.existsBySchoolIdAndCode(schoolId, request.code().trim())) {
            throw new BusinessException(40900, "班级编码已存在");
        }
        List<ClassTeacherItem> validatedTeachers = validateTeachers(schoolId, request.teachers());

        ClassRoom room = new ClassRoom();
        room.setSchoolId(schoolId);
        room.setName(request.name().trim());
        room.setCode(request.code().trim());
        room.setStatus(ClassStatus.ACTIVE);
        try {
            ClassRoom saved = classRoomRepository.saveAndFlush(room);
            replaceTeachers(saved.getId(), validatedTeachers);
            auditLogService.log(currentUser.id(), "CLASS_CREATE", "CLASS", saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "班级编码已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateClass(CurrentUser currentUser, Long id, UpdateClassRequest request) {
        ClassRoom room = requireClass(currentUser, id);
        List<ClassTeacherItem> validatedTeachers = validateTeachers(currentUser.schoolId(), request.teachers());
        room.setName(request.name().trim());
        room.setStatus(request.status());
        ClassRoom saved = classRoomRepository.save(room);
        replaceTeachers(saved.getId(), validatedTeachers);
        auditLogService.log(currentUser.id(), "CLASS_UPDATE", "CLASS", saved.getId(), Map.of("name", saved.getName()));
        return toView(saved);
    }

    @Transactional
    public void deleteClass(CurrentUser currentUser, Long id) {
        ClassRoom room = requireClass(currentUser, id);
        if (classMemberRepository.existsByClassId(room.getId())) {
            throw new BusinessException(40020, "班级下存在学生，不能删除");
        }
        classTeacherRepository.deleteByClassId(room.getId());
        classRoomRepository.delete(room);
        auditLogService.log(currentUser.id(), "CLASS_DELETE", "CLASS", id, Map.of("deleted", true));
    }

    private ClassRoom requireClass(CurrentUser currentUser, Long id) {
        return classRoomRepository.findByIdAndSchoolId(id, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "班级不存在"));
    }

    private List<ClassTeacherItem> validateTeachers(Long schoolId, List<ClassTeacherItem> teachers) {
        if (teachers == null || teachers.isEmpty()) {
            throw new BusinessException(40024, "班级至少需要绑定一名教师");
        }
        Set<Long> seenTeacherIds = new HashSet<>();
        int headCount = 0;
        List<ClassTeacherItem> normalized = new ArrayList<>();
        for (ClassTeacherItem item : teachers) {
            if (!seenTeacherIds.add(item.teacherUserId())) {
                throw new BusinessException(40000, "班级教师不能重复");
            }
            SysUser teacher = sysUserRepository.findByIdAndSchoolIdAndRole(item.teacherUserId(), schoolId, UserRole.TEACHER)
                    .orElseThrow(() -> new BusinessException(40300, "无权限访问该资源"));
            if (teacher.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessException(40300, "无权限访问该资源");
            }
            if (item.role() == ClassTeacherRole.HEAD) {
                headCount++;
                if (headCount > 1) {
                    throw new BusinessException(40022, "每个班级最多只能有一名班主任");
                }
                normalized.add(new ClassTeacherItem(item.teacherUserId(), ClassTeacherRole.HEAD, null));
            } else {
                String subjectName = item.subjectName() == null ? null : item.subjectName().trim();
                if (subjectName == null || subjectName.isEmpty() || subjectName.length() > 64) {
                    throw new BusinessException(40023, "学科教师必须填写学科名称");
                }
                normalized.add(new ClassTeacherItem(item.teacherUserId(), ClassTeacherRole.SUBJECT, subjectName));
            }
        }
        return normalized;
    }

    private void replaceTeachers(Long classId, List<ClassTeacherItem> teachers) {
        classTeacherRepository.deleteByClassId(classId);
        OffsetDateTime now = OffsetDateTime.now();
        for (ClassTeacherItem item : teachers) {
            ClassTeacher assignment = new ClassTeacher();
            assignment.setClassId(classId);
            assignment.setTeacherUserId(item.teacherUserId());
            assignment.setRole(item.role());
            assignment.setSubjectName(item.role() == ClassTeacherRole.HEAD ? null : item.subjectName());
            assignment.setAssignedAt(now);
            classTeacherRepository.save(assignment);
        }
    }

    private Map<String, Object> toView(ClassRoom room) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", room.getId());
        view.put("name", room.getName());
        view.put("code", room.getCode());
        view.put("status", room.getStatus().name());
        view.put("studentCount", classMemberRepository.countByClassId(room.getId()));
        view.put("teachers", buildTeacherViews(room.getId()));
        return view;
    }

    private List<Map<String, Object>> buildTeacherViews(Long classId) {
        return classTeacherRepository.findByClassId(classId).stream().map(assignment -> {
            SysUser teacher = sysUserRepository.findById(assignment.getTeacherUserId()).orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("teacherUserId", assignment.getTeacherUserId());
            item.put("teacherName", teacher != null ? teacher.getDisplayName() : "");
            item.put("role", assignment.getRole().name());
            item.put("subjectName", assignment.getSubjectName());
            return item;
        }).toList();
    }
}
