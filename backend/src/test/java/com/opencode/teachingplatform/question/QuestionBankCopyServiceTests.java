package com.opencode.teachingplatform.question;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.entity.HomeworkQuestion;
import com.opencode.teachingplatform.homework.repository.HomeworkQuestionRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import com.opencode.teachingplatform.lab.entity.Lab;
import com.opencode.teachingplatform.lab.entity.LabStep;
import com.opencode.teachingplatform.lab.repository.LabRepository;
import com.opencode.teachingplatform.lab.repository.LabStepRepository;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.question.service.QuestionBankCopyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 作业内联题 / 实验题项复制到题库的服务用例。
 *
 * <p>重点验证幂等来源键、编码冲突、内容重复提示与实验题型映射口径。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuestionBankCopyServiceTests {

    private static final CurrentUser TEACHER = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

    @Autowired
    private QuestionBankCopyService questionBankCopyService;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private HomeworkQuestionRepository homeworkQuestionRepository;

    @Autowired
    private LabRepository labRepository;

    @Autowired
    private LabStepRepository labStepRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Test
    void homeworkCopyCreatesQuestionAndIsIdempotent() {
        HomeworkQuestion question = saveInlineHomeworkQuestion("复制题干", "[]");

        Map<String, Object> first = questionBankCopyService.copyFromHomework(TEACHER, question.getId(), null);
        Map<String, Object> second = questionBankCopyService.copyFromHomework(TEACHER, question.getId(), null);

        assertThat(first.get("created")).isEqualTo(true);
        Map<String, Object> view = asMap(first.get("question"));
        assertThat(view.get("sourceType")).isEqualTo("HOMEWORK_INLINE");
        assertThat(view.get("sourceRef")).isEqualTo("HOMEWORK:" + question.getId());
        assertThat(view.get("code")).isEqualTo("HW" + question.getId());
        assertThat(view.get("type")).isEqualTo("SINGLE");
        assertThat(view).doesNotContainKey("defaultScore");

        assertThat(second.get("created")).isEqualTo(false);
        assertThat(asMap(second.get("question")).get("id")).isEqualTo(view.get("id"));
        assertThat(questionBankRepository.findBySourceRefAndSchoolId("HOMEWORK:" + question.getId(), 1L)).isPresent();
    }

    @Test
    void homeworkCopyRejectsDuplicateCode() {
        QuestionBank existing = new QuestionBank();
        existing.setSchoolId(1L);
        existing.setCode("DUP-CODE-001");
        existing.setType("SHORT");
        existing.setStem("已有题目");
        existing.setDifficulty("MEDIUM");
        existing.setAnswerJson("[\"参考\"]");
        existing.setCreatedBy(1L);
        questionBankRepository.save(existing);

        HomeworkQuestion question = saveInlineHomeworkQuestion("编码冲突题干", "[]");

        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () ->
                questionBankCopyService.copyFromHomework(TEACHER, question.getId(), "DUP-CODE-001"));
        assertThat(ex.getCode()).isEqualTo(40900);
        assertThat(ex.getMessage()).contains("题目编码已存在");
    }

    @Test
    void homeworkCopyReturnsDuplicateQuestionHintForSameContent() {
        HomeworkQuestion firstQuestion = saveInlineHomeworkQuestion("内容重复题干", "[]");
        HomeworkQuestion secondQuestion = saveInlineHomeworkQuestion("内容重复题干", "[]");

        Map<String, Object> first = questionBankCopyService.copyFromHomework(TEACHER, firstQuestion.getId(), null);
        Map<String, Object> second = questionBankCopyService.copyFromHomework(TEACHER, secondQuestion.getId(), null);

        assertThat(second.get("created")).isEqualTo(true);
        assertThat(second.get("duplicateQuestionId")).isEqualTo(asMap(first.get("question")).get("id"));
    }

    @Test
    void labTrueFalseStepMapsToJudgeQuestion() {
        LabStep step = saveLabStep("TRUE_FALSE", "{\"correctAnswer\":[\"TRUE\"]}", "TEXT");

        Map<String, Object> result = questionBankCopyService.copyFromLabStep(TEACHER, step.getId(), null);
        Map<String, Object> view = asMap(result.get("question"));

        assertThat(result.get("created")).isEqualTo(true);
        assertThat(view.get("type")).isEqualTo("JUDGE");
        assertThat(view.get("sourceType")).isEqualTo("LAB_STEP");
        assertThat(view.get("sourceRef")).isEqualTo("LAB:" + step.getLabId() + ":" + step.getId());
        assertThat(String.valueOf(view.get("answerJson"))).contains("T");
        assertThat(view).doesNotContainKey("defaultScore");
    }

    @Test
    void labCodeStepMapsToShortQuestionWithLanguageConfig() {
        LabStep step = saveLabStep("CODE", "{\"rubric\":\"参考实现\"}", "JAVA");

        Map<String, Object> result = questionBankCopyService.copyFromLabStep(TEACHER, step.getId(), null);
        Map<String, Object> view = asMap(result.get("question"));

        assertThat(view.get("type")).isEqualTo("SHORT");
        assertThat(String.valueOf(view.get("scoringConfigJson"))).contains("JAVA");
    }

    @Test
    void sameContentInDifferentLabsCopiesToIndependentQuestions() {
        LabStep firstStep = saveLabStep("TRUE_FALSE", "{\"correctAnswer\":[\"TRUE\"]}", "TEXT");
        LabStep secondStep = saveLabStep("TRUE_FALSE", "{\"correctAnswer\":[\"TRUE\"]}", "TEXT");

        Map<String, Object> first = questionBankCopyService.copyFromLabStep(TEACHER, firstStep.getId(), null);
        Map<String, Object> second = questionBankCopyService.copyFromLabStep(TEACHER, secondStep.getId(), null);

        Map<String, Object> firstView = asMap(first.get("question"));
        Map<String, Object> secondView = asMap(second.get("question"));
        // 来源键按实验与题项区分：两个实验的同内容题各自生成独立题目，仅给重复提示
        assertThat(first.get("created")).isEqualTo(true);
        assertThat(second.get("created")).isEqualTo(true);
        assertThat(secondView.get("id")).isNotEqualTo(firstView.get("id"));
        assertThat(firstView.get("sourceRef")).isEqualTo("LAB:" + firstStep.getLabId() + ":" + firstStep.getId());
        assertThat(secondView.get("sourceRef")).isEqualTo("LAB:" + secondStep.getLabId() + ":" + secondStep.getId());
        assertThat(second.get("duplicateQuestionId")).isEqualTo(firstView.get("id"));
    }

    @Test
    void unsupportedLabStepTypeIsRejected() {
        LabStep step = saveLabStep("ESSAY", "{}", "TEXT");

        assertThatThrownBy(() -> questionBankCopyService.copyFromLabStep(TEACHER, step.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持复制到题库");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private HomeworkQuestion saveInlineHomeworkQuestion(String stem, String analysisText) {
        Homework homework = new Homework();
        homework.setSchoolId(1L);
        homework.setTitle("复制来源作业");
        homework.setDescription("来源作业");
        homework.setClassId(1L);
        homework.setCreatedBy(1L);
        homework.setStatus(ActivityStatus.DRAFT);
        Homework savedHomework = homeworkRepository.save(homework);

        HomeworkQuestion question = new HomeworkQuestion();
        question.setHomeworkId(savedHomework.getId());
        question.setSortOrder(1);
        question.setQuestionScore(5D);
        question.setQuestionSnapshotJson("""
                {"type":"SINGLE","stem":"%s","questionScore":5,
                 "options":[{"key":"A","content":"正确"},{"key":"B","content":"错误"}],
                 "answer":["A"],"scoringConfig":{},"analysisText":"%s","difficulty":"EASY","fromBank":false}
                """.formatted(stem, analysisText));
        return homeworkQuestionRepository.save(question);
    }

    private LabStep saveLabStep(String questionType, String answerConfigJson, String editorLanguage) {
        Lab lab = new Lab();
        lab.setSchoolId(1L);
        lab.setTitle("复制来源实验");
        lab.setDescription("来源实验");
        lab.setClassId(1L);
        lab.setCreatedBy(1L);
        lab.setStatus(ActivityStatus.DRAFT);
        Lab savedLab = labRepository.save(lab);

        LabStep step = new LabStep();
        step.setLabId(savedLab.getId());
        step.setStepNo(1);
        step.setTitle("实验题项");
        step.setQuestionType(questionType);
        step.setContent("实验题项题干");
        step.setAnswerConfigJson(answerConfigJson);
        step.setStepScore(5);
        step.setEditorLanguage(editorLanguage);
        return labStepRepository.save(step);
    }
}
