package com.opencode.teachingplatform.analysis;

import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.analysis.service.StudentScoreVisibilityResolver;
import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 成绩载体按校读写隔离回归。 */
@SpringBootTest
@ActiveProfiles("test")
class ScoreSchoolScopeTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScoreRecordRepository scoreRecordRepository;

    @Autowired
    private ScoreRecordWriter scoreRecordWriter;

    @Autowired
    private StudentScoreVisibilityResolver visibilityResolver;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
    }

    @Test
    void studentScoresExcludeOtherSchool() {
        assertThat(scoreRecordRepository.findBySchoolIdAndStudentId(
                SchoolScopeFixtures.SCHOOL_A, SchoolScopeFixtures.STUDENT_ID))
                .extracting(ScoreRecord::getId).doesNotContain(SchoolScopeFixtures.SCORE_B);
        assertThat(scoreRecordRepository.findBySchoolIdAndStudentId(
                SchoolScopeFixtures.SCHOOL_B, SchoolScopeFixtures.STUDENT_ID))
                .extracting(ScoreRecord::getId).contains(SchoolScopeFixtures.SCORE_B);
        assertThat(visibilityResolver.resolve(SchoolScopeFixtures.STUDENT_ID,
                SchoolScopeFixtures.SCHOOL_A))
                .extracting(StudentScoreVisibilityResolver.VisibleScore::id)
                .doesNotContain(SchoolScopeFixtures.SCORE_B);
    }

    @Test
    void teacherDashboardExcludesOtherSchool() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record sr JOIN class_room cr ON cr.id = sr.class_id "
                        + "WHERE cr.school_id = ? AND sr.school_id <> cr.school_id",
                Integer.class, SchoolScopeFixtures.SCHOOL_A)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE school_id = ? AND id = ?",
                Integer.class, SchoolScopeFixtures.SCHOOL_A, SchoolScopeFixtures.SCORE_B)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE school_id = ? AND id = ?",
                Integer.class, SchoolScopeFixtures.SCHOOL_B, SchoolScopeFixtures.SCORE_B)).isEqualTo(1);
    }

    @Test
    void scoreRecordWriteAndRemoveUseMasterSchoolId() {
        scoreRecordWriter.write(BusinessType.EXAM, 9301L, SchoolScopeFixtures.STUDENT_ID,
                SchoolScopeFixtures.CLASS_B, 70D, OffsetDateTime.now(), SchoolScopeFixtures.SCHOOL_B);

        assertThat(jdbc.queryForObject(
                "SELECT school_id FROM score_record WHERE business_type = 'EXAM' AND business_id = 9301",
                Long.class)).isEqualTo(SchoolScopeFixtures.SCHOOL_B);

        scoreRecordWriter.remove(BusinessType.EXAM, 9301L, SchoolScopeFixtures.STUDENT_ID,
                SchoolScopeFixtures.SCHOOL_A);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = 9301",
                Integer.class)).isEqualTo(1);

        scoreRecordWriter.remove(BusinessType.EXAM, 9301L, SchoolScopeFixtures.STUDENT_ID,
                SchoolScopeFixtures.SCHOOL_B);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = 9301",
                Integer.class)).isZero();
    }
}
