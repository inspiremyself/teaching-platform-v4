package com.opencode.teachingplatform.common;

import com.opencode.teachingplatform.admin.service.AdminSubjectService;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.homework.service.HomeworkServiceSupport;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.service.LabConfigService;
import com.opencode.teachingplatform.lab.service.LabServiceSupport;
import com.opencode.teachingplatform.material.dto.MaterialRequests.UpdateMaterialRequest;
import com.opencode.teachingplatform.material.service.MaterialService;
import com.opencode.teachingplatform.question.dto.QuestionRequests.SaveQuestionRequest;
import com.opencode.teachingplatform.question.service.QuestionBankCopyService;
import com.opencode.teachingplatform.question.service.QuestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 读侧两步查询改造后的跨校 / 同校归属语义回归。
 *
 * <p>跨校 id 统一由 {@code findByIdAndSchoolId} 拦为 {@code 40400}，
 * 同校他人资源仍由 createdBy / uploader 校验拦为 {@code 40300}，两者不得互相吞并。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CrossSchoolNotFoundTests {

    private static final Long LAB_A = 9101L;
    private static final Long HOMEWORK_A = 9102L;
    private static final Long QUESTION_A = 9103L;
    private static final Long MATERIAL_A = 9104L;
    private static final Long MATERIAL_B = 9105L;
    private static final Long HOMEWORK_QUESTION_B = 9108L;
    private static final Long LAB_ITEM_B = 9109L;
    private static final Long FOREIGN_TEACHER_ID = 9L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminSubjectService adminSubjectService;

    @Autowired
    private LabServiceSupport labServiceSupport;

    @Autowired
    private HomeworkServiceSupport homeworkServiceSupport;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionBankCopyService questionBankCopyService;

    @Autowired
    private LabConfigService labConfigService;

    @Autowired
    private MaterialService materialService;

    private Long classA;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM school_subject WHERE school_id = ? AND code = ?",
                SchoolScopeFixtures.SCHOOL_B, "B-SUBJECT");
    }

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
        classA = jdbc.queryForObject(
                "SELECT MIN(id) FROM class_room WHERE school_id = 1 AND status = 'ACTIVE'", Long.class);

        jdbc.update("DELETE FROM course_material WHERE id = ?", MATERIAL_B);
        jdbc.update("INSERT INTO course_material (id, title, category, file_path, file_name, uploader_user_id, visibility, school_id) "
                        + "VALUES (?, 'B校资料', 'COURSE', 'test/b.txt', 'b.txt', ?, 'ALL', ?)",
                MATERIAL_B, SchoolScopeFixtures.TEACHER_ID, SchoolScopeFixtures.SCHOOL_B);
        jdbc.update("DELETE FROM homework_question WHERE id = ?", HOMEWORK_QUESTION_B);
        jdbc.update("INSERT INTO homework_question (id, homework_id, question_id, sort_order, question_score, question_snapshot_json) "
                        + "VALUES (?, ?, NULL, 1, 5, '{}')",
                HOMEWORK_QUESTION_B, SchoolScopeFixtures.HOMEWORK_B);
        jdbc.update("DELETE FROM t_experiment_item WHERE id = ?", LAB_ITEM_B);
        jdbc.update("INSERT INTO t_experiment_item (id, experiment_id, experiment_item_no, experiment_item_name, experiment_item_content, experiment_item_score, question_type, question_snapshot_json) "
                        + "VALUES (?, ?, 1, 'B校题项', '内容', 5, 'SINGLE', '{}')",
                LAB_ITEM_B, SchoolScopeFixtures.LAB_B);

        jdbc.update("DELETE FROM t_experiment WHERE experiment_id = ?", LAB_A);
        jdbc.update("INSERT INTO t_experiment (experiment_id, experiment_no, experiment_name, status, class_id, created_by, school_id) "
                        + "VALUES (?, 9101, 'A校实验', 'PUBLISHED', ?, 1, 1)",
                LAB_A, classA);
        jdbc.update("DELETE FROM homework WHERE id = ?", HOMEWORK_A);
        jdbc.update("INSERT INTO homework (id, title, class_id, created_by, status, school_id) "
                        + "VALUES (?, 'A校作业', ?, 1, 'PUBLISHED', 1)",
                HOMEWORK_A, classA);
        jdbc.update("DELETE FROM question_bank WHERE id = ?", QUESTION_A);
        jdbc.update("INSERT INTO question_bank (id, code, type, stem, difficulty, created_by, school_id) "
                        + "VALUES (?, 'QB-A-9104', 'SINGLE', 'A校题目', 'EASY', 1, 1)",
                QUESTION_A);
        jdbc.update("DELETE FROM course_material WHERE id = ?", MATERIAL_A);
        jdbc.update("INSERT INTO course_material (id, title, category, file_path, file_name, uploader_user_id, visibility, school_id) "
                        + "VALUES (?, 'A校资料', 'COURSE', 'test/a.txt', 'a.txt', 1, 'ALL', 1)",
                MATERIAL_A);
    }

    @Test
    void ownedLabCrossSchoolReturns40400() {
        assertNotFound(() -> labServiceSupport.ownedLab(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), SchoolScopeFixtures.LAB_B), "实验不存在");
    }

    @Test
    void ownedHomeworkCrossSchoolReturns40400() {
        assertNotFound(() -> homeworkServiceSupport.ownedHomework(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), SchoolScopeFixtures.HOMEWORK_B), "作业不存在");
    }

    @Test
    void questionUpdateAndDeleteCrossSchoolReturn40400() {
        SaveQuestionRequest request = new SaveQuestionRequest(
                "QB-A-9104", "SINGLE", "A校题目", "EASY", null, "[\"A\"]", null);

        assertNotFound(() -> questionService.update(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), SchoolScopeFixtures.QUESTION_B, request), "题库题目不存在");
        assertNotFound(() -> questionService.delete(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), SchoolScopeFixtures.QUESTION_B), "题库题目不存在");
    }

    @Test
    void copyFromHomeworkAndLabStepCrossSchoolReturn40400() {
        assertNotFound(() -> questionBankCopyService.copyFromHomework(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), HOMEWORK_QUESTION_B, null), "作业不存在");
        assertNotFound(() -> questionBankCopyService.copyFromLabStep(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), LAB_ITEM_B, null), "实验不存在");
    }

    @Test
    void copyLabCrossSchoolReturns40400() {
        assertNotFound(() -> labConfigService.copyLab(
                        teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), SchoolScopeFixtures.LAB_B,
                        new LabRequests.CopyLabRequest(classA, "副本", "DRAFT")),
                "实验不存在");
    }

    @Test
    void materialDownloadAndManageCrossSchoolReturn40400() {
        UpdateMaterialRequest request = new UpdateMaterialRequest();
        request.setTitle("改名");

        assertNotFound(() -> materialService.downloadMaterial(
                student(SchoolScopeFixtures.STUDENT_ID, SchoolScopeFixtures.SCHOOL_A), MATERIAL_B), "资料不存在");
        assertNotFound(() -> materialService.updateMaterial(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), MATERIAL_B, request), "资料不存在");
    }

    @Test
    void sameSchoolForeignOwnerStillReturns40300() {
        UpdateMaterialRequest materialRequest = new UpdateMaterialRequest();
        materialRequest.setTitle("改名");

        assertForbidden(() -> labServiceSupport.ownedLab(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), LAB_A), "无权限访问该实验");
        assertForbidden(() -> homeworkServiceSupport.ownedHomework(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), HOMEWORK_A), "无权限访问该作业");
        assertForbidden(() -> questionService.delete(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), QUESTION_A), "无权限访问该题目");
        assertForbidden(() -> materialService.updateMaterial(
                teacher(FOREIGN_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A), MATERIAL_A, materialRequest), "无权限访问该资料");
    }

    @Test
    void enablePlatformSubjectForOtherSchoolReturns40400() {
        jdbc.update("DELETE FROM school_subject WHERE school_id = ? AND code = ?",
                SchoolScopeFixtures.SCHOOL_B, "B-SUBJECT");
        jdbc.update("INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at) "
                        + "VALUES (?, 'B校学科', 'B-SUBJECT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SchoolScopeFixtures.SCHOOL_B);
        Long bSubjectId = jdbc.queryForObject(
                "SELECT id FROM school_subject WHERE school_id = ? AND code = 'B-SUBJECT'",
                Long.class, SchoolScopeFixtures.SCHOOL_B);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subject WHERE id = ?", Integer.class,
                bSubjectId)).isZero();
        Integer rowsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_subject WHERE school_id = ?", Integer.class,
                SchoolScopeFixtures.SCHOOL_B);

        CurrentUser schoolAdmin = new CurrentUser(1L, "admin9001", "校管",
                UserRole.SCHOOL_ADMIN, SchoolScopeFixtures.SCHOOL_A, "session-cross-school");
        assertNotFound(() -> adminSubjectService.enablePlatformSubject(schoolAdmin, bSubjectId),
                "学科不存在");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_subject WHERE school_id = ?", Integer.class,
                SchoolScopeFixtures.SCHOOL_B)).isEqualTo(rowsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_subject WHERE school_id = ? AND global_subject_id IS NOT NULL",
                Integer.class, SchoolScopeFixtures.SCHOOL_B)).isZero();
    }

    private void assertNotFound(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(40400);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }

    private void assertForbidden(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(40300);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }

    private CurrentUser teacher(Long id, Long schoolId) {
        return new CurrentUser(id, "t" + id, "教师" + id, UserRole.TEACHER, schoolId, "session-" + id);
    }

    private CurrentUser student(Long id, Long schoolId) {
        return new CurrentUser(id, "s" + id, "学生" + id, UserRole.STUDENT, schoolId, "session-s" + id);
    }
}
