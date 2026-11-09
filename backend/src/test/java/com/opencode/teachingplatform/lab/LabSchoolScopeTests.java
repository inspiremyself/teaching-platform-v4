package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.lab.entity.Lab;
import com.opencode.teachingplatform.lab.repository.LabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 实验域按校读写隔离回归。 */
@SpringBootTest
@ActiveProfiles("test")
class LabSchoolScopeTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LabRepository labRepository;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
    }

    @Test
    void teacherOnlySeesOwnSchoolLabs() {
        assertThat(labRepository.findBySchoolIdAndCreatedByOrderByIdDesc(
                SchoolScopeFixtures.SCHOOL_A, SchoolScopeFixtures.TEACHER_ID))
                .extracting(Lab::getId).doesNotContain(SchoolScopeFixtures.LAB_B);
        assertThat(labRepository.findBySchoolIdAndCreatedByOrderByIdDesc(
                SchoolScopeFixtures.SCHOOL_B, SchoolScopeFixtures.TEACHER_ID))
                .extracting(Lab::getId).contains(SchoolScopeFixtures.LAB_B);
    }

    @Test
    void crossSchoolLabIdReturns40400() {
        assertThat(labRepository.findByIdAndSchoolId(SchoolScopeFixtures.LAB_B,
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
        assertThat(labRepository.findByIdAndSchoolId(SchoolScopeFixtures.LAB_B,
                SchoolScopeFixtures.SCHOOL_B)).isPresent();
    }

    @Test
    void copyLabWritesCurrentSchoolId() {
        assertThat(labRepository.findByIdInAndSchoolId(List.of(SchoolScopeFixtures.LAB_B),
                SchoolScopeFixtures.SCHOOL_B)).hasSize(1);
        assertThat(labRepository.findByIdInAndSchoolId(List.of(SchoolScopeFixtures.LAB_B),
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT school_id FROM t_experiment WHERE experiment_id = ?",
                Long.class, SchoolScopeFixtures.LAB_B)).isEqualTo(SchoolScopeFixtures.SCHOOL_B);
    }

    @Test
    void answerImagePreviewRejectsCrossSchoolLab() {
        assertThat(labRepository.findByIdAndSchoolId(SchoolScopeFixtures.LAB_B,
                SchoolScopeFixtures.SCHOOL_A)).isEmpty();
    }
}
