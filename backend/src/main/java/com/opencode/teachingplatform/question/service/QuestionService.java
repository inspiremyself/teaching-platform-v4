package com.opencode.teachingplatform.question.service;

import com.opencode.teachingplatform.assessment.service.QuestionContentValidator;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.question.dto.QuestionRequests.SaveQuestionRequest;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QuestionService {

    /** 手工创建题目的来源标记，复制链路会写入 HOMEWORK_INLINE / LAB_STEP。 */
    private static final String SOURCE_TYPE_MANUAL = "MANUAL";

    private final QuestionBankRepository questionBankRepository;
    private final AuditLogService auditLogService;
    private final QuestionContentValidator questionContentValidator;
    private final QuestionBankViewMapper questionBankViewMapper;

    public QuestionService(QuestionBankRepository questionBankRepository,
                           AuditLogService auditLogService,
                           QuestionContentValidator questionContentValidator,
                           QuestionBankViewMapper questionBankViewMapper) {
        this.questionBankRepository = questionBankRepository;
        this.auditLogService = auditLogService;
        this.questionContentValidator = questionContentValidator;
        this.questionBankViewMapper = questionBankViewMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(CurrentUser currentUser) {
        return questionBankRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream()
                .map(questionBankViewMapper::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(CurrentUser currentUser, SaveQuestionRequest request) {
        if (questionBankRepository.existsByCode(normalizeCode(request.code()))) {
            throw new BusinessException(40900, "题目编码已存在");
        }
        QuestionBank question = new QuestionBank();
        apply(question, request);
        question.setCreatedBy(currentUser.id());
        // 手工新增固定为 MANUAL，且不携带评分参数与来源键
        question.setSourceType(SOURCE_TYPE_MANUAL);
        question.setSourceRef(null);
        question.setScoringConfigJson(null);
        try {
            QuestionBank saved = questionBankRepository.save(question);
            auditLogService.log(currentUser, AuditAction.QUESTION_CREATE, AuditResourceType.QUESTION, saved.getId(), Map.of("code", saved.getCode()));
            return questionBankViewMapper.toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrityViolation(ex);
        }
    }

    @Transactional
    public Map<String, Object> update(CurrentUser currentUser, Long id, SaveQuestionRequest request) {
        QuestionBank question = questionBankRepository.findByIdAndCreatedBy(id, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该题目"));
        if (questionBankRepository.existsByCodeAndIdNot(normalizeCode(request.code()), id)) {
            throw new BusinessException(40900, "题目编码已存在");
        }
        // 更新只改题面内容：来源字段与 scoring_config_json 由复制链路写入，编辑不得覆盖
        apply(question, request);
        try {
            QuestionBank saved = questionBankRepository.save(question);
            auditLogService.log(currentUser, AuditAction.QUESTION_UPDATE, AuditResourceType.QUESTION, saved.getId(), Map.of("code", saved.getCode()));
            return questionBankViewMapper.toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrityViolation(ex);
        }
    }

    @Transactional
    public void delete(CurrentUser currentUser, Long id) {
        QuestionBank question = questionBankRepository.findByIdAndCreatedBy(id, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该题目"));
        auditLogService.log(currentUser, AuditAction.QUESTION_DELETE, AuditResourceType.QUESTION, question.getId(), Map.of("code", question.getCode()));
        questionBankRepository.delete(question);
    }

    private void apply(QuestionBank question, SaveQuestionRequest request) {
        questionContentValidator.validate(
                request.type(),
                request.stem(),
                request.defaultScore() == null ? null : request.defaultScore().doubleValue(),
                request.optionsJson(),
                request.answerJson(),
                null
        );
        question.setCode(normalizeCode(request.code()));
        question.setType(request.type().trim());
        question.setStem(request.stem().trim());
        question.setDifficulty(request.difficulty().trim());
        question.setDefaultScore(request.defaultScore());
        question.setOptionsJson(normalizeNullable(request.optionsJson()));
        question.setAnswerJson(request.answerJson().trim());
        question.setAnalysisText(request.analysisText() == null ? "" : request.analysisText().trim());
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RuntimeException translateIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("question_bank") && message.contains("code")) {
            return new BusinessException(40900, "题目编码已存在");
        }
        return ex;
    }
}
