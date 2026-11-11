package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.entity.ContentMount;
import com.opencode.teachingplatform.content.entity.Courseware;
import com.opencode.teachingplatform.content.repository.ContentMountRepository;
import com.opencode.teachingplatform.content.repository.CoursewareRepository;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 五类筛选：精确匹配、不展开树；学科按分域归属列并列过滤、不按挂载目标反推；
 * 结果集一律先经 D21 过滤；教材只作筛选目标、不混入内容列表。
 */
@Service
public class ContentFilterService {

    private final CoursewareRepository coursewareRepository;
    private final QuestionBankRepository questionBankRepository;
    private final ContentMountRepository contentMountRepository;
    private final ContentVisibilityService contentVisibilityService;

    public ContentFilterService(CoursewareRepository coursewareRepository,
                                QuestionBankRepository questionBankRepository,
                                ContentMountRepository contentMountRepository,
                                ContentVisibilityService contentVisibilityService) {
        this.coursewareRepository = coursewareRepository;
        this.questionBankRepository = questionBankRepository;
        this.contentMountRepository = contentMountRepository;
        this.contentVisibilityService = contentVisibilityService;
    }

    public List<ContentRef> filter(CurrentUser currentUser, ContentFilterRequest request) {
        if (request == null || request.filterType() == null) {
            throw new BusinessException(40000, "请指定筛选类型");
        }
        Set<Long> mountedContentIds = null;
        Set<Long> matchedContentIds = null;
        switch (request.filterType()) {
            case "ALL" -> {
                // 不过滤挂载行
            }
            case "UNMOUNTED" -> {
                mountedContentIds = new HashSet<>();
                for (ContentMount mount : contentMountRepository.findAll()) {
                    mountedContentIds.add(mount.getContentId());
                }
            }
            case "TEXTBOOK" -> matchedContentIds = contentIdsOf("TEXTBOOK", request.textbookId());
            case "TEXTBOOK_SECTION" -> matchedContentIds = contentIdsOf("TEXTBOOK_SECTION", request.sectionId());
            case "COURSEWARE_SECTION" ->
                    matchedContentIds = contentIdsOf("COURSEWARE_SECTION", request.coursewareSectionId());
            default -> throw new BusinessException(40000, "不支持的筛选类型");
        }

        List<ContentRef> result = new ArrayList<>();
        for (Courseware courseware : coursewareRepository.findAll()) {
            if (!subjectMatches(request.subjectId(), courseware.getGlobalSubjectId(),
                    courseware.getSchoolSubjectId(), null)) {
                continue;
            }
            if (!inScope(courseware.getId(), matchedContentIds, mountedContentIds)) {
                continue;
            }
            ContentRef ref = new ContentRef("COURSEWARE", courseware.getId(), courseware.getOwnerScope(),
                    courseware.getOwnerSchoolId(), courseware.getCreatedBy());
            if (contentVisibilityService.isVisible(currentUser, ref)) {
                result.add(ref);
            }
        }
        for (QuestionBank question : questionBankRepository.findAll()) {
            if (!subjectMatches(request.subjectId(), null, null, question.getSubjectId())) {
                continue;
            }
            if (!inScope(question.getId(), matchedContentIds, mountedContentIds)) {
                continue;
            }
            ContentRef ref = new ContentRef("QUESTION", question.getId(), question.getOwnerScope(),
                    question.getOwnerSchoolId(), question.getCreatedBy());
            if (contentVisibilityService.isVisible(currentUser, ref)) {
                result.add(ref);
            }
        }
        return result;
    }

    private Set<Long> contentIdsOf(String mountType, Long mountTargetId) {
        if (mountTargetId == null) {
            throw new BusinessException(40000, "请指定筛选目标");
        }
        Set<Long> ids = new HashSet<>();
        for (ContentMount mount : contentMountRepository.findByMountTypeAndMountTargetId(mountType, mountTargetId)) {
            ids.add(mount.getContentId());
        }
        return ids;
    }

    private boolean inScope(Long contentId, Set<Long> matched, Set<Long> mounted) {
        if (matched != null && !matched.contains(contentId)) {
            return false;
        }
        return mounted == null || !mounted.contains(contentId);
    }

    private boolean subjectMatches(Long requested, Long globalSubjectId, Long schoolSubjectId, Long taskSubjectId) {
        if (requested == null) {
            return true;
        }
        return requested.equals(globalSubjectId) || requested.equals(schoolSubjectId)
                || requested.equals(taskSubjectId);
    }
}
