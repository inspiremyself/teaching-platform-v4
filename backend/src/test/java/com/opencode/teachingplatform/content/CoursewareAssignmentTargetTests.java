package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.CoursewareAssignmentTargetRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ResolvedTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 测试数据直接持久化 courseware_assignment 行，不经阶段 4 布置端点与目标展开写入服务。 */
class CoursewareAssignmentTargetTests extends ContentIntegrationTestSupport {

    @Test
    void classWideMultipleClassesAndNamedStudentsShareOneGroup() {
        long cw = insertCourseware(900601L, "PLATFORM", null, platformSubjectId(), null, 999L);
        String group = "44444444-4444-4444-8444-444444444444";
        // 2 个目标 × 2 个选中节 = 4 行
        insertAssignment(900602L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, 1L, group, 1);
        insertAssignment(900603L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, 2L, group, 2);
        insertAssignment(900604L, SCHOOL_ID, 9201L, "STUDENT", STUDENT_ID, cw, 1L, group, 1);
        insertAssignment(900605L, SCHOOL_ID, 9201L, "STUDENT", STUDENT_ID, cw, 2L, group, 2);

        assertThat(coursewareAssignmentRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courseware_assignment WHERE assignment_group_id = ?",
                Long.class, group)).isEqualTo(4L);
    }

    @Test
    void studentVisibilityReturnsOwnClassRowsAndOwnNamedRowsOnly() {
        long cw = insertCourseware(900611L, "PLATFORM", null, platformSubjectId(), null, 999L);
        String group = "55555555-5555-4555-8555-555555555555";
        insertAssignment(900612L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, null, group, 0);
        insertAssignment(900613L, SCHOOL_ID, CLASS_ID, "STUDENT", STUDENT_ID, cw, null, group, 0);
        insertAssignment(900614L, SCHOOL_ID, CLASS_ID, "STUDENT", 999998L, cw, null, group, 0);

        assertThat(coursewareAssignmentRepository.findVisibleToStudent(SCHOOL_ID, CLASS_ID, STUDENT_ID))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.getTargetId()).isIn(CLASS_ID, STUDENT_ID));
        assertThat(coursewareAssignmentRepository.findVisibleToStudent(SCHOOL_ID, CLASS_ID, 999997L))
                .hasSize(1)
                .allSatisfy(row -> assertThat(row.getTargetType()).isEqualTo("CLASS"));
    }

    @Test
    void resolveTargetsNormalizesClassAndStudentRows() {
        List<ResolvedTarget> resolved = coursewareAssignmentTargetService.resolveTargets(teacher(),
                List.of(new CoursewareAssignmentTargetRequest(CLASS_ID, List.of())));
        assertThat(resolved).containsExactly(new ResolvedTarget(CLASS_ID, "CLASS", CLASS_ID));

        List<ResolvedTarget> named = coursewareAssignmentTargetService.resolveTargets(teacher(),
                List.of(new CoursewareAssignmentTargetRequest(CLASS_ID, List.of(STUDENT_ID))));
        assertThat(named).containsExactly(new ResolvedTarget(CLASS_ID, "STUDENT", STUDENT_ID));
    }

    @Test
    void duplicateClassOrStudentIsRejectedWith40000() {
        assertThatThrownBy(() -> coursewareAssignmentTargetService.resolveTargets(teacher(),
                List.of(new CoursewareAssignmentTargetRequest(CLASS_ID, List.of()),
                        new CoursewareAssignmentTargetRequest(CLASS_ID, List.of()))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40000));
        assertThatThrownBy(() -> coursewareAssignmentTargetService.resolveTargets(teacher(),
                List.of(new CoursewareAssignmentTargetRequest(CLASS_ID, List.of(STUDENT_ID, STUDENT_ID)))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40000));
    }

    @Test
    void studentOutsideClassOrSchoolIsRejectedWith40400() {
        assertThatThrownBy(() -> coursewareAssignmentTargetService.resolveTargets(teacher(),
                List.of(new CoursewareAssignmentTargetRequest(CLASS_ID, List.of(999999L)))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40400));
    }

    @Test
    void eachClassIsAuthorizedThroughClassAccessService() {
        assertThatThrownBy(() -> coursewareAssignmentTargetService.resolveTargets(teacher(),
                List.of(new CoursewareAssignmentTargetRequest(999999L, List.of()))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40300));
    }
}
