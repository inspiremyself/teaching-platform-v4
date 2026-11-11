package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.dto.ContentRequests.GenerateCoursewareRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.GrantSchoolsRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.MountRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.SectionPayload;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateCoursewareRequest;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareAssetItem;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareDetail;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareItem;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareSectionItem;
import com.opencode.teachingplatform.content.dto.ContentViews.DownloadedAsset;
import com.opencode.teachingplatform.content.dto.ContentViews.ShareGrantItem;
import com.opencode.teachingplatform.content.entity.ContentMount;
import com.opencode.teachingplatform.content.entity.Courseware;
import com.opencode.teachingplatform.content.entity.CoursewareAsset;
import com.opencode.teachingplatform.content.entity.CoursewareSection;
import com.opencode.teachingplatform.content.entity.TextbookSection;
import com.opencode.teachingplatform.content.repository.ContentMountRepository;
import com.opencode.teachingplatform.content.repository.ContentShareGrantRepository;
import com.opencode.teachingplatform.content.repository.CoursewareAssetRepository;
import com.opencode.teachingplatform.content.repository.CoursewareAssignmentRepository;
import com.opencode.teachingplatform.content.repository.CoursewareRepository;
import com.opencode.teachingplatform.content.repository.CoursewareSectionRepository;
import com.opencode.teachingplatform.content.repository.TextbookSectionRepository;
import com.opencode.teachingplatform.school.repository.SchoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 平台课件编排：制作编辑、发布下架、授权、素材与物理删除。
 *
 * <p>正文唯一真相源是 courseware_section.content_json；挂载、授权、可见性分别复用阶段 2 基座服务，
 * 本类只做编排与状态机判定，不重复实现领域规则。</p>
 */
