package com.opencode.teachingplatform.question.service;

import com.opencode.teachingplatform.question.entity.QuestionBank;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 题库题目视图转换器。
 *
 * <p>题库列表、复制到题库的响应等场景需要完全一致的题目视图结构，
 * 因此把转换逻辑收敛到唯一实现，避免各服务各自拼装导致字段漂移。</p>
 */
@Component
public class QuestionBankViewMapper {

    /**
     * 把题库实体转换为前端消费的 Map 视图。
     */
    public Map<String, Object> toView(QuestionBank question) {
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
        view.put("scoringConfigJson", question.getScoringConfigJson());
        view.put("createdBy", question.getCreatedBy());
        view.put("sourceType", question.getSourceType());
        view.put("sourceRef", question.getSourceRef());
        return view;
    }
}
