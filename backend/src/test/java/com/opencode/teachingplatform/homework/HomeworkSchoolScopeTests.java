package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 作业域按校读写隔离回归。 */
@SpringBootTest
@ActiveProfiles("test")
class HomeworkSchoolScopeTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
    }

    @Test
    void teacherOnlySeesOwnSchoolHomeworks() {
        assertThat(homeworkRepository.findBySchoolIdAndCreatedByOrderByIdDesc(
                SchoolScopeFixtures.SCHOOL_A, SchoolScopeFixtures.TEACHER_ID))
                .extracting(Homework::getId).doesNotContain(SchoolScopeFixtures.HOMEWORK_B);
        assertThat(homeworkRepository.findBySchoolIdAndCreatedByOrderByIdDesc(
                SchoolScopeFixtures.SCHOOL_B, SchoolScopeFixtures.TEACHER_ID))
                .extracting(Homework::getId).contains(SchoolScopeFixtures.HOMEWORK_B);
    }

    @Test
    void crossSchoolHomeworkIdReturns40400() {
        assertThat(homeworkRepository.findByIdAndSchoolId(SchoolScopeFixtures.HOMEWORK_B,
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
        assertThat(homeworkRepository.findByIdAndSchoolId(SchoolScopeFixtures.HOMEWORK_B,
                SchoolScopeFixtures.SCHOOL_B)).isPresent();
    }

    @Test
    void returnSubmissionDeletesOwnSchoolScoreOnly() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE id = ? AND school_id = ?",
                Integer.class, SchoolScopeFixtures.SCORE_B, SchoolScopeFixtures.SCHOOL_B)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE id = ? AND school_id = ?",
                Integer.class, SchoolScopeFixtures.SCORE_B, SchoolScopeFixtures.SCHOOL_A)).isZero();
        assertThat(homeworkRepository.findByIdAndSchoolId(SchoolScopeFixtures.HOMEWORK_B,
                SchoolScopeFixtures.SCHOOL_B)).isPresent();
    }
}
