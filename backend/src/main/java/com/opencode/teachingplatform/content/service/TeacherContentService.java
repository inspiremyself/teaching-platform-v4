package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.dto.ContentViews.ContentBrowseItem;
import com.opencode.teachingplatform.content.dto.ContentViews.ContentVisibilityItem;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareAssetItem;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareSectionItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookSectionItem;
import com.opencode.teachingplatform.content.dto.ContentViews.DownloadedAsset;
import com.opencode.teachingplatform.content.dto.ContentViews.PlatformSubjectItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookItem;
import com.opencode.teachingplatform.content.entity.Courseware;
import com.opencode.teachingplatform.content.entity.CoursewareAsset;
import com.opencode.teachingplatform.content.entity.CoursewareSection;
import com.opencode.teachingplatform.content.repository.CoursewareAssetRepository;
import com.opencode.teachingplatform.content.repository.CoursewareRepository;
import com.opencode.teachingplatform.content.repository.CoursewareSectionRepository;
import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.school.entity.Subject;
import com.opencode.teachingplatform.school.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师侧只读浏览：五类筛选 + 按课件内小节预览。
 *
 * <p>可见性一律复用 {@link ContentVisibilityService}（D21），未授权与跨校返回 40400；
 * 教材目录与学科字典是平台级下拉数据，不经授权过滤。</p>
 */
@Service
public class TeacherContentService {

    private static final String CONTENT_TYPE = "COURSEWARE";
    private static final String FILTER_ALL = "ALL";

    private final ContentFilterService contentFilterService;
    private final ContentVisibilityService contentVisibilityService;
    private final CoursewareRepository coursewareRepository;
    private final CoursewareSectionRepository coursewareSectionRepository;
    private final CoursewareAssetRepository coursewareAssetRepository;
    private final SubjectRepository subjectRepository;
    private final PlatformCatalogService platformCatalogService;
    private final LocalFileStorageService localFileStorageService;

    public TeacherContentService(ContentFilterService contentFilterService,
                                 ContentVisibilityService contentVisibilityService,
                                 CoursewareRepository coursewareRepository,
                                 CoursewareSectionRepository coursewareSectionRepository,
                                 CoursewareAssetRepository coursewareAssetRepository,
                                 SubjectRepository subjectRepository,
                                 PlatformCatalogService platformCatalogService,
                                 LocalFileStorageService localFileStorageService) {
        this.contentFilterService = contentFilterService;
        this.contentVisibilityService = contentVisibilityService;
        this.coursewareRepository = coursewareRepository;
        this.coursewareSectionRepository = coursewareSectionRepository;
        this.coursewareAssetRepository = coursewareAssetRepository;
        this.subjectRepository = subjectRepository;
        this.platformCatalogService = platformCatalogService;
        this.localFileStorageService = localFileStorageService;
    }

    /** 五类筛选浏览：先经 D21 过滤，再只保留课件类内容。 */
    public List<ContentBrowseItem> list(CurrentUser currentUser, ContentFilterRequest request) {
        ContentFilterRequest normalized = normalize(request);
        List<ContentRef> refs = contentFilterService.filter(currentUser, normalized);
        List<Long> ids = new ArrayList<>();
        for (ContentRef ref : refs) {
            if (CONTENT_TYPE.equals(ref.contentType())) {
                ids.add(ref.contentId());
            }
        }
        Map<Long, Courseware> byId = new HashMap<>();
        for (Courseware courseware : coursewareRepository.findAllById(ids)) {
            byId.put(courseware.getId(), courseware);
        }
        List<ContentBrowseItem> items = new ArrayList<>();
        for (Long id : ids) {
            Courseware courseware = byId.get(id);
            if (courseware == null) {
                continue;
            }
            int sectionCount = coursewareSectionRepository
                    .findByCoursewareIdOrderBySortOrderAsc(courseware.getId()).size();
            items.add(new ContentBrowseItem(courseware.getId(), courseware.getTitle(), courseware.getSubtitle(),
                    courseware.getGlobalSubjectId(), courseware.getStatus(), sectionCount));
        }
        return items;
    }

