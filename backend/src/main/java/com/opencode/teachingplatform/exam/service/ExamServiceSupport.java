package com.opencode.teachingplatform.exam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.model.GradingResultView;
import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.assessment.service.AnswerPayloadNormalizer;
import com.opencode.teachingplatform.assessment.service.GradingAdapter;
import com.opencode.teachingplatform.assessment.service.QuestionSnapshotFactory;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.exam.entity.Exam;
import com.opencode.teachingplatform.exam.entity.ExamAnswer;
import com.opencode.teachingplatform.exam.entity.ExamQuestion;
import com.opencode.teachingplatform.exam.entity.ExamSubmission;
import com.opencode.teachingplatform.exam.enums.ExamSubmissionStatus;
import com.opencode.teachingplatform.exam.repository.ExamAnswerRepository;
import com.opencode.teachingplatform.exam.repository.ExamQuestionRepository;
import com.opencode.teachingplatform.exam.repository.ExamRepository;
import com.opencode.teachingplatform.exam.repository.ExamSubmissionRepository;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ExamServiceSupport {

    public static final long SUBMIT_GRACE_SECONDS = 120L;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassAccessService classAccessService;
    private final ExamScoringService examScoringService;
    private final SysUserRepository sysUserRepository;
    private final ScoreRecordWriter scoreRecordWriter;
    private final TransactionTemplate transactionTemplate;
    private final GradingAdapter gradingAdapter;
    private final ObjectMapper objectMapper;

    public ExamServiceSupport(ExamRepository examRepository,
                       ExamQuestionRepository examQuestionRepository,
                       ExamSubmissionRepository examSubmissionRepository,
                       ExamAnswerRepository examAnswerRepository,
                       QuestionBankRepository questionBankRepository,
                       ClassMemberRepository classMemberRepository,
                       ClassRoomRepository classRoomRepository,
                       ClassAccessService classAccessService,
                       ExamScoringService examScoringService,
                       SysUserRepository sysUserRepository,
                       ScoreRecordWriter scoreRecordWriter,
                       PlatformTransactionManager transactionManager,
                       ObjectProvider<GradingAdapter> gradingAdapterProvider,
                       ObjectProvider<ScoringEngine> scoringEngineProvider,
                       ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.examSubmissionRepository = examSubmissionRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.questionBankRepository = questionBankRepository;
        this.classMemberRepository = classMemberRepository;
        this.classRoomRepository = classRoomRepository;
        this.classAccessService = classAccessService;
        this.examScoringService = examScoringService;
        this.sysUserRepository = sysUserRepository;
        this.scoreRecordWriter = scoreRecordWriter;
        // 开始考试需要“创建提交”独立事务：唯一键冲突必须在外层捕获后再重读，
        // 因此这里用事务模板实现，不新增 Bean 与独立类。
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.gradingAdapter = gradingAdapterProvider.getIfAvailable(() -> new GradingAdapter(scoringEngineProvider.getIfAvailable(), this.objectMapper));
    }


    public QuestionSnapshot resolveQuestionSnapshot(ExamQuestion examQuestion, Long schoolId) {
        if (examQuestion.getQuestionSnapshotJson() != null && !examQuestion.getQuestionSnapshotJson().isBlank()) {
            JsonNode snapshot = readJsonNode(examQuestion.getQuestionSnapshotJson());
            String questionType = jsonText(snapshot, "questionType");
            String stem = jsonText(snapshot, "stem");
            if (questionType != null && stem != null) {
                return new QuestionSnapshot(
                        jsonLong(snapshot, "questionBankId", examQuestion.getQuestionId()),
                        jsonText(snapshot, "sourceType", "BANK"),
                        questionType,
                        stem,
                        jsonInteger(snapshot, "sortOrder", examQuestion.getSortOrder()),
                        jsonDouble(snapshot, "score", examQuestion.getQuestionScore()),
                        jsonField(snapshot, "optionsJson"),
                        jsonField(snapshot, "answerJson"),
                        jsonField(snapshot, "scoringConfigJson"),
                        jsonBoolean(snapshot, "reusableFromBank", Boolean.TRUE)
                );
            }
        }

        QuestionBank questionBank = questionBankRepository.findByIdAndSchoolId(examQuestion.getQuestionId(), schoolId)
                .orElseThrow(() -> new BusinessException(40400, "题库题目不存在"));
        return toSnapshot(questionBank, examQuestion.getSortOrder(), examQuestion.getQuestionScore());
    }



    public Map<String, Object> readJsonMap(String json) {
        JsonNode node = readJsonNode(json);
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }



    public JsonNode readJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode parsed = parseJsonNode(json);
        if (parsed != null) {
            return parsed;
        }
        String normalized = normalizeJsonLiteral(json);
        if (!normalized.equals(json)) {
            return parseJsonNode(normalized);
        }
        return null;
    }



    public JsonNode parseJsonNode(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            while (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.textValue());
            }
            return node;
        } catch (Exception ex) {
            return null;
        }
    }



    public String normalizeJsonLiteral(String json) {
        String normalized = json.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.contains("\\\"")) {
            normalized = normalized.replace("\\\"", "\"");
        }
        if (normalized.contains("\"\"")) {
            normalized = normalized.replace("\"\"", "\"");
        }
        return normalized;
    }



    public List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }



    public boolean isFinal(ExamSubmission submission) {
        return submission != null && submission.getStatus() == ExamSubmissionStatus.GRADED;
    }



    public boolean canStudentSeeScore(Exam exam, ExamSubmission submission) {
        // 只有最终成绩（GRADED）才可能对学生公开；未批完一律不可见。
        if (!isFinal(submission)) {
            return false;
        }
        ScoreVisibilityMode mode = exam.getScoreVisibilityMode() == null
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : exam.getScoreVisibilityMode();
        return switch (mode) {
            case IMMEDIATE, AFTER_TEACHER_CONFIRM -> true;
            case AFTER_DEADLINE -> exam.getEndAt() != null
                    && !OffsetDateTime.now().isBefore(exam.getEndAt());
            case MANUAL_RELEASE -> exam.isScoreReleased();
        };
    }



    public void upsertScoreRecord(Exam exam, ExamSubmission submission, double totalScore) {
        scoreRecordWriter.write(BusinessType.EXAM, exam.getId(), submission.getStudentId(),
                exam.getClassId(), totalScore, OffsetDateTime.now(), exam.getSchoolId());
    }



    public QuestionSnapshot toSnapshot(QuestionBank questionBank, Integer sortOrder, Double score) {
        return QuestionSnapshotFactory.fromBankQuestion(
                questionBank.getId(),
                questionBank.getType(),
                questionBank.getStem(),
                questionBank.getOptionsJson(),
                questionBank.getAnswerJson(),
                questionBank.getScoringConfigJson(),
                sortOrder,
                score
        );
    }



    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(50000, "JSON 序列化失败");
        }
    }



    public String mapString(Object value) {
        return mapString(value, null);
    }



    public String mapString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }



    public Long mapLong(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }



    public Integer mapInteger(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }



    public Double mapDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }



    public Boolean mapBoolean(Object value, Boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }



    public String mapJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return writeJson(value);
    }



    public String jsonText(JsonNode node, String fieldName) {
        return jsonText(node, fieldName, null);
    }



    public String jsonText(JsonNode node, String fieldName, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        String text = field.isTextual() ? field.textValue() : field.asText();
        return text == null || text.isBlank() ? fallback : text;
    }



    public Long jsonLong(JsonNode node, String fieldName, Long fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isNumber()) {
            return field.longValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }



    public Integer jsonInteger(JsonNode node, String fieldName, Integer fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isNumber()) {
            return field.intValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }



    public Double jsonDouble(JsonNode node, String fieldName, Double fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isNumber()) {
            return field.doubleValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }



    public Boolean jsonBoolean(JsonNode node, String fieldName, Boolean fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isBoolean()) {
            return field.booleanValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(text);
    }



    public String jsonField(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (field.isTextual()) {
            return field.textValue();
        }
        return field.toString();
    }

}
