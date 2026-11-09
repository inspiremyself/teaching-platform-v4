package com.opencode.teachingplatform.question.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.service.QuestionContentSupport;
import com.opencode.teachingplatform.assessment.service.QuestionContentValidator;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.infra.audit.AuditResourceType;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 作业题目 / 实验题项复制到题库的服务。
 *
 * <p>复制采用“显式触发 + 幂等来源键”模型：只有教师主动点击复制才写题库，
 * 同一来源重复复制直接返回已有题目；复制失败不会影响来源侧已保存的数据。</p>
 */
@Service
public class QuestionBankCopyService {

    private static final String SOURCE_HOMEWORK_INLINE = "HOMEWORK_INLINE";
    private static final String SOURCE_LAB_STEP = "LAB_STEP";
    private static final String HOMEWORK_SOURCE_PREFIX = "HOMEWORK:";
    private static final String LAB_SOURCE_PREFIX = "LAB:";
    private static final int CODE_MAX_LENGTH = 64;

    private final QuestionBankRepository questionBankRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkQuestionRepository homeworkQuestionRepository;
    private final LabRepository labRepository;
    private final LabStepRepository labStepRepository;
    private final ClassAccessService classAccessService;
    private final AuditLogService auditLogService;
    private final QuestionContentValidator questionContentValidator;
    private final QuestionBankViewMapper questionBankViewMapper;
    private final ObjectMapper objectMapper;

