package com.opencode.teachingplatform.plagiarism;

import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismService;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 查重任务按校隔离回归；历史行由 BusinessSchoolScopeSchemaTests 按 ①② 两类断言。 */
@SpringBootTest
@ActiveProfiles("test")
class PlagiarismSchoolScopeTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlagiarismTaskRepository plagiarismTaskRepository;

    @Autowired
    private PlagiarismTaskWriter plagiarismTaskWriter;

    @Autowired
    private PlagiarismService plagiarismService;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
        jdbc.update("DELETE FROM plagiarism_task WHERE business_id = 9302 AND school_id = 2");
        jdbc.update("DELETE FROM experiment_submission WHERE id = 9301");
        jdbc.update("INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, submitted_at) "
                        + "VALUES (9301, ?, ?, 'SUBMITTED', CURRENT_TIMESTAMP)",
                SchoolScopeFixtures.LAB_B, SchoolScopeFixtures.STUDENT_ID);
        jdbc.update("DELETE FROM plagiarism_task WHERE id = 9302");
        jdbc.update("INSERT INTO plagiarism_task (id, business_type, business_id, student_id, status, similarity_rate, school_id) "
                        + "VALUES (9302, 'LAB', 9301, ?, 'COMPLETED', 66, ?)",
                SchoolScopeFixtures.STUDENT_ID, SchoolScopeFixtures.SCHOOL_B);
    }

    @Test
    void listTasksDoesNotUseFindAll() {
        assertThat(plagiarismTaskRepository.findBySchoolId(SchoolScopeFixtures.SCHOOL_A))
                .extracting(PlagiarismTask::getId).doesNotContain(SchoolScopeFixtures.PLAGIARISM_B);
        assertThat(plagiarismTaskRepository.findBySchoolId(SchoolScopeFixtures.SCHOOL_B))
                .extracting(PlagiarismTask::getId).contains(SchoolScopeFixtures.PLAGIARISM_B);
    }

    @Test
    void detailAndReviewRejectCrossSchoolId() {
        assertThat(plagiarismTaskRepository.findByIdAndSchoolId(SchoolScopeFixtures.PLAGIARISM_B,
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
        assertThat(plagiarismTaskRepository.findByIdAndSchoolId(SchoolScopeFixtures.PLAGIARISM_B,
                SchoolScopeFixtures.SCHOOL_B)).isPresent();
    }

    @Test
    void newTaskUsesMasterSchoolId() {
        plagiarismTaskWriter.write(BusinessType.HOMEWORK, 9302L, SchoolScopeFixtures.STUDENT_ID,
                42D, "跨校摘要", "{}", SchoolScopeFixtures.SCHOOL_B);

        assertThat(jdbc.queryForObject(
                "SELECT school_id FROM plagiarism_task WHERE business_id = 9302 AND school_id = ?",
                Long.class, SchoolScopeFixtures.SCHOOL_B)).isEqualTo(SchoolScopeFixtures.SCHOOL_B);
        assertThat(plagiarismTaskRepository.findBySchoolId(SchoolScopeFixtures.SCHOOL_A))
                .noneMatch(task -> Long.valueOf(9302L).equals(task.getBusinessId()));
    }

    @Test
    void labBusinessNameReadsOwnSchoolExperimentName() {
        List<Map<String, Object>> tasks = plagiarismService.listTasks(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, SchoolScopeFixtures.SCHOOL_B, "session"));

        assertThat(tasks).anySatisfy(view -> assertThat(view.get("businessName")).isEqualTo("B校实验"));
    }
}
