package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.school.repository.SchoolSubjectRepository;
import com.opencode.teachingplatform.school.repository.SubjectRepository;
import org.springframework.stereotype.Service;

/**
 * 内容分域学科归属：两列互斥，PLATFORM 用平台字典、SCHOOL 用本校字典（含自建）。
 *
 * <p>create 与 update 调用同一个方法，避免兄弟操作口径漂移。</p>
 */
@Service
public class ContentSubjectScopeService {

    private final SubjectRepository subjectRepository;
    private final SchoolSubjectRepository schoolSubjectRepository;

    public ContentSubjectScopeService(SubjectRepository subjectRepository,
                                      SchoolSubjectRepository schoolSubjectRepository) {
        this.subjectRepository = subjectRepository;
        this.schoolSubjectRepository = schoolSubjectRepository;
    }

    public void validate(CurrentUser currentUser, String ownerScope,
                         Long globalSubjectId, Long schoolSubjectId) {
        if ("PLATFORM".equals(ownerScope)) {
            if (globalSubjectId == null || schoolSubjectId != null || !subjectRepository.existsById(globalSubjectId)) {
                throw new BusinessException(40025, "平台内容必须指定有效的平台学科");
            }
            return;
        }
        if ("SCHOOL".equals(ownerScope)) {
            if (schoolSubjectId == null || globalSubjectId != null) {
                throw new BusinessException(40025, "本校内容的学科归属列互斥");
            }
            schoolSubjectRepository.findByIdAndSchoolId(schoolSubjectId, currentUser.schoolId())
                    .filter(item -> item.getStatus() == SubjectStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(40025, "学科无效或不属于本校"));
            return;
        }
        throw new BusinessException(40025, "不支持的内容归属");
    }

    /** 平台教材目录只认平台学科字典。 */
    public void validateTextbook(Long globalSubjectId) {
        if (globalSubjectId == null || !subjectRepository.existsById(globalSubjectId)) {
            throw new BusinessException(40025, "教材必须指定有效的平台学科");
        }
    }
}
