package com.opencode.teachingplatform.question.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.question.dto.QuestionRequests.SaveQuestionRequest;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionService {

    private final QuestionBankRepository questionBankRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public QuestionService(QuestionBankRepository questionBankRepository, AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.questionBankRepository = questionBankRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(CurrentUser currentUser) {
        return questionBankRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(CurrentUser currentUser, SaveQuestionRequest request) {
        if (questionBankRepository.existsByCode(request.code())) {
            throw new BusinessException(40900, "题目编码已存在");
        }
        QuestionBank question = new QuestionBank();
        apply(question, request);
        question.setCreatedBy(currentUser.id());
        try {
            QuestionBank saved = questionBankRepository.save(question);
            auditLogService.log(currentUser, AuditAction.QUESTION_CREATE, AuditResourceType.QUESTION, saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrityViolation(ex);
        }
    }

    @Transactional
    public Map<String, Object> update(CurrentUser currentUser, Long id, SaveQuestionRequest request) {
        QuestionBank question = questionBankRepository.findByIdAndCreatedBy(id, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该题目"));
        if (questionBankRepository.existsByCodeAndIdNotAndCreatedBy(request.code(), id, currentUser.id())) {
            throw new BusinessException(40900, "题目编码已存在");
        }
        apply(question, request);
        try {
            QuestionBank saved = questionBankRepository.save(question);
            auditLogService.log(currentUser, AuditAction.QUESTION_UPDATE, AuditResourceType.QUESTION, saved.getId(), Map.of("code", saved.getCode()));
            return toView(saved);
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
        validateJsonPayload(request.optionsJson(), request.answerJson());
        question.setCode(request.code().trim());
        question.setType(request.type().trim());
        question.setStem(request.stem().trim());
        question.setDifficulty(request.difficulty().trim());
        question.setDefaultScore(request.defaultScore());
        question.setOptionsJson(normalizeNullable(request.optionsJson()));
        question.setAnswerJson(request.answerJson().trim());
        question.setAnalysisText(request.analysisText() == null ? "" : request.analysisText().trim());
    }

    private Map<String, Object> toView(QuestionBank question) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", question.getId());
        view.put("code", question.getCode());
        view.put("type", question.getType());
        view.put("stem", question.getStem());
        view.put("difficulty", question.getDifficulty());
        view.put("defaultScore", question.getDefaultScore());
        view.put("optionsJson", question.getOptionsJson());
        view.put("answerJson", question.getAnswerJson());
        view.put("analysisText", question.getAnalysisText() == null ? "" : question.getAnalysisText());
        view.put("createdBy", question.getCreatedBy());
        return view;
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

    private void validateJsonPayload(String optionsJson, String answerJson) {
        validateJsonIfPresent(optionsJson, "题目选项 JSON 格式不正确");
        validateJsonIfPresent(answerJson, "题目答案 JSON 格式不正确");
    }

    private void validateJsonIfPresent(String json, String message) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BusinessException(40000, message);
        }
    }
}
