import type { QuestionType, SaveQuestionPayload } from '@/types/question';
import { isQuestionType } from '@/types/question';
import { TYPED_EDITOR_JUDGE_CHOICES } from '../constants';
import { splitTypedEditorAnswerLines } from '../hydrators/fromQuestion';
import type { TypedEditorDraft, TypedEditorSerializationResult } from '../types';

/** 存量题型归一：文本题并入简答题，历史别名映射到五类标准题型，CODE 等脏值回退为单选 */
export const normalizeQuestionType = (type?: string | null): QuestionType => {
  if (type === 'TEXT') return 'SHORT';
  if (type === 'SINGLE_CHOICE') return 'SINGLE';
  if (type === 'MULTIPLE_CHOICE') return 'MULTI';
  if (type === 'TRUE_FALSE') return 'JUDGE';
  if (type === 'FILL_BLANK') return 'FILL';
  return type && isQuestionType(type) ? type : 'SINGLE';
};

const pad2 = (value: number) => String(value).padStart(2, '0');

/** 题目编码：本地时区 yyMMddHHmmss + 4 位大写 base36 随机位，展示值与落库值同源 */
export const buildQuestionCode = (type: QuestionType): string => {
  const now = new Date();
  const stamp = `${pad2(now.getFullYear() % 100)}${pad2(now.getMonth() + 1)}${pad2(now.getDate())}`
    + `${pad2(now.getHours())}${pad2(now.getMinutes())}${pad2(now.getSeconds())}`;
  const suffix = Math.random().toString(36).slice(2, 6).padEnd(4, '0').toUpperCase();
  return `Q-${type.slice(0, 3)}-${stamp}${suffix}`;
};

const serializeQuestionDraft = (draft: TypedEditorDraft): TypedEditorSerializationResult => {
  if (draft.kind === 'SINGLE' || draft.kind === 'MULTI') {
    const serializedOptions = draft.objectiveOptions.map(option => ({
      key: option.key,
      content: option.content.trim(),
    }));
    const serializedAnswers = draft.objectiveOptions
      .filter(option => draft.selectedObjectiveOptionIds.includes(option.id))
      .map(option => option.key);

    return {
      optionsJson: JSON.stringify(serializedOptions),
      answerJson: JSON.stringify(serializedAnswers),
    };
  }

  if (draft.kind === 'JUDGE') {
    return {
      optionsJson: JSON.stringify(
        TYPED_EDITOR_JUDGE_CHOICES.map(choice => ({
          key: choice.value,
          content: choice.label,
        })),
      ),
      answerJson: JSON.stringify(draft.judgeAnswer ? [draft.judgeAnswer] : []),
    };
  }

  if (draft.kind === 'FILL') {
    return {
      optionsJson: null,
      answerJson: JSON.stringify(
        draft.fillBlanks.map(blank => ({
          index: blank.index,
          answers: splitTypedEditorAnswerLines(blank.answersText),
        })),
      ),
    };
  }

  return {
    optionsJson: null,
    answerJson: JSON.stringify(splitTypedEditorAnswerLines(draft.subjectiveAnswer)),
  };
};

interface BuildQuestionPayloadOptions {
  code: string;
  type: QuestionType;
  difficulty: string;
  analysisText: string;
}

export const buildQuestionPayloadFromDraft = (
  draft: TypedEditorDraft,
  options: BuildQuestionPayloadOptions,
): SaveQuestionPayload => {
  const serialized = serializeQuestionDraft(draft);

  return {
    code: options.code.trim(),
    type: options.type,
    stem: draft.prompt.trim(),
    difficulty: options.difficulty,
    optionsJson: serialized.optionsJson,
    answerJson: serialized.answerJson,
    analysisText: options.analysisText.trim(),
  };
};
