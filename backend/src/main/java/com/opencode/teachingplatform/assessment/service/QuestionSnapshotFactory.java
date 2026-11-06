package com.opencode.teachingplatform.assessment.service;

import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;

public final class QuestionSnapshotFactory {

    private QuestionSnapshotFactory() {
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