    /** 课件小节列表。 */
    public List<CoursewareSectionItem> listSections(CurrentUser currentUser, Long coursewareId) {
        assertVisible(currentUser, coursewareId);
        List<CoursewareAssetItem> assets = assetItems(coursewareId);
        return coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(coursewareId).stream()
                .map(section -> new CoursewareSectionItem(section.getId(), section.getCoursewareId(),
                        section.getHeading(), section.getSortOrder(), section.getContentJson(), assets))
                .toList();
    }

    /** 单个小节预览。 */
    public CoursewareSectionItem getSection(CurrentUser currentUser, Long coursewareId, Long sectionId) {
        assertVisible(currentUser, coursewareId);
        CoursewareSection section = coursewareSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(40400, "课件小节不存在"));
        if (!section.getCoursewareId().equals(coursewareId)) {
            throw new BusinessException(40400, "课件小节不存在");
        }
        return new CoursewareSectionItem(section.getId(), section.getCoursewareId(), section.getHeading(),
                section.getSortOrder(), section.getContentJson(), assetItems(coursewareId));
    }

    /** 读取素材字节。 */
    public DownloadedAsset readAsset(CurrentUser currentUser, Long coursewareId, Long assetId) {
        assertVisible(currentUser, coursewareId);
        CoursewareAsset asset = coursewareAssetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(40400, "素材不存在"));
        if (!asset.getCoursewareId().equals(coursewareId)) {
            throw new BusinessException(40400, "素材不存在");
        }
        byte[] bytes = localFileStorageService.read(asset.getFilePath());
        String fileName = asset.getFilePath().substring(asset.getFilePath().lastIndexOf('/') + 1);
        return new DownloadedAsset(bytes, asset.getMime(), fileName);
    }

    /** 筛选下拉用平台教材目录。 */
    public List<TextbookItem> listTextbooks() {
        return platformCatalogService.listTextbooks(null);
    }

    /** 筛选下拉用启用态平台学科。 */
    public List<PlatformSubjectItem> listSubjects() {
        List<Subject> subjects = new ArrayList<>(
                subjectRepository.findByStatusOrderByCodeAsc(SubjectStatus.ACTIVE));
        subjects.sort(Comparator.comparing(Subject::getCode));
        return subjects.stream()
                .map(subject -> new PlatformSubjectItem(subject.getId(), subject.getCode(), subject.getName(),
                        subject.getStatus().name()))
                .toList();
    }

    /** 筛选下拉用教材小节清单。 */
    public List<TextbookSectionItem> listTextbookSections(Long textbookId) {
        return platformCatalogService.listSections(textbookId);
    }

    private List<CoursewareAssetItem> assetItems(Long coursewareId) {
        return coursewareAssetRepository.findByCoursewareId(coursewareId).stream()
                .map(asset -> new CoursewareAssetItem(asset.getId(), asset.getKind(), asset.getFilePath(),
                        asset.getMime(), asset.getSizeBytes()))
                .toList();
    }

    /** 可见性判定，供前端提示未授权 / 跨校。 */
    public ContentVisibilityItem visibility(CurrentUser currentUser, Long coursewareId) {
        Courseware courseware = coursewareRepository.findById(coursewareId).orElse(null);
        if (courseware == null) {
            return new ContentVisibilityItem(false, "UNKNOWN");
        }
        boolean visible = contentVisibilityService.isVisible(currentUser, refOf(courseware));
        return new ContentVisibilityItem(visible, courseware.getStatus());
    }

    private void assertVisible(CurrentUser currentUser, Long coursewareId) {
        Courseware courseware = coursewareRepository.findById(coursewareId)
                .orElseThrow(() -> new BusinessException(40400, "课件不存在"));
        contentVisibilityService.assertVisible(currentUser, refOf(courseware));
    }

    private ContentRef refOf(Courseware courseware) {
        return new ContentRef(CONTENT_TYPE, courseware.getId(), courseware.getOwnerScope(),
                courseware.getOwnerSchoolId(), courseware.getCreatedBy());
    }

    /** 现网筛选服务要求 filterType 非空，缺省按「全部」归一。 */
    private ContentFilterRequest normalize(ContentFilterRequest request) {
        String filterType = request == null || request.filterType() == null || request.filterType().isBlank()
                ? FILTER_ALL
                : request.filterType();
        if (request == null) {
            return new ContentFilterRequest(filterType, null, null, null, null);
        }
        return new ContentFilterRequest(filterType, request.textbookId(), request.sectionId(),
                request.coursewareSectionId(), request.subjectId());
    }
}
