import type { CreateLabStepPayload, FillBlankRuleConfig, LabQuestionType, LabTextKeywordRule, TextQuestionRuleConfig } from '@/types/lab';
import type { QuestionDifficulty, SaveQuestionPayload } from '@/types/question';
import type { TypedEditorDraft } from '../types';
import { buildQuestionPayloadFromDraft } from './questionAdapter';
import { splitTypedEditorAnswerLines } from '../hydrators/fromQuestion';
import { normalizeLabQuestionType } from '../hydrators/fromLabStep';

export interface LabStepSubjectiveSettings {
  minLength: number;
  commentTemplate: string;
}

export interface LabStepFillSettings {
  ignoreCase: boolean;
}

export interface LabStepCodeSettings {
  language: string;
}

export const serializeLabStepAnswerConfig = (
  questionType: LabQuestionType,
  draft: TypedEditorDraft,
  settings: {
    subjective: LabStepSubjectiveSettings;
    fill: LabStepFillSettings;
    code: LabStepCodeSettings;
  },
) => {
  const normalizedType = normalizeLabQuestionType(questionType);

  if (normalizedType === 'SHORT_ANSWER') {
    const keywords = splitTypedEditorAnswerLines(draft.subjectiveAnswer).map((term) => ({ term, weight: 10 } satisfies LabTextKeywordRule));
    const payload: TextQuestionRuleConfig = {
      keywords,
      minLength: settings.subjective.minLength > 0 ? settings.subjective.minLength : undefined,
      commentTemplate: settings.subjective.commentTemplate.trim() || undefined,
    };
    return JSON.stringify(payload);
  }

  if (normalizedType === 'SINGLE_CHOICE' || normalizedType === 'MULTIPLE_CHOICE') {
    const options = draft.objectiveOptions
      .map(option => ({ key: option.key.trim(), label: option.content.trim() }))
      .filter(option => option.key && option.label);
    const correctAnswer = draft.objectiveOptions
      .filter(option => draft.selectedObjectiveOptionIds.includes(option.id))
      .map(option => option.key.trim())
      .filter(Boolean);

    return JSON.stringify({
      options,
      correctAnswer,
      gradingMode: 'exact',
    });
  }

  if (normalizedType === 'TRUE_FALSE') {
    const correctAnswer = draft.judgeAnswer === 'T' ? ['TRUE'] : draft.judgeAnswer === 'F' ? ['FALSE'] : [];
    return JSON.stringify({
      options: [
        { key: 'TRUE', label: '正确' },
        { key: 'FALSE', label: '错误' },
      ],
      correctAnswer,
      gradingMode: 'exact',
    });
  }

  if (normalizedType === 'FILL_BLANK') {
    const payload: FillBlankRuleConfig = {
      blanks: draft.fillBlanks.map(blank => ({
        answers: splitTypedEditorAnswerLines(blank.answersText),
      })),
      ignoreCase: settings.fill.ignoreCase,
    };

    return JSON.stringify(payload);
  }

  return JSON.stringify({
    language: settings.code.language.trim().toUpperCase() || 'JAVA',
    rubric: draft.subjectiveAnswer.trim() || undefined,
  });
};

export const buildLabStepPayloadFromDraft = (
  payload: Omit<CreateLabStepPayload, 'content' | 'answerConfigJson' | 'questionType'> & { questionType: LabQuestionType },
  draft: TypedEditorDraft,
  settings: {
    subjective: LabStepSubjectiveSettings;
    fill: LabStepFillSettings;
    code: LabStepCodeSettings;
  },
): CreateLabStepPayload => ({
  ...payload,
  questionType: normalizeLabQuestionType(payload.questionType),
  content: draft.prompt.trim(),
  answerConfigJson: serializeLabStepAnswerConfig(payload.questionType, draft, settings),
});

export const buildQuestionCodeFromLabStep = (labId: number, stepNo: number, kind: TypedEditorDraft['kind']) => {
  const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 14);
  return `LAB${labId}-S${String(stepNo).padStart(2, '0')}-${kind.slice(0, 3)}-${stamp}`;
};

export const buildQuestionPayloadFromLabStepDraft = (
  labId: number,
  stepNo: number,
  draft: TypedEditorDraft,
  syncSettings: { difficulty: QuestionDifficulty; analysisText: string },
  defaultScore: number,
): SaveQuestionPayload => buildQuestionPayloadFromDraft(draft, {
  code: buildQuestionCodeFromLabStep(labId, stepNo, draft.kind),
  // 实验域保留 TEXT/CODE 两类编辑器形态，同步题库时统一归入简答题
  type: draft.kind === 'CODE' || draft.kind === 'TEXT' ? 'SHORT' : draft.kind,
  difficulty: syncSettings.difficulty,
  defaultScore,
  analysisText: syncSettings.analysisText,
});

export const validateLabStepDraft = (
  questionType: LabQuestionType,
  draft: TypedEditorDraft,
  title: string,
) => {
  const messages: string[] = [];

  if (!title.trim()) {
    messages.push('请输入题项标题');
  }

  if (!draft.prompt.trim()) {
    messages.push('请输入题项说明');
  }

  const normalizedType = normalizeLabQuestionType(questionType);

  if (normalizedType === 'SHORT_ANSWER') {
    if (!draft.subjectiveAnswer.trim()) {
      messages.push('请填写参考答案或评分要点');
    }
    return messages;
  }

  if (normalizedType === 'CODE') {
    if (!draft.subjectiveAnswer.trim()) {
      messages.push('请填写代码题参考实现或评分要点');
    }
    return messages;
  }

  if (normalizedType === 'FILL_BLANK') {
    if (!draft.fillBlanks.length) {
      messages.push('填空题至少需要一个填空位');
    }
    if (draft.fillBlanks.some(blank => splitTypedEditorAnswerLines(blank.answersText).length === 0)) {
      messages.push('请为每个填空位填写至少一个可接受答案');
    }
    return messages;
  }

  if (normalizedType === 'TRUE_FALSE') {
    if (!draft.judgeAnswer) {
      messages.push('请设置判断题正确答案');
    }
    return messages;
  }

  const filledOptions = draft.objectiveOptions.filter(option => option.content.trim());
  if (filledOptions.length < 2) {
    messages.push('客观题至少需要 2 个有效选项');
  }
  if (filledOptions.length !== draft.objectiveOptions.length) {
    messages.push('请补全所有选项内容');
  }
  if (!draft.selectedObjectiveOptionIds.length) {
    messages.push(normalizedType === 'SINGLE_CHOICE' ? '请设置单选题正确答案' : '请至少选择一个多选题正确答案');
  }

  return messages;
};
