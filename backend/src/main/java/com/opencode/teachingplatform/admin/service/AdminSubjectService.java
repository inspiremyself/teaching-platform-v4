package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeSubjectStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateSubjectRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSubjectRequest;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.enums.SubjectStatus;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.entity.SchoolSubject;
import com.opencode.teachingplatform.school.repository.SchoolSubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AdminSubjectService {

    private static final Pattern SUBJECT_CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{1,64}$");

    private final SchoolSubjectRepository schoolSubjectRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final AuditLogService auditLogService;

    public AdminSubjectService(
            SchoolSubjectRepository schoolSubjectRepository,
            ClassTeacherRepository classTeacherRepository,
            AuditLogService auditLogService
    ) {
        this.schoolSubjectRepository = schoolSubjectRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubjects(CurrentUser currentUser, String status) {
        Long schoolId = currentUser.schoolId();
        List<SchoolSubject> subjects = status == null || status.isBlank()
                ? schoolSubjectRepository.findBySchoolId(schoolId)
                : schoolSubjectRepository.findBySchoolIdAndStatus(schoolId, parseStatus(status));
        return subjects.stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> createSubject(CurrentUser currentUser, CreateSubjectRequest request) {
        Long schoolId = currentUser.schoolId();
        String name = request.name().trim();
        String code = normalizeCode(request.code());
        validateName(name);
        validateCode(code);
        if (schoolSubjectRepository.existsBySchoolIdAndCode(schoolId, code)) {
            throw new BusinessException(40901, "学科编码已存在");
        }
        SchoolSubject subject = new SchoolSubject();
        subject.setSchoolId(schoolId);
        subject.setName(name);
        subject.setCode(code);
        subject.setStatus(SubjectStatus.ACTIVE);
        try {
            SchoolSubject saved = schoolSubjectRepository.saveAndFlush(subject);
            auditLogService.log(currentUser, AuditAction.SUBJECT_CREATE, AuditResourceType.SUBJECT, saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40901, "学科编码已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateSubject(CurrentUser currentUser, Long id, UpdateSubjectRequest request) {
        SchoolSubject subject = requireSubject(currentUser, id);
        String name = request.name().trim();
        String code = normalizeCode(request.code());
        validateName(name);
        validateCode(code);
        if (!subject.getCode().equals(code) && schoolSubjectRepository.existsBySchoolIdAndCode(subject.getSchoolId(), code)) {
            throw new BusinessException(40901, "学科编码已存在");
        }
        subject.setName(name);
        subject.setCode(code);
        try {
            SchoolSubject saved = schoolSubjectRepository.save(subject);
            auditLogService.log(currentUser, AuditAction.SUBJECT_UPDATE, AuditResourceType.SUBJECT, saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40901, "学科编码已存在");
        }
    }

    @Transactional
    public Map<String, Object> changeStatus(CurrentUser currentUser, Long id, ChangeSubjectStatusRequest request) {
        SchoolSubject subject = requireSubject(currentUser, id);
        subject.setStatus(parseStatus(request.status()));
        SchoolSubject saved = schoolSubjectRepository.save(subject);
        auditLogService.log(currentUser, AuditAction.SUBJECT_STATUS, AuditResourceType.SUBJECT, saved.getId(), Map.of("status", saved.getStatus().name()));
        return toView(saved);
    }

    private SchoolSubject requireSubject(CurrentUser currentUser, Long id) {
        return schoolSubjectRepository.findByIdAndSchoolId(id, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "学科不存在"));
    }

    private SubjectStatus parseStatus(String status) {
        try {
            return SubjectStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40000, "学科状态不合法");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private void validateName(String name) {
        if (name.isEmpty() || name.length() > 128) {
            throw new BusinessException(40000, "学科名称长度必须在1到128个字符之间");
        }
    }

    private void validateCode(String code) {
        if (!SUBJECT_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(40000, "学科编码格式不合法");
        }
    }

    private Map<String, Object> toView(SchoolSubject subject) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", subject.getId());
        view.put("name", subject.getName());
        view.put("code", subject.getCode());
        view.put("status", subject.getStatus().name());
        view.put("assignmentCount", classTeacherRepository.countBySubjectId(subject.getId()));
        return view;
    }
}