    public QuestionBankCopyService(QuestionBankRepository questionBankRepository,
                                   HomeworkRepository homeworkRepository,
                                   HomeworkQuestionRepository homeworkQuestionRepository,
                                   LabRepository labRepository,
                                   LabStepRepository labStepRepository,
                                   ClassAccessService classAccessService,
                                   AuditLogService auditLogService,
                                   QuestionContentValidator questionContentValidator,
                                   QuestionBankViewMapper questionBankViewMapper,
                                   ObjectMapper objectMapper) {
        this.questionBankRepository = questionBankRepository;
        this.homeworkRepository = homeworkRepository;
        this.homeworkQuestionRepository = homeworkQuestionRepository;
        this.labRepository = labRepository;
        this.labStepRepository = labStepRepository;
        this.classAccessService = classAccessService;
        this.auditLogService = auditLogService;
        this.questionContentValidator = questionContentValidator;
        this.questionBankViewMapper = questionBankViewMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 把作业内联题复制到教师题库。
     *
     * @param code 可选编码，不传时按 {@code HW{homeworkQuestionId}} 自动生成
     */
    @Transactional
    public Map<String, Object> copyFromHomework(CurrentUser currentUser, Long homeworkQuestionId, String code) {
        requireTeacher(currentUser);
        HomeworkQuestion question = homeworkQuestionRepository.findById(homeworkQuestionId)
                .orElseThrow(() -> new BusinessException(40400, "作业题目不存在"));
        Homework homework = homeworkRepository.findByIdAndSchoolIdAndCreatedBy(question.getHomeworkId(), currentUser.schoolId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该作业"));
        classAccessService.assertTeacherAssignedToClass(currentUser, homework.getClassId());
        if (question.getQuestionId() != null) {
            throw new BusinessException(40000, "题库题无需复制");
        }

        String sourceRef = HOMEWORK_SOURCE_PREFIX + homeworkQuestionId;
        Optional<QuestionBank> existing = questionBankRepository.findBySourceRefAndSchoolId(sourceRef, currentUser.schoolId());
        if (existing.isPresent()) {
            return buildResponse(existing.get(), false, null);
        }

        Map<String, Object> snapshot = readJsonMap(question.getQuestionSnapshotJson());
        String type = toBankQuestionType(stringValue(snapshot.get("type")));
        String stem = stringValue(snapshot.get("stem"));
        String optionsJson = hasText(snapshot.get("options")) ? writeJson(snapshot.get("options")) : null;
        Object answer = snapshot.get("answer");
        String answerJson = writeJson(answer == null ? List.of() : answer);
        String scoringConfigJson = snapshot.get("scoringConfig") instanceof Map<?, ?> config && !config.isEmpty()
                ? writeJson(config)
                : null;
        String analysisText = stringValue(snapshot.get("analysisText"));
        String difficulty = hasText(snapshot.get("difficulty")) ? stringValue(snapshot.get("difficulty")) : "MEDIUM";
        double snapshotScore = numberValue(snapshot.get("questionScore"), question.getQuestionScore());
        int defaultScore = Math.max(1, (int) Math.round(snapshotScore));

        questionContentValidator.validate(type, stem, (double) defaultScore, optionsJson, answerJson, scoringConfigJson);

        String resolvedCode = resolveCode(code, "HW" + homeworkQuestionId);
        return persistCopiedQuestion(currentUser, sourceRef, SOURCE_HOMEWORK_INLINE, resolvedCode, type, stem,
                difficulty, defaultScore, optionsJson, answerJson, scoringConfigJson, analysisText);
    }

    /**
     * 把实验题项复制到教师题库。
     *
     * @param code 可选编码，不传时按 {@code LAB{labId}S{stepNo}} 自动生成
     */
    @Transactional
    public Map<String, Object> copyFromLabStep(CurrentUser currentUser, Long stepId, String code) {
        requireTeacher(currentUser);
        LabStep step = labStepRepository.findById(stepId)
                .orElseThrow(() -> new BusinessException(40400, "实验题项不存在"));
        Lab lab = labRepository.findByIdAndSchoolIdAndCreatedBy(step.getLabId(), currentUser.schoolId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该实验"));
        classAccessService.assertTeacherAssignedToClass(currentUser, lab.getClassId());

        String sourceRef = LAB_SOURCE_PREFIX + step.getLabId() + ":" + stepId;
        Optional<QuestionBank> existing = questionBankRepository.findBySourceRefAndSchoolId(sourceRef, currentUser.schoolId());
        if (existing.isPresent()) {
            return buildResponse(existing.get(), false, null);
        }

        LabQuestionContent content = buildLabQuestionContent(step);
        int defaultScore = Math.max(1, step.getStepScore() == null ? 1 : step.getStepScore());
        questionContentValidator.validate(
                content.type(),
                content.stem(),
                (double) defaultScore,
                content.optionsJson(),
                content.answerJson(),
                content.scoringConfigJson()
        );

        String resolvedCode = resolveCode(code, "LAB" + step.getLabId() + "S" + step.getStepNo());
        return persistCopiedQuestion(currentUser, sourceRef, SOURCE_LAB_STEP, resolvedCode, content.type(),
                content.stem(), "MEDIUM", defaultScore, content.optionsJson(), content.answerJson(),
                content.scoringConfigJson(), "");
    }

    /**
     * 实验题项答案配置到题库字段的映射结果。
     */
    private record LabQuestionContent(
            String type,
            String stem,
            String optionsJson,
            String answerJson,
            String scoringConfigJson
    ) {
    }

    /**
     * 按 TD17 规则把实验题项映射为题库题型与内容。
     */
    private LabQuestionContent buildLabQuestionContent(LabStep step) {
        String rawType = step.getQuestionType() == null ? "" : step.getQuestionType().trim().toUpperCase(Locale.ROOT);
        String stem = step.getContent();
        Map<String, Object> config = readJsonMap(step.getAnswerConfigJson());
        return switch (rawType) {
            case "SINGLE_CHOICE" -> new LabQuestionContent(
                    "SINGLE", stem,
                    writeJson(normalizeLabChoiceOptions(config.get("options"))),
                    requireNonEmptyArrayJson(config.get("correctAnswer"), "单选题答案不能为空"),
                    null
            );
            case "MULTIPLE_CHOICE" -> new LabQuestionContent(
                    "MULTI", stem,
                    writeJson(normalizeLabChoiceOptions(config.get("options"))),
                    requireNonEmptyArrayJson(config.get("correctAnswer"), "多选题答案不能为空"),
                    null
            );
            case "TRUE_FALSE" -> new LabQuestionContent(
                    "JUDGE", stem,
                    writeJson(List.of(
                            Map.of("key", "T", "content", "正确"),
                            Map.of("key", "F", "content", "错误")
                    )),
                    writeJson(List.of(normalizeJudgeAnswer(config.get("correctAnswer")))),
                    null
            );
            case "FILL_BLANK" -> new LabQuestionContent(
                    "FILL", stem, null,
                    writeJson(buildLabBlanks(config.get("blanks"))),
                    null
            );
            case "SHORT_ANSWER", "TEXT" -> new LabQuestionContent(
                    "SHORT", stem, null,
                    writeJson(List.of(resolveLabTextAnswer(config, step))),
                    hasKeywords(config) ? writeJson(Map.of("keywords", config.get("keywords"))) : null
            );
            case "CODE" -> new LabQuestionContent(
                    "SHORT", stem, null,
                    writeJson(List.of(resolveLabCodeAnswer(config, step))),
                    writeJson(Map.of("language", step.getEditorLanguage() == null || step.getEditorLanguage().isBlank()
                            ? "JAVA" : step.getEditorLanguage()))
            );
            default -> throw new BusinessException(40000, "该题项类型不支持复制到题库");
        };
    }

    private List<Map<String, Object>> normalizeLabChoiceOptions(Object rawOptions) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (rawOptions instanceof List<?> list) {
            int index = 0;
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String key = stringValue(map.get("key"));
                    if (key == null || key.isBlank()) {
                        key = QuestionContentSupport.optionKeyAt(index);
                    }
                    String content = firstNonBlank(stringValue(map.get("label")), stringValue(map.get("content")), "");
                    options.add(Map.of("key", key, "content", content));
                    index++;
                }
            }
        }
        return options;
    }

