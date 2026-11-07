package com.opencode.teachingplatform.lab.service;

import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.service.AnswerPayloadNormalizer;
import com.opencode.teachingplatform.assessment.service.GradingAdapter;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.grading.service.ScoringEngine;

import java.util.List;

/**
 * 实验域接入共享评分层的适配层。
 *
 * <p>实验的自动判分一直直接调用 {@link ScoringEngine#evaluate}，本类把它收敛到
 * 单一入口，同时暴露 {@link GradingAdapter} 与 {@link AnswerPayloadNormalizer}
 * 的共享能力。evaluate 只做透传，评分上下文、策略分发与结果字段均不改变。</p>
 */
public class LabAssessmentAdapter {

    private final ScoringEngine scoringEngine;
    private final GradingAdapter gradingAdapter;

    public LabAssessmentAdapter(ScoringEngine scoringEngine) {
        this.scoringEngine = scoringEngine;
        this.gradingAdapter = new GradingAdapter(scoringEngine);
    }

    public ScoringResult evaluate(ScoringContext context) {
        return scoringEngine.evaluate(context);
    }

    public GradingAdapter grading() {
        return gradingAdapter;
    }

    public QuestionAnswerPayload normalize(Long questionRefId,
                                           String questionType,
                                           String answerText,
                                           String answerJson,
                                           List<String> selectedOptions,
                                           String attachmentPath) {
        return AnswerPayloadNormalizer.normalize(questionRefId, questionType, answerText,
                answerJson, selectedOptions, attachmentPath);
    }
}
