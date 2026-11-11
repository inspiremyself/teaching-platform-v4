package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.CoursewareAssignmentTargetRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ResolvedTarget;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次布置的目标解析：逐个班级鉴权 + 班级成员校验 + 去重 / 互斥 + 规范化 CLASS / STUDENT 目标。
 *
 * <p>只解析目标，不写库、不生成 assignment_group_id；目标展开写入归阶段 4 布置端点。</p>
 */
@Service
public class CoursewareAssignmentTargetService {

    private final ClassAccessService classAccessService;
    private final ClassMemberRepository classMemberRepository;
    private final ClassRoomRepository classRoomRepository;

    public CoursewareAssignmentTargetService(ClassAccessService classAccessService,
                                             ClassMemberRepository classMemberRepository,
                                             ClassRoomRepository classRoomRepository) {
        this.classAccessService = classAccessService;
        this.classMemberRepository = classMemberRepository;
        this.classRoomRepository = classRoomRepository;
    }

    public List<ResolvedTarget> resolveTargets(CurrentUser currentUser,
                                               List<CoursewareAssignmentTargetRequest> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new BusinessException(40000, "请至少选择一个布置目标");
        }
        Set<Long> seenClassIds = new HashSet<>();
        List<ResolvedTarget> resolved = new ArrayList<>();
        for (CoursewareAssignmentTargetRequest target : targets) {
            if (target == null || target.classId() == null || !seenClassIds.add(target.classId())) {
                throw new BusinessException(40000, "同一班级只能出现一次");
            }
            // 沿用 D22 同一鉴权入口，失败抛现网 40300
            classAccessService.assertTeacherAssignedToClass(currentUser, target.classId());
            if (classRoomRepository.findByIdAndSchoolId(target.classId(), currentUser.schoolId()).isEmpty()) {
                throw new BusinessException(40400, "班级不存在");
            }
            List<Long> studentIds = target.studentIds() == null ? List.of() : target.studentIds();
            if (studentIds.isEmpty()) {
                resolved.add(new ResolvedTarget(target.classId(), "CLASS", target.classId()));
                continue;
            }
            Set<Long> seenStudentIds = new HashSet<>();
            for (Long studentId : studentIds) {
                if (studentId == null || !seenStudentIds.add(studentId)) {
                    throw new BusinessException(40000, "学生目标重复");
                }
                if (classMemberRepository.findByClassIdAndStudentUserId(target.classId(), studentId).isEmpty()) {
                    throw new BusinessException(40400, "学生不属于该班级");
                }
                resolved.add(new ResolvedTarget(target.classId(), "STUDENT", studentId));
            }
        }
        resolved.sort(Comparator.comparingLong(ResolvedTarget::classId)
                .thenComparingInt(item -> "CLASS".equals(item.targetType()) ? 0 : 1)
                .thenComparingLong(ResolvedTarget::targetId));
        return resolved;
    }
}
