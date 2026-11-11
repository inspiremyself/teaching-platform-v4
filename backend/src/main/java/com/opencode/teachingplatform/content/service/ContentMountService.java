package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.dto.ContentRequests.MountRequest;
import com.opencode.teachingplatform.content.entity.ContentMount;
import com.opencode.teachingplatform.content.entity.Courseware;
import com.opencode.teachingplatform.content.entity.CoursewareSection;
import com.opencode.teachingplatform.content.entity.TextbookSection;
import com.opencode.teachingplatform.content.repository.ContentMountRepository;
import com.opencode.teachingplatform.content.repository.CoursewareRepository;
import com.opencode.teachingplatform.content.repository.CoursewareSectionRepository;
import com.opencode.teachingplatform.content.repository.SchoolTextbookRepository;
import com.opencode.teachingplatform.content.repository.TextbookRepository;
import com.opencode.teachingplatform.content.repository.TextbookSectionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一挂载：0..N 行合法，mounts 全量替换，与目标可见性校验同事务，失败整体回滚。
 */
@Service
public class ContentMountService {

    private final ContentMountRepository contentMountRepository;
    private final ContentVisibilityService contentVisibilityService;
    private final TextbookRepository textbookRepository;
    private final TextbookSectionRepository textbookSectionRepository;
    private final SchoolTextbookRepository schoolTextbookRepository;
    private final CoursewareSectionRepository coursewareSectionRepository;
    private final CoursewareRepository coursewareRepository;

    public ContentMountService(ContentMountRepository contentMountRepository,
                               ContentVisibilityService contentVisibilityService,
                               TextbookRepository textbookRepository,
                               TextbookSectionRepository textbookSectionRepository,
                               SchoolTextbookRepository schoolTextbookRepository,
                               CoursewareSectionRepository coursewareSectionRepository,
                               CoursewareRepository coursewareRepository) {
        this.contentMountRepository = contentMountRepository;
        this.contentVisibilityService = contentVisibilityService;
        this.textbookRepository = textbookRepository;
        this.textbookSectionRepository = textbookSectionRepository;
        this.schoolTextbookRepository = schoolTextbookRepository;
        this.coursewareSectionRepository = coursewareSectionRepository;
        this.coursewareRepository = coursewareRepository;
    }

    @Transactional
    public void replaceMounts(CurrentUser currentUser, ContentRef ref, List<MountRequest> mounts) {
        // P11：不传 mounts 表示不改动；传空数组才是清空
        if (mounts == null) {
            return;
        }
        contentVisibilityService.assertVisible(currentUser, ref);
        List<ContentMount> rows = new ArrayList<>();
        for (MountRequest mount : mounts) {
            validateTarget(currentUser, ref, mount);
            ContentMount row = new ContentMount();
            row.setContentType(ref.contentType());
            row.setContentId(ref.contentId());
            row.setMountType(mount.mountType());
            row.setMountTargetId(mount.mountTargetId());
            rows.add(row);
        }
        contentMountRepository.deleteByContentTypeAndContentId(ref.contentType(), ref.contentId());
        try {
            contentMountRepository.saveAllAndFlush(rows);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "挂载目标重复");
        }
    }

    /** 删目录目标时同事务删命中挂载行；内容本身保留并转未挂载态。 */
    @Transactional
    public void removeMountsByTarget(String mountType, Long mountTargetId) {
        contentMountRepository.deleteByMountTypeAndMountTargetId(mountType, mountTargetId);
        // 同事务内后续查询必须立即看到删除结果，避免一级缓存中的待删实体仍被读回
        contentMountRepository.flush();
    }

    @Transactional
    public void removeMountsByContent(String contentType, Long contentId) {
        contentMountRepository.deleteByContentTypeAndContentId(contentType, contentId);
        contentMountRepository.flush();
    }

    private void validateTarget(CurrentUser currentUser, ContentRef ref, MountRequest mount) {
        if (mount == null || mount.mountType() == null || mount.mountTargetId() == null) {
            throw new BusinessException(40000, "挂载目标非法");
        }
        switch (mount.mountType()) {
            case "TEXTBOOK" -> assertTextbookVisible(currentUser, mount.mountTargetId());
            case "TEXTBOOK_SECTION" -> {
                TextbookSection section = textbookSectionRepository.findById(mount.mountTargetId())
                        .orElseThrow(() -> new BusinessException(40400, "教材小节不存在"));
                assertTextbookVisible(currentUser, section.getTextbookId());
            }
            case "COURSEWARE_SECTION" -> {
                CoursewareSection section = coursewareSectionRepository.findById(mount.mountTargetId())
                        .orElseThrow(() -> new BusinessException(40400, "课件小节不存在"));
                if ("COURSEWARE".equals(ref.contentType())
                        && section.getCoursewareId().equals(ref.contentId())) {
                    throw new BusinessException(40000, "课件不得挂到自身小节");
                }
                Courseware owner = coursewareRepository.findById(section.getCoursewareId())
                        .orElseThrow(() -> new BusinessException(40400, "课件不存在"));
                contentVisibilityService.assertVisible(currentUser, new ContentRef(
                        "COURSEWARE", owner.getId(), owner.getOwnerScope(), owner.getOwnerSchoolId(),
                        owner.getCreatedBy()));
            }
            default -> throw new BusinessException(40000, "不支持的挂载目标类型");
        }
    }

    private void assertTextbookVisible(CurrentUser currentUser, Long textbookId) {
        if (!textbookRepository.existsById(textbookId)) {
            throw new BusinessException(40400, "教材不存在");
        }
        if (currentUser.role() != UserRole.PLATFORM_ADMIN
                && !schoolTextbookRepository.existsBySchoolIdAndTextbookId(currentUser.schoolId(), textbookId)) {
            throw new BusinessException(40400, "教材未对本校启用");
        }
    }
}
