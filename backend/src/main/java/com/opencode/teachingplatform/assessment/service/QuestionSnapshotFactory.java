package com.opencode.teachingplatform.assessment.service;

import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;

public final class QuestionSnapshotFactory {

    private QuestionSnapshotFactory() {
    }

    /**
     * 从已落库的题目快照 JSON 还原评分快照。
     *
     * <p>只接收基础类型，避免共享层反向依赖作业域实体；调用方负责把 JSON 字段序列化成字符串。</p>
     */
    public static QuestionSnapshot fromStored(Long questionRefId,
                                              String sourceType,
                                              String questionType,
                                              String stem,
                                              Integer sortOrder,
                                              Double score,
                                              String optionsJson,
                                              String answerJson,
                                              String scoringConfigJson,
                                              boolean fromBank) {
        return new QuestionSnapshot(
                questionRefId,
                sourceType,
                questionType,
                stem,
                sortOrder,
                score,
                optionsJson,
                answerJson,
                scoringConfigJson,
                fromBank
        );
    }

    public static QuestionSnapshot fromBankQuestion(Long questionBankId,
                                                    String questionType,
                                                    String stem,
                                                    String optionsJson,
                                                    String answerJson,
                                                    String scoringConfigJson,
                                                    Integer sortOrder,
                                                    Double score) {
        return new QuestionSnapshot(
                questionBankId,
                "BANK",
                questionType,
                stem,
                sortOrder,
                score,
                optionsJson,
                answerJson,
                scoringConfigJson,
                true
        );
    }
}
