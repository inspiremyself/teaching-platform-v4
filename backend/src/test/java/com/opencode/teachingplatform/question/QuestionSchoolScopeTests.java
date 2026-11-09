package com.opencode.teachingplatform.question;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.question.dto.QuestionRequests;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.question.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 题库按校隔离、全局编码唯一与账号未绑定学校的 40000 回归。 */
@SpringBootTest
@ActiveProfiles("test")
class QuestionSchoolScopeTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
        jdbc.update("DELETE FROM question_bank WHERE code LIKE 'QB-SCOPE-%'");
    }

    @Test
    void teacherOnlySeesOwnSchoolQuestions() {
        assertThat(questionBankRepository.findBySchoolIdAndCreatedByOrderByIdDesc(
                SchoolScopeFixtures.SCHOOL_A, SchoolScopeFixtures.TEACHER_ID))
                .extracting(QuestionBank::getId).doesNotContain(SchoolScopeFixtures.QUESTION_B);
        assertThat(questionBankRepository.findBySchoolIdAndCreatedByOrderByIdDesc(
                SchoolScopeFixtures.SCHOOL_B, SchoolScopeFixtures.TEACHER_ID))
                .extracting(QuestionBank::getId).contains(SchoolScopeFixtures.QUESTION_B);
    }

    @Test
    void crossSchoolSameCodeStillReturns40900() {
        QuestionRequests.SaveQuestionRequest request = request("QB-SCOPE-SAME");
        questionService.create(teacher(SchoolScopeFixtures.SCHOOL_A), request);

        assertThatThrownBy(() -> questionService.create(teacher(SchoolScopeFixtures.SCHOOL_B), request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(40900);
                    assertThat(ex.getMessage()).isEqualTo("题目编码已存在");
                });
    }

    @Test
    void nullSchoolIdRejectsCreateWith40000() {
        CurrentUser unbound = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, null, "scope-session");

        assertThatThrownBy(() -> questionService.create(unbound, request("QB-SCOPE-NULL")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(40000);
                    assertThat(ex.getMessage()).isEqualTo("账号未绑定学校，无法创建教学数据");
                });
    }

    private static CurrentUser teacher(Long schoolId) {
        return new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, schoolId, "scope-session");
    }

    private static QuestionRequests.SaveQuestionRequest request(String code) {
        return new QuestionRequests.SaveQuestionRequest(
                code, "SINGLE", "跨校回归题干", "MEDIUM", 5, "[\"A\",\"B\"]", "[\"A\"]", "解析");
    }
}
