package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateCoursewareAssignmentRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ResolvedTarget;
import com.opencode.teachingplatform.content.dto.ContentViews.CreateAssignmentResult;
import com.opencode.teachingplatform.content.dto.ContentViews.TeacherAssignmentItem;
import com.opencode.teachingplatform.content.entity.Courseware;
import com.opencode.teachingplatform.content.entity.CoursewareAssignment;
import com.opencode.teachingplatform.content.repository.CoursewareAssignmentRepository;
import com.opencode.teachingplatform.content.repository.CoursewareRepository;
import com.opencode.teachingplatform.content.repository.CoursewareSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 阶段 4 课件布置：一次 POST 写「目标 × 选中节」行，并按班级读取已布置记录。
 *
 * <p>可用性判定与目标解析必须在同一事务内；任一步失败整次不落库，避免产生半份布置。</p>
 */
@Service
public class CoursewareAssignmentWriteService {

    private static final String CONTENT_TYPE = "COURSEWARE";
    private static final String PUBLISHED = "PUBLISHED";

    private final CoursewareAssignmentTargetService coursewareAssignmentTargetService;
    private final CoursewareRepository coursewareRepository;
    private final CoursewareSectionRepository coursewareSectionRepository;
    private final ContentVisibilityService contentVisibilityService;
    private final ClassAccessService classAccessService;
    private final CoursewareAssignmentRepository coursewareAssignmentRepository;

    public CoursewareAssignmentWriteService(CoursewareAssignmentTargetService coursewareAssignmentTargetService,
                                            CoursewareRepository coursewareRepository,
                                            CoursewareSectionRepository coursewareSectionRepository,
                                            ContentVisibilityService contentVisibilityService,
                                            ClassAccessService classAccessService,
                                            CoursewareAssignmentRepository coursewareAssignmentRepository) {
        this.coursewareAssignmentTargetService = coursewareAssignmentTargetService;
        this.coursewareRepository = coursewareRepository;
        this.coursewareSectionRepository = coursewareSectionRepository;
        this.contentVisibilityService = contentVisibilityService;
        this.classAccessService = classAccessService;
        this.coursewareAssignmentRepository = coursewareAssignmentRepository;
    }

    @Transactional
    public CreateAssignmentResult create(CurrentUser currentUser, CreateCoursewareAssignmentRequest request) {
        Courseware courseware = coursewareRepository.findById(request.coursewareId())
                .orElseThrow(() -> new BusinessException(40400, "课件不存在"));
        contentVisibilityService.assertVisible(currentUser, new ContentRef(CONTENT_TYPE, courseware.getId(),
                courseware.getOwnerScope(), courseware.getOwnerSchoolId(), courseware.getCreatedBy()));
        if (!PUBLISHED.equals(courseware.getStatus())) {
            throw new BusinessException(40900, "课件已下架，不能布置");
        }
        List<Long> sectionIds = request.sectionIds();
        Set<Long> distinctIds = new HashSet<>();
        for (Long sectionId : sectionIds) {
            if (sectionId == null || !distinctIds.add(sectionId)) {
                throw new BusinessException(40000, "布置小节重复或不合法");
            }
        }
        if (!distinctIds.isEmpty() && coursewareSectionRepository
                .findByCoursewareIdAndIdIn(courseware.getId(), distinctIds).size() != distinctIds.size()) {
            throw new BusinessException(40000, "小节不属于该课件");
        }
        validateWindow(request.startAt(), request.endAt());
        List<ResolvedTarget> targets = coursewareAssignmentTargetService.resolveTargets(currentUser, request.targets());
        String assignmentGroupId = UUID.randomUUID().toString();
        List<CoursewareAssignment> rows = new ArrayList<>();
        for (ResolvedTarget target : targets) {
            if (sectionIds.isEmpty()) {
                rows.add(newRow(currentUser, assignmentGroupId, target, null, 0, request));
            } else {
                for (int index = 0; index < sectionIds.size(); index++) {
                    rows.add(newRow(currentUser, assignmentGroupId, target,
                            sectionIds.get(index), index + 1, request));
                }
            }
        }
        coursewareAssignmentRepository.saveAll(rows);
        return new CreateAssignmentResult(assignmentGroupId, rows.size());
    }

    public List<TeacherAssignmentItem> listByClass(CurrentUser currentUser, Long classId) {
        classAccessService.assertTeacherAssignedToClass(currentUser, classId);
        List<CoursewareAssignment> rows = coursewareAssignmentRepository
                .findBySchoolAndClass(currentUser.schoolId(), classId);
        Map<Long, String> titles = new HashMap<>();
        List<TeacherAssignmentItem> items = new ArrayList<>();
        for (CoursewareAssignment row : rows) {
            if (!row.getClassId().equals(classId) || !row.getSchoolId().equals(currentUser.schoolId())) {
                throw new BusinessException(40400, "布置记录不存在");
            }
            String title = titles.computeIfAbsent(row.getCoursewareId(),
                    id -> coursewareRepository.findById(id).map(Courseware::getTitle).orElse(""));
            items.add(new TeacherAssignmentItem(row.getAssignmentGroupId(), row.getCoursewareId(), title,
                    row.getClassId(), row.getTargetType(), row.getTargetId(), row.getSectionId(),
                    row.getDisplayOrder(), row.getStartAt(), row.getEndAt(), row.getCreatedAt()));
        }
        return items;
    }

    private void validateWindow(OffsetDateTime startAt, OffsetDateTime endAt) {
        if ((startAt == null) != (endAt == null)) {
            throw new BusinessException(40000, "时间窗须成对填写");
        }
        if (startAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException(40000, "结束时间须晚于开始时间");
        }
    }

    private CoursewareAssignment newRow(CurrentUser currentUser, String assignmentGroupId, ResolvedTarget target,
                                        Long sectionId, int displayOrder, CreateCoursewareAssignmentRequest request) {
        CoursewareAssignment row = new CoursewareAssignment();
        row.setSchoolId(currentUser.schoolId());
        row.setClassId(target.classId());
        row.setTargetType(target.targetType());
        row.setTargetId(target.targetId());
        row.setCoursewareId(request.coursewareId());
        row.setSectionId(sectionId);
        row.setAssignmentGroupId(assignmentGroupId);
        row.setDisplayOrder(displayOrder);
        row.setStartAt(request.startAt());
        row.setEndAt(request.endAt());
        row.setCreatedBy(currentUser.id());
        return row;
    }
}
