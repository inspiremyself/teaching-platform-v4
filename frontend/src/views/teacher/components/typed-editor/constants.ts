import type { TypedEditorDraft, TypedEditorKind, TypedEditorJudgeAnswer, TypedEditorValidationContext } from './types';

export const ALL_TYPED_EDITOR_KIND_OPTIONS: TypedEditorKind[] = ['SINGLE', 'MULTI', 'JUDGE', 'FILL', 'SHORT', 'TEXT', 'CODE'];

export const QUESTION_TYPED_EDITOR_KIND_OPTIONS = ['SINGLE', 'MULTI', 'JUDGE', 'FILL', 'SHORT'] as const;

export const OBJECTIVE_TYPED_EDITOR_KINDS: TypedEditorKind[] = ['SINGLE', 'MULTI'];

export const TYPED_EDITOR_LABELS: Record<TypedEditorKind, string> = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  FILL: '填空题',
  SHORT: '简答题',
  TEXT: '文本题',
  CODE: '代码题',
};

export const TYPED_EDITOR_JUDGE_CHOICES: Array<{ value: Exclude<TypedEditorJudgeAnswer, ''>; label: string }> = [
  { value: 'T', label: '正确' },
  { value: 'F', label: '错误' },
];

export const DEFAULT_TYPED_EDITOR_VALIDATION_CONTEXT: TypedEditorValidationContext = {
  promptLabel: '题干内容',
  fillAnswerLabel: index => `请填写空 ${index} 的正确答案`,
  subjectiveRequiredLabel: {
    SHORT: '请填写简答题参考答案',
    TEXT: '请填写文本题参考答案',
    CODE: '请填写代码题参考实现或评分要点',
  },
};

export const createEmptyTypedEditorDraft = (kind: TypedEditorKind = 'SINGLE'): TypedEditorDraft => ({
  kind,
  prompt: '',
  objectiveOptions: [],
  selectedObjectiveOptionIds: [],
  judgeAnswer: '',
  fillBlanks: [],
  subjectiveAnswer: '',
});