    private String requireNonEmptyArrayJson(Object value, String message) {
        List<String> answers = stringList(value);
        if (answers.isEmpty()) {
            throw new BusinessException(40000, message);
        }
        return writeJson(answers);
    }

    private String normalizeJudgeAnswer(Object value) {
        List<String> answers = stringList(value);
        String token = answers.isEmpty() ? "" : answers.getFirst().trim().toLowerCase(Locale.ROOT);
        return switch (token) {
            case "true", "t", "1", "正确" -> "T";
            case "false", "f", "0", "错误" -> "F";
            default -> throw new BusinessException(40000, "判断题答案必须为 T 或 F");
        };
    }

    private List<Map<String, Object>> buildLabBlanks(Object rawBlanks) {
        if (!(rawBlanks instanceof List<?> blanks) || blanks.isEmpty()) {
            throw new BusinessException(40000, "填空题至少需要 1 个空");
        }
        List<Map<String, Object>> result = new ArrayList<>(blanks.size());
        int index = 1;
        for (Object item : blanks) {
            List<String> answers = item instanceof Map<?, ?> map ? stringList(map.get("answers")) : List.of();
            if (answers.isEmpty()) {
                throw new BusinessException(40000, "第 " + index + " 个空至少需要 1 个答案");
            }
            result.add(Map.of("index", index, "answers", answers));
            index++;
        }
        return result;
    }

    private String resolveLabTextAnswer(Map<String, Object> config, LabStep step) {
        List<String> terms = new ArrayList<>();
        if (config.get("keywords") instanceof List<?> keywords) {
            for (Object item : keywords) {
                if (item instanceof Map<?, ?> keyword) {
                    String term = stringValue(keyword.get("term"));
                    if (term != null && !term.isBlank()) {
                        terms.add(term.trim());
                    }
                }
            }
        }
        if (!terms.isEmpty()) {
            return String.join("、", terms);
        }
        String legacyAnswer = legacyAnswerText(step);
        if (legacyAnswer != null) {
            return legacyAnswer;
        }
        throw new BusinessException(40000, "简答题参考答案或评分要点不能为空");
    }

    private String resolveLabCodeAnswer(Map<String, Object> config, LabStep step) {
        String rubric = stringValue(config.get("rubric"));
        if (rubric != null && !rubric.isBlank()) {
            return rubric.trim();
        }
        String legacyAnswer = legacyAnswerText(step);
        if (legacyAnswer != null) {
            return legacyAnswer;
        }
        throw new BusinessException(40000, "简答题参考答案或评分要点不能为空");
    }

