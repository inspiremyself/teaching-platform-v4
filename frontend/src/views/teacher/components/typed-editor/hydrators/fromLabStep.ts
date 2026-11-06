import type {
  FillBlankRuleConfig,
  LabQuestionType,
  LabStepAutoRuleOption,
  LabStepItem,
  LabTextKeywordRule,
  TextQuestionRuleConfig,
} from '@/types/lab';
import type { QuestionItem } from '@/types/question';
import { hydrateTypedEditorFromQuestion, splitTypedEditorAnswerLines } from './fromQuestion';
import type { TypedEditorKind } from '../types';

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

export interface HydratedLabStepEditorState {
  draft: ReturnType<typeof hydrateTypedEditorFromQuestion>['draft'];
  subjectiveSettings: LabStepSubjectiveSettings;
  fillSettings: LabStepFillSettings;
  codeSettings: LabStepCodeSettings;
}

const DEFAULT_SUBJECTIVE_SETTINGS: LabStepSubjectiveSettings = {
  minLength: 0,
  commentTemplate: '',
};

const DEFAULT_FILL_SETTINGS: LabStepFillSettings = {
  ignoreCase: true,
};

const DEFAULT_CODE_SETTINGS: LabStepCodeSettings = {
  language: 'JAVA',
};

const DEFAULT_OBJECTIVE_OPTIONS: Record<'SINGLE_CHOICE' | 'MULTIPLE_CHOICE', LabStepAutoRuleOption[]> = {
  SINGLE_CHOICE: [
    { key: 'A', label: '' },
    { key: 'B', label: '' },
  ],
  MULTIPLE_CHOICE: [
    { key: 'A', label: '' },
    { key: 'B', label: '' },
  ],
};

const JUDGE_OPTIONS: LabStepAutoRuleOption[] = [
  { key: 'TRUE', label: '正确' },
  { key: 'FALSE', label: '错误' },
];

const parseJsonRecord = (value?: string | null): Record<string, unknown> => {
  if (!value?.trim()) {
    return {};
  }

  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
};

const parseKeywords = (value: unknown) => {
  if (!Array.isArray(value)) {
    return [] as LabTextKeywordRule[];
  }

  return value
    .map((item) => {
      if (typeof item === 'string') {
        return { term: item.trim(), weight: 10 } satisfies LabTextKeywordRule;
      }

      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>;
        return {
          term: String(record.term ?? '').trim(),
          weight: typeof record.weight === 'number' ? record.weight : 10,
        } satisfies LabTextKeywordRule;
      }

      return null;
    })
    .filter((item): item is LabTextKeywordRule => Boolean(item?.term));
};

const parseObjectiveOptions = (value: unknown, questionType: LabQuestionType) => {
  if (!Array.isArray(value)) {
    if (questionType === 'TRUE_FALSE') {
      return JUDGE_OPTIONS;
    }
    if (questionType === 'SINGLE_CHOICE' || questionType === 'MULTIPLE_CHOICE') {
      return DEFAULT_OBJECTIVE_OPTIONS[questionType];
    }
    return [] as LabStepAutoRuleOption[];
  }

  return value
    .map((item) => {
      if (!item || typeof item !== 'object') {
        return null;
      }

      const record = item as Record<string, unknown>;
      return {
        key: String(record.key ?? '').trim(),
        label: String(record.label ?? record.content ?? '').trim(),
      } satisfies LabStepAutoRuleOption;
    })
    .filter((item): item is LabStepAutoRuleOption => Boolean(item?.key && item.label));
};

export const normalizeLabQuestionType = (value?: string | null): LabQuestionType => {
  const normalized = String(value ?? '').trim().toUpperCase();

  if (!normalized) {
    return 'SHORT_ANSWER';
  }

  if (normalized === 'SUBJECTIVE' || normalized === 'TEXT') {
    return 'SHORT_ANSWER';
  }

  return normalized;
};

export const mapLabQuestionTypeToTypedKind = (questionType: LabQuestionType): TypedEditorKind => {
  switch (normalizeLabQuestionType(questionType)) {
    case 'SINGLE_CHOICE':
      return 'SINGLE';
    case 'MULTIPLE_CHOICE':
      return 'MULTI';
    case 'TRUE_FALSE':
      return 'JUDGE';
    case 'FILL_BLANK':
      return 'FILL';
    case 'CODE':
      return 'CODE';
    case 'SHORT_ANSWER':
    default:
      return 'SHORT';
  }
};

/** typed-editor 题型反解为实验题项题型：用于从题库预填实验题项 */
export const mapTypedKindToLabQuestionType = (kind: TypedEditorKind): LabQuestionType => {
  switch (kind) {
    case 'SINGLE':
      return 'SINGLE_CHOICE';
    case 'MULTI':
      return 'MULTIPLE_CHOICE';
    case 'JUDGE':
      return 'TRUE_FALSE';
    case 'FILL':
      return 'FILL_BLANK';
    case 'CODE':
      return 'CODE';
    case 'SHORT':
    default:
      return 'SHORT_ANSWER';
  }
};

