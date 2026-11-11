package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.entity.ContentShareGrant;
import com.opencode.teachingplatform.content.repository.ContentShareGrantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台内容按学校集合授权；同一条记录只读引用，取消授权删单行，删除平台内容同事务清全部 grant。
 */
@Service
public class ContentShareGrantService {

    private final ContentShareGrantRepository contentShareGrantRepository;

    public ContentShareGrantService(ContentShareGrantRepository contentShareGrantRepository) {
        this.contentShareGrantRepository = contentShareGrantRepository;
    }

    @Transactional
    public void grant(CurrentUser currentUser, ContentRef ref, Long schoolId) {
        assertPlatformAdmin(currentUser);
        if (ref == null || !"PLATFORM".equals(ref.ownerScope()) || schoolId == null) {
            throw new BusinessException(40400, "内容不可共享");
        }
        ContentShareGrant grant = new ContentShareGrant();
        grant.setContentType(ref.contentType());
        grant.setContentId(ref.contentId());
        grant.setSchoolId(schoolId);
        grant.setGrantedBy(currentUser.id());
        // 唯一键由 DB 兜底：重复授权映射为现网 40900 段位
        try {
            contentShareGrantRepository.saveAndFlush(grant);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "该学校已获得授权");
        }
    }

    /** 取消授权只删单行，不删内容。 */
    @Transactional
    public void revoke(CurrentUser currentUser, String contentType, Long contentId, Long schoolId) {
        assertPlatformAdmin(currentUser);
        contentShareGrantRepository.findBySchoolIdAndContentTypeAndContentId(
                        schoolId, contentType, contentId)
                .ifPresent(contentShareGrantRepository::delete);
    }

    /** 删除平台内容时同事务清该内容全部 grant 行。 */
    @Transactional
    public void deleteAllForContent(CurrentUser currentUser, String contentType, Long contentId) {
        assertPlatformAdmin(currentUser);
        contentShareGrantRepository.deleteByContentTypeAndContentId(contentType, contentId);
    }

    private void assertPlatformAdmin(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.PLATFORM_ADMIN) {
            throw new BusinessException(40300, "无权限管理内容授权");
        }
    }
}
