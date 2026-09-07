package com.opencode.teachingplatform.question;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        questionBankRepository.deleteAll();
    }

    @Test
    void teacherCanCreateQuestionWithStructuredPayload() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Q-NEW-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("Q-NEW-001"))
                .andExpect(jsonPath("$.data.type").value("SINGLE"))
                .andExpect(jsonPath("$.data.stem").value("统一返回结构的主要作用是什么？"))
                .andExpect(jsonPath("$.data.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.data.defaultScore").value(5));

        List<QuestionBank> questions = questionBankRepository.findAll();
        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().getCode()).isEqualTo("Q-NEW-001");
        assertThat(questions.getFirst().getAnswerJson()).isNotBlank();
        assertThat(questions.getFirst().getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void duplicateQuestionCodeIsRejected() throws Exception {
        QuestionBank existing = new QuestionBank();
        existing.setCode("Q-DUP-001");
        existing.setCreatedBy(1L);
        existing.setType("SINGLE");
        existing.setStem("已有题目");
        existing.setDifficulty("EASY");
        existing.setDefaultScore(3);
        existing.setOptionsJson("[\"A\",\"B\"]");
        existing.setAnswerJson("[\"A\"]");
        existing.setAnalysisText("已有解析");
        questionBankRepository.save(existing);

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Q-DUP-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void studentCannotAccessQuestionBankEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/questions")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void canListSeededShortQuestionWhenOptionsJsonIsNull() throws Exception {
        QuestionBank shortQuestion = new QuestionBank();
        shortQuestion.setCode("Q-FOUND-003");
        shortQuestion.setCreatedBy(1L);
        shortQuestion.setType("SHORT");
        shortQuestion.setStem("简述接口统一返回结构的意义。");
        shortQuestion.setDifficulty("MEDIUM");
        shortQuestion.setDefaultScore(10);
        shortQuestion.setOptionsJson(null);
        shortQuestion.setAnswerJson("[\"统一错误处理与前端解析\"]");
        shortQuestion.setAnalysisText("便于一致处理");
        questionBankRepository.save(shortQuestion);

        mockMvc.perform(get("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.code=='Q-FOUND-003')].type").value("SHORT"))
                .andExpect(jsonPath("$.data[?(@.code=='Q-FOUND-003')].optionsJson").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.data[?(@.code=='Q-FOUND-003')].createdBy").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void teacherOnlySeesOwnQuestions() throws Exception {
        QuestionBank owned = new QuestionBank();
        owned.setCode("Q-OWN-001");
        owned.setCreatedBy(1L);
        owned.setType("SINGLE");
        owned.setStem("教师自己的题目");
        owned.setDifficulty("EASY");
        owned.setDefaultScore(5);
        owned.setOptionsJson("[\"A\",\"B\"]");
        owned.setAnswerJson("[\"A\"]");
        owned.setAnalysisText("own");
        questionBankRepository.save(owned);

        QuestionBank foreign = new QuestionBank();
        foreign.setCode("Q-FOREIGN-001");
        foreign.setCreatedBy(9L);
        foreign.setType("SINGLE");
        foreign.setStem("其他教师题目");
        foreign.setDifficulty("EASY");
        foreign.setDefaultScore(5);
        foreign.setOptionsJson("[\"A\",\"B\"]");
        foreign.setAnswerJson("[\"A\"]");
        foreign.setAnalysisText("foreign");
        questionBankRepository.save(foreign);

        mockMvc.perform(get("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("Q-OWN-001"));
    }

    @Test
    void teacherCannotDeleteForeignQuestion() throws Exception {
        QuestionBank foreign = new QuestionBank();
        foreign.setCode("Q-FOREIGN-DELETE");
        foreign.setCreatedBy(9L);
        foreign.setType("SINGLE");
        foreign.setStem("其他教师题目");
        foreign.setDifficulty("EASY");
        foreign.setDefaultScore(5);
        foreign.setOptionsJson("[\"A\",\"B\"]");
        foreign.setAnswerJson("[\"A\"]");
        foreign.setAnalysisText("foreign");
        QuestionBank saved = questionBankRepository.save(foreign);

        mockMvc.perform(delete("/api/v1/questions/{id}", saved.getId())
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void shortQuestionCreateAndUpdateAllowNullOptionsJson() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortPayload("Q-SHORT-001", "简述分层架构的作用。", "[\"职责分离\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("Q-SHORT-001"));

        Long id = questionBankRepository.findAll().stream()
                .filter(question -> "Q-SHORT-001".equals(question.getCode()))
                .map(QuestionBank::getId)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(put("/api/v1/questions/{id}", id)
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortPayload("Q-SHORT-001", "简述分层架构更新后的作用。", "[\"职责更清晰\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("SHORT"));
    }

    @Test
    void invalidQuestionJsonPayloadIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "code": "Q-BAD-001",
                                  "type": "SINGLE",
                                  "stem": "无效 JSON 测试",
                                  "difficulty": "MEDIUM",
                                  "defaultScore": 5,
                                  "optionsJson": "[\"A\",]",
                                  "answerJson": "[\"A\"]",
                                  "analysisText": "非法 JSON"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求体 JSON 格式不正确"));
    }

    @Test
    void invalidEmbeddedOptionsJsonIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "Q-BAD-EMBED-001",
                                  "type": "SINGLE",
                                  "stem": "嵌入式 JSON 非法测试",
                                  "difficulty": "MEDIUM",
                                  "defaultScore": 5,
                                  "optionsJson": "[\\"A\\",]",
                                  "answerJson": "[\\"A\\"]",
                                  "analysisText": "非法嵌入 JSON"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("题目选项 JSON 格式不正确"));
    }

    @Test
    void shortQuestionTypeWithPaddingStillAllowsNullOptionsJson() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "Q-SHORT-PAD-001",
                                  "type": " SHORT ",
                                  "stem": "带空格的主观题类型",
                                  "difficulty": "MEDIUM",
                                  "defaultScore": 10,
                                  "optionsJson": null,
                                  "answerJson": "[\\"回答\\"]",
                                  "analysisText": "主观题不需要选项"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("SHORT"))
                .andExpect(jsonPath("$.data.optionsJson").isEmpty());
    }

    private String validPayload(String code) {
        return """
                {
                  "code": "%s",
                  "type": "SINGLE",
                  "stem": "统一返回结构的主要作用是什么？",
                  "difficulty": "MEDIUM",
                  "defaultScore": 5,
                  "optionsJson": "[%s,%s]",
                  "answerJson": "[%s]",
                  "analysisText": "便于前后端一致处理"
                }
                """.formatted(code, jsonQuoted("A"), jsonQuoted("B"), jsonQuoted("A"));
    }

    private String shortPayload(String code, String stem, String answerJson) {
        return """
                {
                  "code": "%s",
                  "type": "SHORT",
                  "stem": "%s",
                  "difficulty": "MEDIUM",
                  "defaultScore": 10,
                  "optionsJson": null,
                  "answerJson": "%s",
                  "analysisText": "主观题不需要选项"
                }
                """.formatted(code, stem, answerJson.replace("\"", "\\\""));
    }

    private String jsonQuoted(String value) {
        return "\\\"" + value + "\\\"";
    }

    private String bearerToken(SysUser user) {
        return IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                user.getId()
        );
    }

    private SysUser teacherUser(Long id, String username, String displayName) {
        return buildUser(id, username, displayName, UserRole.TEACHER);
    }

    private SysUser studentUser(Long id, String username, String displayName) {
        return buildUser(id, username, displayName, UserRole.STUDENT);
    }

    private SysUser buildUser(Long id, String username, String displayName, UserRole role) {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return id;
            }
        };
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("{noop}123456");
        return user;
    }
}
