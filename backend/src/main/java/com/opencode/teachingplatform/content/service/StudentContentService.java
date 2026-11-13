package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareAssetItem;
import com.opencode.teachingplatform.content.dto.ContentViews.DownloadedAsset;
import com.opencode.teachingplatform.content.dto.ContentViews.StudentAssignmentGroup;
import com.opencode.teachingplatform.content.dto.ContentViews.StudentSectionContent;
import com.opencode.teachingplatform.content.dto.ContentViews.StudentSectionRef;
import com.opencode.teachingplatform.content.entity.Courseware;
import com.opencode.teachingplatform.content.entity.CoursewareAsset;
import com.opencode.teachingplatform.content.entity.CoursewareAssignment;
import com.opencode.teachingplatform.content.entity.CoursewareSection;
import com.opencode.teachingplatform.content.repository.CoursewareAssetRepository;
import com.opencode.teachingplatform.content.repository.CoursewareAssignmentRepository;
import com.opencode.teachingplatform.content.repository.CoursewareRepository;
import com.opencode.teachingplatform.content.repository.CoursewareSectionRepository;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生端阅读：阅读列表 / 小节正文 / 素材共用同一份可见布置行。
 *
 * <p>已布置记录只读当前正文，不校验课件状态与授权；但归属与时间窗一律先在
 * {@code visibleRows} 判定，避免正文与素材直链绕过列表过滤。</p>
 */
@Service
public class StudentContentService {

    private final ClassMemberRepository classMemberRepository;
    private final CoursewareAssignmentRepository coursewareAssignmentRepository;
    private final CoursewareRepository coursewareRepository;
    private final CoursewareSectionRepository coursewareSectionRepository;
    private final CoursewareAssetRepository coursewareAssetRepository;
    private final LocalFileStorageService localFileStorageService;

    public StudentContentService(ClassMemberRepository classMemberRepository,
                                 CoursewareAssignmentRepository coursewareAssignmentRepository,
                                 CoursewareRepository coursewareRepository,
                                 CoursewareSectionRepository coursewareSectionRepository,
                                 CoursewareAssetRepository coursewareAssetRepository,
                                 LocalFileStorageService localFileStorageService) {
        this.classMemberRepository = classMemberRepository;
        this.coursewareAssignmentRepository = coursewareAssignmentRepository;
        this.coursewareRepository = coursewareRepository;
        this.coursewareSectionRepository = coursewareSectionRepository;
        this.coursewareAssetRepository = coursewareAssetRepository;
        this.localFileStorageService = localFileStorageService;
    }