export const getLabQuestionTypeLabel = (questionType: LabQuestionType) => {
  switch (normalizeLabQuestionType(questionType)) {
    case 'SHORT_ANSWER':
      return '简答题';
    case 'SINGLE_CHOICE':
      return '单选题';
    case 'MULTIPLE_CHOICE':
      return '多选题';
    case 'TRUE_FALSE':
      return '判断题';
    case 'FILL_BLANK':
      return '填空题';
    case 'CODE':
      return '代码题';
    default:
      return questionType;
  }
};

export const createEmptyLabStepEditorState = (questionType: LabQuestionType = 'SHORT_ANSWER'): HydratedLabStepEditorState => {
  const kind = mapLabQuestionTypeToTypedKind(questionType);
  const seedQuestion: QuestionItem = {
    id: 0,
    code: '',
    type: kind,
    stem: '',
    difficulty: 'EASY',
    defaultScore: 0,
    optionsJson: kind === 'JUDGE' ? JSON.stringify(JUDGE_OPTIONS.map(option => ({ key: option.key, content: option.label }))) : null,
    answerJson: '[]',
    analysisText: '',
  };

  return {
    draft: hydrateTypedEditorFromQuestion(seedQuestion).draft,
    subjectiveSettings: { ...DEFAULT_SUBJECTIVE_SETTINGS },
    fillSettings: { ...DEFAULT_FILL_SETTINGS },
    codeSettings: { ...DEFAULT_CODE_SETTINGS },
  };
};

export const hydrateTypedEditorFromLabStep = (row: LabStepItem): HydratedLabStepEditorState => {
  const normalizedType = normalizeLabQuestionType(row.questionType);
  const config = parseJsonRecord(row.answerConfigJson);
  const kind = mapLabQuestionTypeToTypedKind(normalizedType);

  let optionsJson: string | null = null;
  let answerJson = '[]';
  let subjectiveSettings = { ...DEFAULT_SUBJECTIVE_SETTINGS };
  let fillSettings = { ...DEFAULT_FILL_SETTINGS };
  let codeSettings = { ...DEFAULT_CODE_SETTINGS };

  if (kind === 'SHORT' || kind === 'TEXT') {
    const keywords = parseKeywords(config.keywords);
    answerJson = JSON.stringify(keywords.map(keyword => keyword.term));
    subjectiveSettings = {
      minLength: typeof config.minLength === 'number' ? config.minLength : 0,
      commentTemplate: typeof config.commentTemplate === 'string' ? config.commentTemplate : '',
    };
  }

  if (kind === 'SINGLE' || kind === 'MULTI' || kind === 'JUDGE') {
    const options = parseObjectiveOptions(config.options, normalizedType);
    const normalizedOptions = options.length
      ? options
      : kind === 'JUDGE'
        ? JUDGE_OPTIONS
        : DEFAULT_OBJECTIVE_OPTIONS[normalizedType as 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE'];
    const rawCorrectAnswer = Array.isArray(config.correctAnswer)
      ? config.correctAnswer
      : Array.isArray(config.correctAnswers)
        ? config.correctAnswers
        : [];

    optionsJson = JSON.stringify(normalizedOptions.map(option => ({ key: option.key, content: option.label })));
    answerJson = JSON.stringify(rawCorrectAnswer.map(item => String(item)));
  }

  if (kind === 'FILL') {
    const blanks = Array.isArray(config.blanks)
      ? (config.blanks as unknown[])
          .map((blank, index) => {
            if (!blank || typeof blank !== 'object') {
              return null;
            }

            const record = blank as Record<string, unknown>;
            const answers = Array.isArray(record.answers)
              ? record.answers.map(item => String(item ?? '').trim()).filter(Boolean)
              : [];

            return answers.length
              ? {
                  index: index + 1,
                  answers,
                }
              : null;
          })
          .filter((blank): blank is { index: number; answers: string[] } => Boolean(blank))
      : [];

    answerJson = JSON.stringify(blanks);
    fillSettings = {
      ignoreCase: config.ignoreCase !== false,
    };
  }

  if (kind === 'CODE') {
    answerJson = JSON.stringify(splitTypedEditorAnswerLines(String(config.rubric ?? '')));
    codeSettings = {
      language: String(config.language ?? row.editorLanguage ?? 'JAVA').trim().toUpperCase() || 'JAVA',
    };
  }

  const questionLike: QuestionItem = {
    id: row.id,
    code: `LAB-${row.id}`,
    type: kind,
    stem: row.content,
    difficulty: 'EASY',
    defaultScore: row.stepScore,
    optionsJson,
    answerJson,
    analysisText: '',
  };

  return {
    draft: hydrateTypedEditorFromQuestion(questionLike).draft,
    subjectiveSettings,
    fillSettings,
    codeSettings,
  };
};
