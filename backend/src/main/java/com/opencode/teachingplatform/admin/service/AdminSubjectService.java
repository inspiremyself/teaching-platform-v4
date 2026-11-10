package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeSubjectStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateSubjectRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSubjectRequest;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.infra.audit.AuditResourceType;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.entity.SchoolSubject;
import com.opencode.teachingplatform.school.entity.Subject;
import com.opencode.teachingplatform.school.repository.SchoolSubjectRepository;
import com.opencode.teachingplatform.school.repository.SubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AdminSubjectService {

    private static final Pattern SUBJECT_CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{1,64}$");

    private final SchoolSubjectRepository schoolSubjectRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final AuditLogService auditLogService;
    private final SubjectRepository subjectRepository;

    public AdminSubjectService(
            SchoolSubjectRepository schoolSubjectRepository,
            ClassTeacherRepository classTeacherRepository,
            AuditLogService auditLogService,
            SubjectRepository subjectRepository
    ) {
        this.schoolSubjectRepository = schoolSubjectRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.auditLogService = auditLogService;
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubjects(CurrentUser currentUser, String status) {
        Long schoolId = currentUser.schoolId();
        List<SchoolSubject> subjects = status == null || status.isBlank()
                ? schoolSubjectRepository.findBySchoolId(schoolId)
                : schoolSubjectRepository.findBySchoolIdAndStatus(schoolId, parseStatus(status));
        return subjects.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlatformSubjects() {
        return subjectRepository.findByStatusOrderByCodeAsc(SubjectStatus.ACTIVE).stream()
                .map(platform -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", platform.getId());
                    view.put("code", platform.getCode());
                    view.put("name", platform.getName());
                    view.put("status", platform.getStatus().name());
                    return view;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> enablePlatformSubject(CurrentUser currentUser, Long platformSubjectId) {
        Long schoolId = currentUser.schoolId();
        Subject platform = subjectRepository.findById(platformSubjectId)
                .orElseThrow(() -> new BusinessException(40400, "学科不存在"));
        if (platform.getStatus() != SubjectStatus.ACTIVE) {
            throw new BusinessException(40026, "平台学科已停用，不能启用");
        }
        if (schoolSubjectRepository.existsBySchoolIdAndGlobalSubjectId(schoolId, platform.getId())) {
            throw new BusinessException(40000, "平台学科已启用");
        }

        SchoolSubject subject;
        AuditAction action;
        Optional<SchoolSubject> existing =
                schoolSubjectRepository.findBySchoolIdAndCode(schoolId, platform.getCode());
        if (existing.isPresent()) {
            SchoolSubject row = existing.get();
            if (row.getGlobalSubjectId() != null) {
                throw new BusinessException(40000, "该编码已映射到其它平台学科");
            }
            row.setGlobalSubjectId(platform.getId());
            subject = row;
            action = AuditAction.SUBJECT_UPDATE;
        } else {
            SchoolSubject created = new SchoolSubject();
            created.setSchoolId(schoolId);
            created.setName(platform.getName());
            created.setCode(platform.getCode());
            created.setStatus(SubjectStatus.ACTIVE);
            created.setGlobalSubjectId(platform.getId());
            subject = created;
            action = AuditAction.SUBJECT_CREATE;
        }

        try {
            SchoolSubject saved = schoolSubjectRepository.saveAndFlush(subject);
            auditLogService.log(currentUser, action, AuditResourceType.SUBJECT,
                    saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40000, "平台学科已启用");
        }
    }

    @Transactional
    public Map<String, Object> createSubject(CurrentUser currentUser, CreateSubjectRequest request) {
        Long schoolId = currentUser.schoolId();
        String name = request.name().trim();
        String code = normalizeCode(request.code());
        validateName(name);
        validateCode(code);
        if (schoolSubjectRepository.existsBySchoolIdAndCode(schoolId, code)) {
            throw new BusinessException(40902, "学科编码已存在");
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
            throw new BusinessException(40902, "学科编码已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateSubject(CurrentUser currentUser, Long id, UpdateSubjectRequest request) {
        SchoolSubject subject = requireSubject(currentUser, id);
        String name = request.name().trim();
        String code = normalizeCode(request.code());
        if (subject.getGlobalSubjectId() != null && !subject.getCode().equals(code)) {
            throw new BusinessException(40000, "映射学科编码不可修改");
        }
        validateName(name);
        validateCode(code);
        if (!subject.getCode().equals(code) && schoolSubjectRepository.existsBySchoolIdAndCode(subject.getSchoolId(), code)) {
            throw new BusinessException(40902, "学科编码已存在");
        }
        subject.setName(name);
        subject.setCode(code);
        try {
            SchoolSubject saved = schoolSubjectRepository.save(subject);
            auditLogService.log(currentUser, AuditAction.SUBJECT_UPDATE, AuditResourceType.SUBJECT, saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40902, "学科编码已存在");
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
        return EnumValueParser.parse(SubjectStatus.class, status, "学科状态");
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
        Long globalSubjectId = subject.getGlobalSubjectId();
        view.put("globalSubjectId", globalSubjectId);
        view.put("globalSubjectCode", globalSubjectId == null
                ? null
                : subjectRepository.findById(globalSubjectId).map(Subject::getCode).orElse(null));
        return view;
    }
}