    public List<StudentAssignmentGroup> listGroups(CurrentUser currentUser) {
        Map<String, List<CoursewareAssignment>> grouped = new LinkedHashMap<>();
        for (CoursewareAssignment row : visibleRows(currentUser)) {
            grouped.computeIfAbsent(row.getAssignmentGroupId(), key -> new ArrayList<>()).add(row);
        }
        List<StudentAssignmentGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<CoursewareAssignment>> entry : grouped.entrySet()) {
            List<CoursewareAssignment> groupRows = entry.getValue();
            CoursewareAssignment first = groupRows.get(0);
            String title = coursewareRepository.findById(first.getCoursewareId())
                    .map(Courseware::getTitle).orElse("");
            groups.add(new StudentAssignmentGroup(entry.getKey(), first.getCoursewareId(), title,
                    first.getCreatedAt(), sectionRefs(groupRows)));
        }
        return groups;
    }

    public StudentSectionContent readSection(CurrentUser currentUser, Long coursewareId, Long sectionId) {
        boolean hit = false;
        for (CoursewareAssignment row : visibleRows(currentUser)) {
            if (!row.getCoursewareId().equals(coursewareId)) {
                continue;
            }
            if (row.getSectionId() != null && row.getSectionId().equals(sectionId)) {
                hit = true;
                break;
            }
            if (row.getSectionId() == null && coursewareSectionRepository
                    .findByCoursewareIdOrderBySortOrderAsc(coursewareId).stream()
                    .anyMatch(section -> section.getId().equals(sectionId))) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            throw new BusinessException(40400, "学习内容不存在");
        }
        CoursewareSection section = coursewareSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(40400, "学习内容不存在"));
        if (!section.getCoursewareId().equals(coursewareId)) {
            throw new BusinessException(40400, "学习内容不存在");
        }
        return new StudentSectionContent(coursewareId, sectionId, section.getHeading(), section.getContentJson(),
                assetItems(coursewareId));
    }

    public DownloadedAsset readAsset(CurrentUser currentUser, Long coursewareId, Long assetId) {
        boolean hit = visibleRows(currentUser).stream()
                .anyMatch(row -> row.getCoursewareId().equals(coursewareId));
        if (!hit) {
            throw new BusinessException(40400, "学习内容不存在");
        }
        CoursewareAsset asset = coursewareAssetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(40400, "学习内容不存在"));
        if (!asset.getCoursewareId().equals(coursewareId)) {
            throw new BusinessException(40400, "学习内容不存在");
        }
        byte[] bytes = localFileStorageService.read(asset.getFilePath());
        String fileName = Path.of(asset.getFilePath()).getFileName().toString();
        return new DownloadedAsset(bytes, asset.getMime(), fileName);
    }

    /** 列表、正文与素材的唯一可见集合入口：班级反查 + 归属查询 + 时间窗过滤。 */
    private List<CoursewareAssignment> visibleRows(CurrentUser currentUser) {
        ClassMember member = classMemberRepository.findFirstByStudentUserId(currentUser.id()).orElse(null);
        if (member == null) {
            return List.of();
        }
        List<CoursewareAssignment> rows = coursewareAssignmentRepository.findVisibleToStudent(
                currentUser.schoolId(), member.getClassId(), currentUser.id());
        OffsetDateTime now = OffsetDateTime.now();
        List<CoursewareAssignment> visible = new ArrayList<>();
        for (CoursewareAssignment row : rows) {
            if (row.getStartAt() == null && row.getEndAt() == null) {
                visible.add(row);
                continue;
            }
            if (row.getStartAt() == null || row.getEndAt() == null) {
                continue;
            }
            if (!now.isBefore(row.getStartAt()) && !now.isAfter(row.getEndAt())) {
                visible.add(row);
            }
        }
        return visible;
    }

    private List<StudentSectionRef> sectionRefs(List<CoursewareAssignment> groupRows) {
        Long coursewareId = groupRows.get(0).getCoursewareId();
        List<CoursewareSection> allSections = coursewareSectionRepository
                .findByCoursewareIdOrderBySortOrderAsc(coursewareId);
        boolean whole = groupRows.stream().anyMatch(row -> row.getSectionId() == null);
        if (whole) {
            return allSections.stream()
                    .map(section -> new StudentSectionRef(section.getId(), section.getHeading()))
                    .toList();
        }
        Map<Long, CoursewareSection> byId = new HashMap<>();
        for (CoursewareSection section : allSections) {
            byId.put(section.getId(), section);
        }
        List<CoursewareAssignment> ordered = new ArrayList<>(groupRows);
        ordered.sort(Comparator.comparingInt(CoursewareAssignment::getDisplayOrder));
        Set<Long> seen = new HashSet<>();
        List<StudentSectionRef> refs = new ArrayList<>();
        for (CoursewareAssignment row : ordered) {
            if (!seen.add(row.getSectionId())) {
                continue;
            }
            CoursewareSection section = byId.get(row.getSectionId());
            if (section != null) {
                refs.add(new StudentSectionRef(section.getId(), section.getHeading()));
            }
        }
        return refs;
    }

    private List<CoursewareAssetItem> assetItems(Long coursewareId) {
        return coursewareAssetRepository.findByCoursewareId(coursewareId).stream()
                .map(asset -> new CoursewareAssetItem(asset.getId(), asset.getKind(), asset.getFilePath(),
                        asset.getMime(), asset.getSizeBytes()))
                .toList();
    }
}
