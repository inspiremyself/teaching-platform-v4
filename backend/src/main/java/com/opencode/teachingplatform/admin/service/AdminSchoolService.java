package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSchoolRequest;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.entity.School;
import com.opencode.teachingplatform.school.repository.SchoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminSchoolService {

    private final SchoolRepository schoolRepository;
    private final AuditLogService auditLogService;

    public AdminSchoolService(SchoolRepository schoolRepository, AuditLogService auditLogService) {
        this.schoolRepository = schoolRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSchool(CurrentUser currentUser) {
        School school = requireSchool(currentUser.schoolId());
        return toView(school);
    }

    @Transactional
    public Map<String, Object> updateSchool(CurrentUser currentUser, UpdateSchoolRequest request) {
        School school = requireSchool(currentUser.schoolId());
        school.setName(request.name().trim());
        School saved = schoolRepository.save(school);
        auditLogService.log(currentUser, AuditAction.SCHOOL_UPDATE, AuditResourceType.SCHOOL, saved.getId(), Map.of("name", saved.getName()));
        return toView(saved);
    }

    private School requireSchool(Long schoolId) {
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> new BusinessException(40400, "学校不存在"));
    }

    private Map<String, Object> toView(School school) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", school.getId());
        view.put("name", school.getName());
        view.put("code", school.getCode());
        view.put("status", school.getStatus().name());
        return view;
    }
}