@Service
public class PlatformContentService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformContentService.class);

    private static final String CONTENT_TYPE = "COURSEWARE";
    private static final String OWNER_SCOPE = "PLATFORM";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String MOUNT_TYPE_TEXTBOOK_SECTION = "TEXTBOOK_SECTION";
    private static final Set<String> ASSET_SUFFIXES = Set.of(".png", ".jpg", ".webp", ".mp3");
    private static final long MAX_ASSET_BYTES = 50L * 1024 * 1024;

    private final CoursewareRepository coursewareRepository;
    private final CoursewareSectionRepository coursewareSectionRepository;
    private final CoursewareAssetRepository coursewareAssetRepository;
    private final CoursewareAssignmentRepository coursewareAssignmentRepository;
    private final ContentMountRepository contentMountRepository;
    private final ContentShareGrantRepository contentShareGrantRepository;
    private final TextbookSectionRepository textbookSectionRepository;
    private final SchoolRepository schoolRepository;
    private final ContentMountService contentMountService;
    private final ContentShareGrantService contentShareGrantService;
    private final ContentSubjectScopeService contentSubjectScopeService;
    private final PlatformSubjectActiveSupport activeSupport;
    private final CoursewareContentJsonSupport contentJsonSupport;
    private final LocalFileStorageService localFileStorageService;

    public PlatformContentService(CoursewareRepository coursewareRepository,
                                  CoursewareSectionRepository coursewareSectionRepository,
                                  CoursewareAssetRepository coursewareAssetRepository,
                                  CoursewareAssignmentRepository coursewareAssignmentRepository,
                                  ContentMountRepository contentMountRepository,
                                  ContentShareGrantRepository contentShareGrantRepository,
                                  TextbookSectionRepository textbookSectionRepository,
                                  SchoolRepository schoolRepository,
                                  ContentMountService contentMountService,
                                  ContentShareGrantService contentShareGrantService,
                                  ContentSubjectScopeService contentSubjectScopeService,
                                  PlatformSubjectActiveSupport activeSupport,
                                  CoursewareContentJsonSupport contentJsonSupport,
                                  LocalFileStorageService localFileStorageService) {
        this.coursewareRepository = coursewareRepository;
        this.coursewareSectionRepository = coursewareSectionRepository;
        this.coursewareAssetRepository = coursewareAssetRepository;
        this.coursewareAssignmentRepository = coursewareAssignmentRepository;
        this.contentMountRepository = contentMountRepository;
        this.contentShareGrantRepository = contentShareGrantRepository;
        this.textbookSectionRepository = textbookSectionRepository;
        this.schoolRepository = schoolRepository;
        this.contentMountService = contentMountService;
        this.contentShareGrantService = contentShareGrantService;
        this.contentSubjectScopeService = contentSubjectScopeService;
        this.activeSupport = activeSupport;
        this.contentJsonSupport = contentJsonSupport;
        this.localFileStorageService = localFileStorageService;
    }

    /** 平台课件列表：只列 owner_scope = PLATFORM，按 id 倒序。 */
    public List<CoursewareItem> list() {
        List<Courseware> coursewares = new ArrayList<>(coursewareRepository.findByOwnerScope(OWNER_SCOPE));
        coursewares.sort(Comparator.comparing(Courseware::getId).reversed());
        return coursewares.stream().map(this::toItem).toList();
    }

    /** 课件详情：含小节、挂载与素材。 */
    public CoursewareDetail get(Long coursewareId) {
        return toDetail(requireCourseware(coursewareId));
    }

    /** 课件全量编辑：小节全量替换 + 挂载全量替换，与发布门禁共用同一套学科校验。 */
    @Transactional
    public CoursewareDetail update(CurrentUser currentUser, Long coursewareId, UpdateCoursewareRequest request) {
        Courseware courseware = requireCourseware(coursewareId);
        if (!STATUS_DRAFT.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "请先下架再编辑");
        }
        contentSubjectScopeService.validate(currentUser, OWNER_SCOPE, request.globalSubjectId(), null);
        activeSupport.requireActive(request.globalSubjectId());
        for (SectionPayload payload : request.sections()) {
            contentJsonSupport.validate(payload.contentJson());
        }
        replaceSections(coursewareId, request.sections());
        courseware.setTitle(request.title());
        courseware.setSubtitle(request.subtitle());
        courseware.setGlobalSubjectId(request.globalSubjectId());
        coursewareRepository.save(courseware);
        contentMountService.replaceMounts(currentUser, refOf(courseware), request.mounts());
        return toDetail(requireCourseware(coursewareId));
    }

    /** 物理删除：存在布置引用时只允许下架。 */
    @Transactional
    public void delete(CurrentUser currentUser, Long coursewareId) {
        Courseware courseware = requireCourseware(coursewareId);
        if (coursewareAssignmentRepository.countByCoursewareId(coursewareId) > 0) {
            throw new BusinessException(40900, "课件已被布置，只能下架");
        }
        List<String> assetPaths = coursewareAssetRepository.findByCoursewareId(coursewareId).stream()
                .map(CoursewareAsset::getFilePath).toList();
        contentShareGrantService.deleteAllForContent(currentUser, CONTENT_TYPE, coursewareId);
        coursewareSectionRepository.deleteAll(
                coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(coursewareId));
        coursewareAssetRepository.deleteAll(coursewareAssetRepository.findByCoursewareId(coursewareId));
        contentMountRepository.deleteByContentTypeAndContentId(CONTENT_TYPE, coursewareId);
        coursewareRepository.delete(courseware);
        deleteFilesAfterCommit(assetPaths);
    }

    /** 发布：四项门禁与状态改写在同一事务内完成。 */
    @Transactional
    public CoursewareDetail publish(Long coursewareId) {
        Courseware courseware = requireCourseware(coursewareId);
        if (STATUS_PUBLISHED.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "课件已发布");
        }
        assertPublishGate(courseware);
        courseware.setStatus(STATUS_PUBLISHED);
        courseware.setPublishedAt(OffsetDateTime.now());
        coursewareRepository.save(courseware);
        return toDetail(courseware);
    }

    /** 下架：置回 DRAFT 并清空发布时间，已布置记录不受影响。 */
    @Transactional
    public CoursewareDetail unpublish(Long coursewareId) {
        Courseware courseware = requireCourseware(coursewareId);
        if (!STATUS_PUBLISHED.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "课件未发布");
        }
        courseware.setStatus(STATUS_DRAFT);
        courseware.setPublishedAt(null);
        coursewareRepository.save(courseware);
        return toDetail(courseware);
    }

    /** 已授权学校列表。 */
    public List<ShareGrantItem> listGrants(Long coursewareId) {
        requireCourseware(coursewareId);
        return contentShareGrantRepository.findByContentTypeAndContentId(CONTENT_TYPE, coursewareId).stream()
                .map(grant -> new ShareGrantItem(grant.getId(), grant.getSchoolId(), grant.getGrantedBy()))
                .toList();
    }

    /** 按学校集合授权：发布后另一次操作，可多次增删。 */
    @Transactional
    public List<ShareGrantItem> grant(CurrentUser currentUser, Long coursewareId, GrantSchoolsRequest request) {
        Courseware courseware = requireCourseware(coursewareId);
        if (!STATUS_PUBLISHED.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "请先发布再授权");
        }
        for (Long schoolId : request.schoolIds()) {
            if (schoolId == null || !schoolRepository.existsById(schoolId)) {
                throw new BusinessException(40400, "学校不存在");
            }
            contentShareGrantService.grant(currentUser, refOf(courseware), schoolId);
        }
        return listGrants(coursewareId);
    }

    /** 取消单校授权。 */
    @Transactional
    public void revoke(CurrentUser currentUser, Long coursewareId, Long schoolId) {
        Courseware courseware = requireCourseware(coursewareId);
        contentShareGrantService.revoke(currentUser, CONTENT_TYPE, coursewareId, schoolId);
    }

    /** 上传素材：只允许草稿态课件，白名单后缀 + 50MB 上限。 */
    @Transactional
    public CoursewareAssetItem uploadAsset(Long coursewareId, MultipartFile file) {
        Courseware courseware = requireCourseware(coursewareId);
        if (!STATUS_DRAFT.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "请先下架再上传素材");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40000, "素材文件不能为空");
        }
        String suffix = suffixOf(file.getOriginalFilename());
        if (!ASSET_SUFFIXES.contains(suffix)) {
            throw new BusinessException(40000, "素材类型不支持");
        }
        if (file.getSize() > MAX_ASSET_BYTES) {
            throw new BusinessException(40000, "素材超过 50MB");
        }
        String filePath = localFileStorageService.store("courseware-assets/" + coursewareId, file);
        CoursewareAsset asset = new CoursewareAsset();
        asset.setCoursewareId(coursewareId);
        asset.setKind("mp3".equals(suffix) ? "AUDIO" : "IMAGE");
        asset.setFilePath(filePath);
        asset.setMime(file.getContentType());
        asset.setSizeBytes(file.getSize());
        CoursewareAsset saved = coursewareAssetRepository.save(asset);
        return new CoursewareAssetItem(saved.getId(), saved.getKind(), saved.getFilePath(), saved.getMime(),
                saved.getSizeBytes());
    }

    /** 读取素材字节。 */
    public DownloadedAsset readAsset(Long coursewareId, Long assetId) {
        requireCourseware(coursewareId);
        CoursewareAsset asset = coursewareAssetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(40400, "素材不存在"));
        if (!asset.getCoursewareId().equals(coursewareId)) {
            throw new BusinessException(40400, "素材不存在");
        }
        byte[] bytes = localFileStorageService.read(asset.getFilePath());
        String fileName = asset.getFilePath().substring(asset.getFilePath().lastIndexOf('/') + 1);
        return new DownloadedAsset(bytes, asset.getMime(), fileName);
    }

    /**
     * AI 生成落库：coursewareId 为空是首次生成（新建主表 + N 行小节），非空是重新生成
     * （按既有小节顺序更新正文与标题，不新增小节行、不改写主表）。
     */
    @Transactional
    public CoursewareDetail saveGenerated(CurrentUser currentUser, GenerateCoursewareRequest request,
                                          List<SectionPayload> sections) {
        if (request.coursewareId() == null) {
            Courseware courseware = new Courseware();
            courseware.setOwnerScope(OWNER_SCOPE);
            courseware.setOwnerSchoolId(null);
            courseware.setSchoolSubjectId(null);
            courseware.setGlobalSubjectId(request.globalSubjectId());
            courseware.setTitle(request.title());
            courseware.setSubtitle(request.subtitle());
            courseware.setStatus(STATUS_DRAFT);
            courseware.setPublishedAt(null);
            courseware.setCreatedBy(currentUser.id());
            Courseware saved = coursewareRepository.save(courseware);
            int sortOrder = 0;
            for (SectionPayload payload : sections) {
                sortOrder++;
                CoursewareSection section = new CoursewareSection();
                section.setCoursewareId(saved.getId());
                section.setHeading(payload.heading());
                section.setSortOrder(sortOrder);
                section.setContentJson(payload.contentJson());
                coursewareSectionRepository.save(section);
            }
            return toDetail(requireCourseware(saved.getId()));
        }
        Courseware courseware = requireCourseware(request.coursewareId());
        if (!STATUS_DRAFT.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "请先下架再重新生成");
        }
        List<CoursewareSection> existing =
                coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(courseware.getId());
        if (existing.size() != sections.size()) {
            throw new BusinessException(40000, "生成小节数与课件现有小节数不一致");
        }
        int index = 0;
        for (SectionPayload payload : sections) {
            CoursewareSection section = existing.get(index);
            section.setHeading(payload.heading());
            section.setContentJson(payload.contentJson());
            coursewareSectionRepository.save(section);
            index++;
        }
        return toDetail(requireCourseware(courseware.getId()));
    }

    private void assertPublishGate(Courseware courseware) {
        if (courseware.getGlobalSubjectId() == null) {
            throw new BusinessException(40025, "平台学科归属无效");
        }
        activeSupport.requireActive(courseware.getGlobalSubjectId());
        if (!hasLeafTextbookSectionMount(courseware.getId())) {
            throw new BusinessException(40000, "发布前至少挂载一个叶子教材小节");
        }
        List<CoursewareSection> sections =
                coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(courseware.getId());
        if (sections.isEmpty()) {
            throw new BusinessException(40000, "课件小节树不完整");
        }
        int expected = 1;
        for (CoursewareSection section : sections) {
            if (section.getHeading() == null || section.getHeading().isBlank()
                    || section.getSortOrder() == null || section.getSortOrder() != expected) {
                throw new BusinessException(40000, "课件小节树不完整");
            }
            expected++;
        }
        Set<String> assetPaths = new HashSet<>();
        coursewareAssetRepository.findByCoursewareId(courseware.getId())
                .forEach(asset -> assetPaths.add(asset.getFilePath()));
        for (CoursewareSection section : sections) {
            contentJsonSupport.validate(section.getContentJson());
            for (String path : contentJsonSupport.assetPaths(section.getContentJson())) {
                if (!assetPaths.contains(path)) {
                    throw new BusinessException(40000, "课件产物不完整");
                }
            }
        }
    }

    private boolean hasLeafTextbookSectionMount(Long coursewareId) {
        for (ContentMount mount : contentMountRepository.findByContentTypeAndContentId(CONTENT_TYPE, coursewareId)) {
            if (!MOUNT_TYPE_TEXTBOOK_SECTION.equals(mount.getMountType())) {
                continue;
            }
            TextbookSection section = textbookSectionRepository.findById(mount.getMountTargetId()).orElse(null);
            if (section != null && !textbookSectionRepository.existsByParentId(section.getId())) {
                return true;
            }
        }
        return false;
    }

    /** 小节全量替换：先删被移除行，再把剩余行 sort_order 置负临时值，最后按目标值写回，规避唯一键冲突。 */
    private void replaceSections(Long coursewareId, List<SectionPayload> payloads) {
        Set<Integer> sortOrders = new HashSet<>();
        for (SectionPayload payload : payloads) {
            if (!sortOrders.add(payload.sortOrder())) {
                throw new BusinessException(40000, "小节次序不能重复");
            }
        }
        List<CoursewareSection> existing =
                coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(coursewareId);
        Map<Long, CoursewareSection> existingById = new HashMap<>();
        Set<Long> keepIds = new HashSet<>();
        for (SectionPayload payload : payloads) {
            if (payload.id() != null) {
                keepIds.add(payload.id());
            }
        }
        for (CoursewareSection section : existing) {
            existingById.put(section.getId(), section);
            if (!keepIds.contains(section.getId())) {
                coursewareSectionRepository.delete(section);
            }
        }
        coursewareSectionRepository.flush();
        int temporary = 0;
        for (CoursewareSection section : existing) {
            if (keepIds.contains(section.getId())) {
                section.setSortOrder(-(++temporary));
                coursewareSectionRepository.save(section);
            }
        }
        coursewareSectionRepository.flush();
        for (SectionPayload payload : payloads) {
            CoursewareSection section = payload.id() == null ? new CoursewareSection() : existingById.get(payload.id());
            if (section == null) {
                throw new BusinessException(40400, "课件小节不存在");
            }
            section.setCoursewareId(coursewareId);
            section.setHeading(payload.heading());
            section.setSortOrder(payload.sortOrder());
            section.setContentJson(payload.contentJson());
            coursewareSectionRepository.save(section);
        }
        coursewareSectionRepository.flush();
    }

    private void deleteFilesAfterCommit(List<String> filePaths) {
        if (filePaths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            filePaths.forEach(this::deleteQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                filePaths.forEach(PlatformContentService.this::deleteQuietly);
            }
        });
    }

    private void deleteQuietly(String filePath) {
        try {
            localFileStorageService.delete(filePath);
        } catch (Exception ex) {
            LOG.warn("删除课件素材文件失败: {}", filePath, ex);
        }
    }

    private String suffixOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String name = Path.of(originalFilename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private Courseware requireCourseware(Long coursewareId) {
        return coursewareRepository.findById(coursewareId)
                .orElseThrow(() -> new BusinessException(40400, "课件不存在"));
    }

    private ContentRef refOf(Courseware courseware) {
        return new ContentRef(CONTENT_TYPE, courseware.getId(), courseware.getOwnerScope(),
                courseware.getOwnerSchoolId(), courseware.getCreatedBy());
    }

    private CoursewareItem toItem(Courseware courseware) {
        int sectionCount = coursewareSectionRepository
                .findByCoursewareIdOrderBySortOrderAsc(courseware.getId()).size();
        return new CoursewareItem(courseware.getId(), courseware.getTitle(), courseware.getSubtitle(),
                courseware.getGlobalSubjectId(), courseware.getStatus(), courseware.getPublishedAt(), sectionCount);
    }

    private CoursewareDetail toDetail(Courseware courseware) {
        List<CoursewareSectionItem> sections =
                coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(courseware.getId()).stream()
                        .map(section -> new CoursewareSectionItem(section.getId(), section.getCoursewareId(),
                                section.getHeading(), section.getSortOrder(), section.getContentJson(), List.of()))
                        .toList();
        List<MountRequest> mounts = contentMountRepository
                .findByContentTypeAndContentId(CONTENT_TYPE, courseware.getId()).stream()
                .map(mount -> new MountRequest(mount.getMountType(), mount.getMountTargetId()))
                .toList();
        List<CoursewareAssetItem> assets = coursewareAssetRepository.findByCoursewareId(courseware.getId()).stream()
                .map(asset -> new CoursewareAssetItem(asset.getId(), asset.getKind(), asset.getFilePath(),
                        asset.getMime(), asset.getSizeBytes()))
                .toList();
        return new CoursewareDetail(toItem(courseware), sections, mounts, assets);
    }
}
