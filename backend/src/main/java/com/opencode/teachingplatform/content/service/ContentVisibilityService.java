package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.repository.ContentShareGrantRepository;
import org.springframework.stereotype.Service;

/**
 * D21 可见性过滤。课件侧本期教师唯一可见集合为 PLATFORM 且已授权本校；
 * 跨校 / 未授权一律 40400，不泄露存在性。
 */
@Service
public class ContentVisibilityService {

    private final ContentShareGrantRepository contentShareGrantRepository;

    public ContentVisibilityService(ContentShareGrantRepository contentShareGrantRepository) {
        this.contentShareGrantRepository = contentShareGrantRepository;
    }

    public void assertVisible(CurrentUser currentUser, ContentRef ref) {
        if (!isVisible(currentUser, ref)) {
            throw new BusinessException(40400, "内容不存在或未授权");
        }
    }

    public boolean isVisible(CurrentUser currentUser, ContentRef ref) {
        if (ref == null || ref.contentType() == null || ref.contentId() == null || ref.ownerScope() == null) {
            return false;
        }
        UserRole role = currentUser.role();
        if (role == UserRole.PLATFORM_ADMIN) {
            return "PLATFORM".equals(ref.ownerScope());
        }
        if (role == UserRole.STUDENT) {
            return false;
        }
        Long schoolId = currentUser.schoolId();
        if (schoolId == null) {
            return false;
        }
        if ("PLATFORM".equals(ref.ownerScope())) {
            return contentShareGrantRepository
                    .existsBySchoolIdAndContentTypeAndContentId(schoolId, ref.contentType(), ref.contentId());
        }
        if ("SCHOOL".equals(ref.ownerScope())) {
            if (!schoolId.equals(ref.ownerSchoolId())) {
                return false;
            }
            // 课件侧本期教师只放行 PLATFORM 且已授权本校的内容
            return role != UserRole.TEACHER || !"COURSEWARE".equals(ref.contentType());
        }
        if ("TEACHER".equals(ref.ownerScope())) {
            return role == UserRole.TEACHER
                    && "QUESTION".equals(ref.contentType())
                    && currentUser.id().equals(ref.createdBy());
        }
        return false;
    }
}
