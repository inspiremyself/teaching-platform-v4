package com.opencode.teachingplatform.exam;

import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.exam.entity.Exam;
import com.opencode.teachingplatform.exam.repository.ExamRepository;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 考试域按校读写隔离回归。 */
@SpringBootTest
@ActiveProfiles("test")
class ExamSchoolScopeTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
    }

    @Test
    void teacherOnlySeesOwnSchoolExams() {
        assertThat(examRepository.findBySchoolIdAndCreatedBy(
                SchoolScopeFixtures.SCHOOL_A, SchoolScopeFixtures.TEACHER_ID))
                .extracting(Exam::getId).doesNotContain(SchoolScopeFixtures.EXAM_B);
        assertThat(examRepository.findBySchoolIdAndCreatedBy(
                SchoolScopeFixtures.SCHOOL_B, SchoolScopeFixtures.TEACHER_ID))
                .extracting(Exam::getId).contains(SchoolScopeFixtures.EXAM_B);
    }

    @Test
    void crossSchoolQuestionReferenceReturns40400() {
        assertThat(examRepository.findByIdAndSchoolId(SchoolScopeFixtures.EXAM_B,
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
        assertThat(questionBankRepository.findByIdAndSchoolId(SchoolScopeFixtures.QUESTION_B,
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
        assertThat(questionBankRepository.findByIdAndSchoolId(SchoolScopeFixtures.QUESTION_B,
                SchoolScopeFixtures.SCHOOL_B)).isPresent();
    }

    @Test
    void autoSubmitWritesExamSchoolId() {
        assertThat(examRepository.findByIdAndSchoolId(SchoolScopeFixtures.EXAM_B,
                SchoolScopeFixtures.SCHOOL_B)).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT school_id FROM score_record WHERE id = ?",
                Long.class, SchoolScopeFixtures.SCORE_B)).isEqualTo(SchoolScopeFixtures.SCHOOL_B);
    }
}
