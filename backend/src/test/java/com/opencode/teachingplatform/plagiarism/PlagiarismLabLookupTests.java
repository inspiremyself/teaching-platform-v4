package com.opencode.teachingplatform.plagiarism;

import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 查重模块 LAB 业务名与归属校验改查实验新表后的回归。
 *
 * <p>覆盖三条 LAB 原生 SQL：{@code resolveBusinessName} 的两步反查、
 * {@code ownsLabSubmission} 的三条件 COUNT，以及 {@code resolveStudentName} 的按校过滤。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PlagiarismLabLookupTests {

    private static final Long LAB_SUBMISSION_ID = 9301L;
    private static final Long LAB_TASK_ID = 9302L;
    private static final Long B_SCHOOL_STUDENT_ID = 9303L;
    private static final Long SCHOOL_B_TEACHER_ID = 1L;
    private static final Long SCHOOL_B_OTHER_TEACHER_ID = 99L;
    private static final Long LEGACY_LAB_TASK_ID = -2L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlagiarismService plagiarismService;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
        jdbc.update("DELETE FROM plagiarism_task WHERE id = ?", LAB_TASK_ID);
        jdbc.update("DELETE FROM experiment_submission WHERE id = ?", LAB_SUBMISSION_ID);
        jdbc.update("INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, submitted_at) "
                        + "VALUES (?, ?, ?, 'SUBMITTED', CURRENT_TIMESTAMP)",
                LAB_SUBMISSION_ID, SchoolScopeFixtures.LAB_B, SchoolScopeFixtures.STUDENT_ID);
        jdbc.update("INSERT INTO plagiarism_task (id, business_type, business_id, student_id, status, similarity_rate, school_id) "
                        + "VALUES (?, 'LAB', ?, ?, 'COMPLETED', 66, ?)",
                LAB_TASK_ID, LAB_SUBMISSION_ID, SchoolScopeFixtures.STUDENT_ID, SchoolScopeFixtures.SCHOOL_B);
        jdbc.update("DELETE FROM sys_user WHERE id = ?", B_SCHOOL_STUDENT_ID);
        jdbc.update("INSERT INTO sys_user (id, username, password_hash, role, display_name, status, school_id) "
                        + "VALUES (?, 's9303', '{noop}test', 'STUDENT', 'B校学生', 'ACTIVE', ?)",
                B_SCHOOL_STUDENT_ID, SchoolScopeFixtures.SCHOOL_B);
        // 仅本类把 B 校作业查重任务改挂到自造学生，便于断言按校解析出的学生名
        jdbc.update("UPDATE plagiarism_task SET student_id = ? WHERE id = ?",
                B_SCHOOL_STUDENT_ID, SchoolScopeFixtures.PLAGIARISM_B);
    }

    @Test
    void labBusinessNameReadsOwnSchoolExperimentName() {
        List<Map<String, Object>> tasks = plagiarismService.listTasks(teacher(SCHOOL_B_TEACHER_ID, SchoolScopeFixtures.SCHOOL_B));

        Map<String, Object> task = taskOf(tasks, LAB_TASK_ID);
        assertThat(task.get("businessName")).isEqualTo("B校实验");
    }

    @Test
    void labBusinessNameRejectsCrossSchoolSubmission() {
        List<Map<String, Object>> tasks = plagiarismService.listTasks(teacher(SchoolScopeFixtures.TEACHER_ID, SchoolScopeFixtures.SCHOOL_A));

        assertThat(tasks).noneMatch(view -> LAB_SUBMISSION_ID.equals(view.get("businessId")));
        assertThatThrownBy(() -> plagiarismService.getTaskDetail(
                student(SchoolScopeFixtures.STUDENT_ID, SchoolScopeFixtures.SCHOOL_A), LAB_TASK_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(40400);
                    assertThat(exception.getMessage()).isEqualTo("查重任务不存在");
                });
    }

    @Test
    void labOwnershipAcceptsSameSchoolTeacher() {
        List<Map<String, Object>> tasks = plagiarismService.listTasks(teacher(SCHOOL_B_TEACHER_ID, SchoolScopeFixtures.SCHOOL_B));

        assertThat(tasks).extracting(view -> view.get("id")).contains(LAB_TASK_ID);
    }

    @Test
    void labOwnershipRejectsCrossSchoolTeacher() {
        assertThatThrownBy(() -> plagiarismService.reviewTask(
                teacher(SCHOOL_B_OTHER_TEACHER_ID, SchoolScopeFixtures.SCHOOL_B), LAB_TASK_ID, "PASS", "ok"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(40300);
                    assertThat(exception.getMessage()).isEqualTo("无权限访问该查重记录");
                });
    }

    @Test
    void legacySeedTaskStillReturnsUnknownActivity() {
        Map<String, Object> view = plagiarismService.getTaskDetail(
                student(SchoolScopeFixtures.STUDENT_ID, SchoolScopeFixtures.SCHOOL_A), LEGACY_LAB_TASK_ID);

        assertThat(view.get("businessName")).isEqualTo("未知活动");
        assertThatThrownBy(() -> plagiarismService.reviewTask(
                teacher(SchoolScopeFixtures.TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), LEGACY_LAB_TASK_ID, "PASS", "ok"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(40300));
    }

    @Test
    void studentNameIsSchoolScoped() {
        Map<String, Object> view = plagiarismService.getTaskDetail(
                student(B_SCHOOL_STUDENT_ID, SchoolScopeFixtures.SCHOOL_B), SchoolScopeFixtures.PLAGIARISM_B);

        assertThat(view.get("studentName")).isEqualTo("B校学生");
    }

    private Map<String, Object> taskOf(List<Map<String, Object>> tasks, Long taskId) {
        return tasks.stream()
                .filter(view -> taskId.equals(view.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("查重任务列表缺少 id = " + taskId));
    }

    private CurrentUser teacher(Long id, Long schoolId) {
        return new CurrentUser(id, "t" + id, "教师" + id, UserRole.TEACHER, schoolId, "session-" + id);
    }

    private CurrentUser student(Long id, Long schoolId) {
        return new CurrentUser(id, "s" + id, "学生" + id, UserRole.STUDENT, schoolId, "session-s" + id);
    }
}
