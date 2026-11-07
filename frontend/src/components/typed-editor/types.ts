export type TypedEditorKind = 'SINGLE' | 'MULTI' | 'JUDGE' | 'FILL' | 'SHORT' | 'TEXT' | 'CODE';

export type TypedEditorJudgeAnswer = 'T' | 'F' | '';

export interface TypedEditorOptionDraft {
  id: string;
  key: string;
  content: string;
}

export interface TypedEditorBlankDraft {
  index: number;
  token: string;
  answersText: string;
}

export interface TypedEditorDraft {
  kind: TypedEditorKind;
  prompt: string;
  objectiveOptions: TypedEditorOptionDraft[];
  selectedObjectiveOptionIds: string[];
  judgeAnswer: TypedEditorJudgeAnswer;
  fillBlanks: TypedEditorBlankDraft[];
  subjectiveAnswer: string;
}

export interface TypedEditorValidationResult {
  messages: string[];
}

export interface TypedEditorHydratedState {
  draft: TypedEditorDraft;
}

export interface QuestionEditorContext {
  code: string;
  difficulty: string;
  defaultScore: number;
  analysisText: string;
}

export interface TypedEditorValidationContext {
  promptLabel?: string;
  fillAnswerLabel?: (index: number) => string;
  subjectiveRequiredLabel?: Partial<Record<TypedEditorKind, string>>;
}

export interface TypedEditorSerializationResult {
  optionsJson: string | null;
  answerJson: string;
}