    /**
     * 历史实验题项的参考答案直接存在 experimentItemAnswer；JSON 配置对象不算有效答案。
     */
    private String legacyAnswerText(LabStep step) {
        String value = step.getExperimentItemAnswer();
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return null;
        }
        return trimmed;
    }

    private boolean hasKeywords(Map<String, Object> config) {
        return config.get("keywords") instanceof List<?> keywords && !keywords.isEmpty();
    }

    /**
     * 写入复制生成的题库题，并处理编码冲突与内容重复提示。
     */
    private Map<String, Object> persistCopiedQuestion(CurrentUser currentUser,
                                                      String sourceRef,
                                                      String sourceType,
                                                      String code,
                                                      String type,
                                                      String stem,
                                                      String difficulty,
                                                      int defaultScore,
                                                      String optionsJson,
                                                      String answerJson,
                                                      String scoringConfigJson,
                                                      String analysisText) {
        if (questionBankRepository.existsByCode(code)) {
            throw new BusinessException(40900, "题目编码已存在");
        }
        QuestionBank question = new QuestionBank();
        question.setCode(code);
        question.setType(type);
        question.setStem(stem);
        question.setDifficulty(difficulty);
        question.setDefaultScore(defaultScore);
        question.setOptionsJson(optionsJson);
        question.setAnswerJson(answerJson);
        question.setScoringConfigJson(scoringConfigJson);
        question.setAnalysisText(analysisText == null ? "" : analysisText);
        question.setCreatedBy(currentUser.id());
        if (currentUser.schoolId() == null) {
            throw new BusinessException(40000, "账号未绑定学校，无法创建教学数据");
        }
        question.setSchoolId(currentUser.schoolId());
        question.setSourceType(sourceType);
        question.setSourceRef(sourceRef);

        QuestionBank saved;
        try {
            saved = questionBankRepository.save(question);
        } catch (DataIntegrityViolationException ex) {
            // 并发重复复制由 uk_question_bank_source_ref 唯一索引兜底
            Optional<QuestionBank> concurrent = questionBankRepository.findBySourceRefAndSchoolId(sourceRef, currentUser.schoolId());
            if (concurrent.isPresent()) {
                return buildResponse(concurrent.get(), false, null);
            }
            throw new BusinessException(40900, "题目编码已存在");
        }
        auditLogService.log(currentUser, AuditAction.QUESTION_CREATE, AuditResourceType.QUESTION, saved.getId(),
                Map.of("code", saved.getCode(), "sourceType", sourceType, "sourceRef", sourceRef));
        Long duplicateQuestionId = findDuplicateQuestionId(currentUser.id(), saved, currentUser.schoolId());
        return buildResponse(saved, true, duplicateQuestionId);
    }

    /**
     * 内容重复提示：与当前教师已有题目做固定口径指纹比较，命中返回首个同内容题目 id。
     *
     * <p>仅提示、不阻断、不合并。</p>
     */
    private Long findDuplicateQuestionId(Long teacherId, QuestionBank saved, Long schoolId) {
        String fingerprint = fingerprint(saved);
        return questionBankRepository.findBySchoolIdAndCreatedBy(schoolId, teacherId).stream()
                .filter(candidate -> candidate.getId() != null && !candidate.getId().equals(saved.getId()))
                .filter(candidate -> fingerprint.equals(fingerprint(candidate)))
                .map(QuestionBank::getId)
                .findFirst()
                .orElse(null);
    }

    private String fingerprint(QuestionBank question) {
        String type = question.getType() == null ? "" : question.getType().trim().toUpperCase(Locale.ROOT);
        String stem = question.getStem() == null ? "" : question.getStem().trim();
        String options = writeJson(QuestionContentSupport.normalizeOptions(question.getOptionsJson()).stream()
                .sorted(Comparator.comparing(option -> String.valueOf(option.get("key"))))
                .toList());
        String answer = canonicalAnswer(type, question.getAnswerJson());
        String raw = type + "|" + stem + "|" + options + "|" + answer;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return raw;
        }
    }

    private String canonicalAnswer(String type, String answerJson) {
        Object answer = readJsonRaw(answerJson);
        if (answer instanceof List<?> list) {
            if ("SINGLE".equals(type) || "JUDGE".equals(type)) {
                return writeJson(list.isEmpty() ? List.of() : List.of(list.getFirst()));
            }
            if ("MULTI".equals(type)) {
                List<String> sorted = stringList(list).stream().sorted().toList();
                return writeJson(sorted);
            }
            if ("FILL".equals(type)) {
                List<Map<String, Object>> normalized = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> blank) {
                        normalized.add(Map.of(
                                "index", blank.get("index") == null ? 0 : blank.get("index"),
                                "answers", stringList(blank.get("answers")).stream().sorted().toList()
                        ));
                    }
                }
                normalized.sort(Comparator.comparingInt(item -> (int) numberValue(item.get("index"), 0D)));
                return writeJson(normalized);
            }
            return writeJson(stringList(list).stream().sorted().toList());
        }
        return writeJson(answer);
    }

    private Map<String, Object> buildResponse(QuestionBank question, boolean created, Long duplicateQuestionId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("created", created);
        response.put("question", questionBankViewMapper.toView(question));
        response.put("duplicateQuestionId", duplicateQuestionId);
        return response;
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.TEACHER) {
            throw new BusinessException(40300, "无教师权限");
        }
    }

    /**
     * 编码归一：优先使用教师传入值，缺省使用来源默认编码；统一大写并截断至 64 字符。
     */
    private String resolveCode(String code, String fallback) {
        String candidate = code == null || code.isBlank() ? fallback : code;
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > CODE_MAX_LENGTH ? normalized.substring(0, CODE_MAX_LENGTH) : normalized;
    }

    private String toBankQuestionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "";
        }
        return switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "SINGLE", "SINGLE_CHOICE" -> "SINGLE";
            case "MULTI", "MULTIPLE", "MULTIPLE_CHOICE" -> "MULTI";
            case "JUDGE", "TRUE_FALSE" -> "JUDGE";
            case "FILL", "FILL_BLANK" -> "FILL";
            case "SHORT", "TEXT", "SUBJECTIVE" -> "SHORT";
            default -> rawType.trim().toUpperCase(Locale.ROOT);
        };
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            throw new BusinessException(40000, "JSON 数据格式不正确");
        }
    }

    private Object readJsonRaw(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() { });
        } catch (Exception ex) {
            throw new BusinessException(40000, "JSON 数据格式不正确");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(50000, "JSON 序列化失败");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private double numberValue(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (fallback != null) {
            return fallback;
        }
        return 0D;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(item -> String.valueOf(item).trim())
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        if (value == null) {
            return List.of();
        }
        String single = String.valueOf(value).trim();
        return single.isBlank() ? List.of() : List.of(single);
    }
}
