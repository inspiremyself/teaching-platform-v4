package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.school.entity.Subject;
import com.opencode.teachingplatform.school.repository.SubjectRepository;
import org.springframework.stereotype.Component;

/**
 * 平台学科启用态校验：内容目录域只认平台字典，且必须处于启用态。
 *
 * <p>现网 {@code ContentSubjectScopeService} 只校验学科存在、不读 status，阶段 3 在调用方补这一层判定，
 * 不改动阶段 2 基座方法，避免出现第二份领域逻辑。</p>
 */
@Component
public class PlatformSubjectActiveSupport {

    private final SubjectRepository subjectRepository;

    public PlatformSubjectActiveSupport(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    /** 要求平台学科存在且为 ACTIVE，否则返回 40025。 */
    public void requireActive(Long globalSubjectId) {
        if (globalSubjectId == null) {
            throw new BusinessException(40025, "平台学科归属无效");
        }
        Subject subject = subjectRepository.findById(globalSubjectId).orElse(null);
        if (subject == null || subject.getStatus() != SubjectStatus.ACTIVE) {
            throw new BusinessException(40025, "平台学科归属无效");
        }
    }
}
