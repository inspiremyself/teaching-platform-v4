package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.school.entity.SchoolSubject;
import com.opencode.teachingplatform.school.repository.SchoolSubjectRepository;
import org.springframework.stereotype.Service;

/**
 * 任务侧 subject_id 校验。新建必填，编辑省略（null）表示保持原值，两者共用同一套校验。
 */
@Service
public class TaskSubjectValidator {

    private final SchoolSubjectRepository schoolSubjectRepository;

    public TaskSubjectValidator(SchoolSubjectRepository schoolSubjectRepository) {
        this.schoolSubjectRepository = schoolSubjectRepository;
    }

    /** 新建任务：subjectId 必填且属于本校 ACTIVE 学科。 */
    public Long requireForCreate(CurrentUser currentUser, Long subjectId) {
        return validate(currentUser, subjectId, true);
    }

    /** 编辑历史任务：subjectId 省略表示保持原值，传入则与新建同一套校验。 */
    public Long requireForUpdate(CurrentUser currentUser, Long subjectId) {
        return validate(currentUser, subjectId, false);
    }

    private Long validate(CurrentUser currentUser, Long subjectId, boolean required) {
        if (subjectId == null) {
            if (required) {
                throw new BusinessException(40025, "请选择学科");
            }
            return null;
        }
        SchoolSubject subject = schoolSubjectRepository
                .findByIdAndSchoolId(subjectId, currentUser.schoolId())
                .filter(item -> item.getStatus() == SubjectStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(40025, "学科无效或不属于本校"));
        return subject.getId();
    }
}
