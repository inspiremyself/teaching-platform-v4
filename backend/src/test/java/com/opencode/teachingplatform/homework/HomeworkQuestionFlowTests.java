package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.homework.dto.HomeworkRequests;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.entity.HomeworkAnswer;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.homework.entity.HomeworkQuestion;
import com.opencode.teachingplatform.homework.entity.HomeworkSubmission;
import com.opencode.teachingplatform.homework.repository.HomeworkAnswerRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkQuestionRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkSubmissionRepository;
import com.opencode.teachingplatform.homework.service.HomeworkService;
import com.opencode.teachingplatform.homework.service.HomeworkSubmissionClaimService;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HomeworkQuestionFlowTests {

    private static final CurrentUser TEACHER = new CurrentUser(1L, "t9001", "张老师", UserRole.TEACHER);
    private static final CurrentUser STUDENT = new CurrentUser(2L, "20260001", "李学生", UserRole.STUDENT);

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private HomeworkQuestionRepository homeworkQuestionRepository;

    @Autowired
    private HomeworkAnswerRepository homeworkAnswerRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private HomeworkSubmissionRepository homeworkSubmissionRepository;

    /**
     * 首存 claim 走 REQUIRES_NEW 独立连接，在 @Transactional 测试里看不到未提交的作业种子数据，
     * 会触发 homework_submission 的外键冲突；此处用委托桩替代，语义与真实实现一致（无则建 SAVED 行）。
     */
    @MockBean
    private HomeworkSubmissionClaimService homeworkSubmissionClaimService;

    @BeforeEach
    void stubSubmissionClaim() {
        given(homeworkSubmissionClaimService.claim(anyLong(), anyLong()))
                .willAnswer(invocation -> {
                    Long homeworkId = invocation.getArgument(0);
                    Long studentId = invocation.getArgument(1);
                    return homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homeworkId, studentId)
                            .map(HomeworkSubmission::getSubmitStatus)
                            .orElseGet(() -> {
                                HomeworkSubmission created = new HomeworkSubmission();
                                created.setHomeworkId(homeworkId);
                                created.setStudentId(studentId);
                                created.setSubmitStatus(SubmissionStatus.SAVED);
                                created.setPlagiarismRate(0D);
                                created.setTotalScore(0D);
                                homeworkSubmissionRepository.saveAndFlush(created);
                                return SubmissionStatus.SAVED;
                            });
                });
    }

    @Test
    void teacherShouldBeAbleToAttachQuestionBankQuestionsAndInlineQuestions() {
        Homework homework = saveHomework(ActivityStatus.DRAFT);
        QuestionBank bankQuestion = saveBankQuestion();

        homeworkService.addQuestionFromBank(TEACHER, homework.getId(),
                new HomeworkRequests.AddHomeworkQuestionFromBankRequest(bankQuestion.getId(), 1, 10D));

        homeworkService.addInlineQuestion(TEACHER, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE",
                        "请说明策略模式的价值",
                        15D,
                        null,
                        "[\"参考要点\"]",
                        null,
                        "{\"keywords\":[{\"term\":\"strategy\",\"weight\":8}],\"minLength\":6}",
                        2
                ));

        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homework.getId());

        assertThat(questions).hasSize(2);
        assertThat(questions.get(0).getQuestionId()).isEqualTo(bankQuestion.getId());
        assertThat(questions.get(1).getQuestionId()).isNull();
        assertThat(questions.get(1).getQuestionSnapshotJson()).contains("策略模式的价值");
    }

    @Test
    void studentSubmissionShouldCreatePerQuestionAnswersAndRecommendationScores() {
        Homework homework = saveHomework(ActivityStatus.DRAFT);
        QuestionBank bankQuestion = saveBankQuestion();

        Map<String, Object> objectiveQuestion = homeworkService.addQuestionFromBank(TEACHER, homework.getId(),
                new HomeworkRequests.AddHomeworkQuestionFromBankRequest(bankQuestion.getId(), 1, 10D));

        Map<String, Object> subjectiveQuestion = homeworkService.addInlineQuestion(TEACHER, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE",
                        "请说明 strategy 扩展与复用能力",
                        15D,
                        null,
                        "[\"参考要点\"]",
                        null,
                        "{\"keywords\":[{\"term\":\"strategy\",\"weight\":8},{\"term\":\"复用\",\"weight\":4}],\"minLength\":6}",
                        2
                ));

        // 题目只能在草稿状态下编辑，作答前把作业切到已发布状态
        homework.setStatus(ActivityStatus.PUBLISHED);
        homeworkRepository.save(homework);

        homeworkService.submitHomework(STUDENT, homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest(
                        List.of(
                                new HomeworkRequests.SubmitHomeworkAnswerItem((Long) objectiveQuestion.get("id"), List.of("A"), null, null, null),
                                new HomeworkRequests.SubmitHomeworkAnswerItem((Long) subjectiveQuestion.get("id"), null, "strategy 设计提升复用", null, null)
                        )
                ));

        List<HomeworkAnswer> answers = homeworkAnswerRepository.findAll();

        assertThat(answers).hasSize(2);
        HomeworkAnswer objective = answers.stream().filter(item -> item.getAutoScore() != null).findFirst().orElseThrow();
        HomeworkAnswer subjective = answers.stream().filter(item -> item.getSuggestedScore() != null).findFirst().orElseThrow();

        assertThat(objective.getScore()).isEqualTo(10D);
        assertThat(subjective.getSuggestedScore()).isEqualTo(12D);
        assertThat(subjective.getScore()).isNull();
        assertThat(subjective.getJudgeDetail()).contains("strategy");
    }

    @Test
    void repeatedSavesOfSameInlineQuestionDoNotAppendRows() {
        Homework homework = saveHomework(ActivityStatus.DRAFT);

        Map<String, Object> created = homeworkService.addInlineQuestion(TEACHER, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE",
                        "请说明策略模式的价值",
                        15D,
                        null,
                        "[\"参考要点\"]",
                        null,
                        null,
                        1
                ));
        Long homeworkQuestionId = (Long) created.get("id");

        // 同一题连续保存（新增 1 次 + 更新 2 次）后题目数量必须保持不变
        for (int round = 1; round <= 2; round++) {
            homeworkService.updateQuestion(TEACHER, homework.getId(), homeworkQuestionId,
                    new HomeworkRequests.SaveHomeworkQuestionRequest(
                            "SUBJECTIVE",
                            "请说明策略模式的价值（第 " + round + " 次修改）",
                            15D,
                            null,
                            "[\"参考要点\"]",
                            null,
                            null,
                            1
                    ));
        }

        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homework.getId());
        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().getQuestionSnapshotJson()).contains("第 2 次修改");
    }

    private Homework saveHomework(ActivityStatus status) {
        Homework homework = new Homework();
        homework.setSchoolId(1L);
        homework.setTitle("评分系统作业");
        homework.setDescription("题目化作业");
        homework.setClassId(1L);
        homework.setCreatedBy(1L);
        homework.setStatus(status);
        homework.setStartAt(OffsetDateTime.now().minusHours(1));
        homework.setDueAt(OffsetDateTime.now().plusDays(1));
        return homeworkRepository.save(homework);
    }

    private QuestionBank saveBankQuestion() {
        QuestionBank question = new QuestionBank();
        question.setSchoolId(1L);
        question.setCode("QB-HW-001");
        question.setType("SINGLE_CHOICE");
        question.setStem("策略模式的核心目的是什么？");
        question.setDifficulty("MEDIUM");
        question.setDefaultScore(10);
        question.setOptionsJson("[{\"label\":\"A\",\"content\":\"封装可替换算法\"}]");
        question.setAnswerJson("{\"correctAnswer\":\"A\"}");
        question.setScoringConfigJson("{\"correctAnswer\":\"A\"}");
        question.setCreatedBy(1L);
        return questionBankRepository.save(question);
    }
}
