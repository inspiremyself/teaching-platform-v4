package com.opencode.teachingplatform.plagiarism;

import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
        jdbc.update("DELETE FROM plagiarism_task WHERE business_id = 9302 AND school_id = 2");
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
}
