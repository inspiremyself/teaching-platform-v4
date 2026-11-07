import type { QuestionItem } from '@/types/question';
import type {
  TypedEditorBlankDraft,
  TypedEditorDraft,
  TypedEditorHydratedState,
  TypedEditorKind,
  TypedEditorOptionDraft,
} from '../types';
import { ALL_TYPED_EDITOR_KIND_OPTIONS, createEmptyTypedEditorDraft } from '../constants';

let optionIdSeed = 0;

const parseJsonValue = (value: string | null) => {
  if (!value?.trim()) {
    return null;
  }

  try {
    return JSON.parse(value) as unknown;
  } catch {
    return null;
  }
};

const parseJsonArray = (value: string | null) => {
  const parsed = parseJsonValue(value);
  return Array.isArray(parsed) ? parsed : [];
};

const makeOptionId = () => `option-${Date.now()}-${optionIdSeed++}`;

export const makeOptionKey = (index: number) => {
  if (index < 26) {
    return String.fromCharCode(65 + index);
  }

  return `O${index + 1}`;
};

const createOption = (content = ''): TypedEditorOptionDraft => ({
  id: makeOptionId(),
  key: '',
  content,
});

const normalizeToken = (value: string) => value.trim().toUpperCase();

const extractStringCandidates = (value: unknown): string[] => {
  if (typeof value === 'string') {
    return [value];
  }
  if (typeof value === 'number') {
    return [String(value)];
  }
  if (Array.isArray(value)) {
    return value.flatMap(item => extractStringCandidates(item));
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const keys = ['key', 'code', 'value', 'label', 'text', 'content', 'answer', 'correct'];
    return keys.flatMap(key => extractStringCandidates(record[key]));
  }
  return [];
};

interface ParsedObjectiveOption {
  content: string;
  aliases: string[];
}

const parseObjectiveOptionSources = (rawOptions: string | null): ParsedObjectiveOption[] => {
  const parsed = parseJsonArray(rawOptions);

  return parsed
    .map((item, index) => {
      if (typeof item === 'string') {
        return {
          content: item,
          aliases: [item, makeOptionKey(index), String(index + 1)],
        } satisfies ParsedObjectiveOption;
      }

      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>;
        const contentCandidate = extractStringCandidates(record.content ?? record.text ?? record.label ?? record.value)[0] ?? '';
        const keyCandidate = extractStringCandidates(record.key ?? record.code)[0] ?? '';
        return {
          content: contentCandidate,
          aliases: [contentCandidate, keyCandidate, makeOptionKey(index), String(index + 1)].filter(Boolean),
        } satisfies ParsedObjectiveOption;
      }

      return {
        content: '',
        aliases: [makeOptionKey(index), String(index + 1)],
      } satisfies ParsedObjectiveOption;
    })
    .filter(option => option.content.trim() || option.aliases.length);
};

const buildObjectiveDraft = (kind: Extract<TypedEditorKind, 'SINGLE' | 'MULTI'>, rawOptions: string | null, rawAnswers: string): TypedEditorDraft => {
  const parsedSources = parseObjectiveOptionSources(rawOptions);
  const sourceList = parsedSources.length
    ? parsedSources
    : Array.from({ length: 4 }, (_, index) => ({
        content: '',
        aliases: [makeOptionKey(index), String(index + 1)],
      }));

  const options = sourceList.map(source => createOption(source.content));
  const normalizedOptions = options.map((option, index) => ({ ...option, key: makeOptionKey(index) }));
  const answerTokens = parseJsonArray(rawAnswers).flatMap(item => extractStringCandidates(item)).map(normalizeToken);

  const selectedObjectiveOptionIds = normalizedOptions
    .filter((option, index) => {
      const aliases = [...sourceList[index].aliases, option.key, option.content].map(normalizeToken);
      return answerTokens.some(token => aliases.includes(token));
    })
    .map(option => option.id);

  return {
    ...createEmptyTypedEditorDraft(kind),
    objectiveOptions: normalizedOptions,
    selectedObjectiveOptionIds: kind === 'SINGLE' ? selectedObjectiveOptionIds.slice(0, 1) : selectedObjectiveOptionIds,
  };
};

const parseJudgeAnswer = (rawAnswers: string) => {
  const firstToken = parseJsonArray(rawAnswers).flatMap(item => extractStringCandidates(item)).map(normalizeToken)[0] ?? '';
  if (['T', 'TRUE', '正确', 'Y', 'YES', '1', 'A'].includes(firstToken)) {
    return 'T' as const;
  }
  if (['F', 'FALSE', '错误', 'N', 'NO', '0', 'B'].includes(firstToken)) {
    return 'F' as const;
  }
  return '';
};

const parseSubjectiveAnswer = (rawAnswer: string) => {
  const parsed = parseJsonValue(rawAnswer);

  if (Array.isArray(parsed)) {
    return parsed.flatMap(item => extractStringCandidates(item)).join('\n');
  }

  if (typeof parsed === 'string') {
    return parsed;
  }

  if (parsed && typeof parsed === 'object') {
    const candidates = extractStringCandidates(parsed);
    return candidates.length ? candidates.join('\n') : '';
  }

  return '';
};

const extractBlankIndexes = (prompt: string) => {
  const matches = Array.from(prompt.matchAll(/【填空(\d+)】/g));
  const uniqueIndexes = new Set<number>();

  matches.forEach(match => {
    const parsed = Number(match[1]);
    if (Number.isFinite(parsed)) {
      uniqueIndexes.add(parsed);
    }
  });

  return [...uniqueIndexes].sort((left, right) => left - right);
};

const parseFillAnswers = (rawAnswer: string) => {
  const parsed = parseJsonValue(rawAnswer);
  const answerMap = new Map<number, string[]>();

  if (Array.isArray(parsed)) {
    parsed.forEach((item, index) => {
      if (typeof item === 'string') {
        answerMap.set(index + 1, [item]);
        return;
      }

      if (Array.isArray(item)) {
        answerMap.set(index + 1, item.flatMap(entry => extractStringCandidates(entry)).filter(Boolean));
        return;
      }

      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>;
        const blankIndexCandidate = Number(record.index ?? record.blankIndex ?? index + 1);
        const answers = Array.isArray(record.answers)
          ? record.answers.flatMap(entry => extractStringCandidates(entry)).filter(Boolean)
          : extractStringCandidates(record.answer ?? record.value).filter(Boolean);

        if (Number.isFinite(blankIndexCandidate)) {
          answerMap.set(blankIndexCandidate, answers);
        }
      }
    });
  }

  return answerMap;
};

const buildFillDraft = (row: QuestionItem): TypedEditorDraft => {
  const fillAnswerMap = parseFillAnswers(row.answerJson);
  const fillBlanks: TypedEditorBlankDraft[] = extractBlankIndexes(row.stem).map(index => ({
    index,
    token: `【填空${index}】`,
    answersText: (fillAnswerMap.get(index) ?? []).join('\n'),
  }));

  return {
    ...createEmptyTypedEditorDraft('FILL'),
    prompt: row.stem,
    fillBlanks,
  };
};

const normalizeQuestionKind = (type: string): TypedEditorKind => {
  if (ALL_TYPED_EDITOR_KIND_OPTIONS.includes(type as TypedEditorKind)) {
    return type as TypedEditorKind;
  }

  return 'SINGLE';
};

export const hydrateTypedEditorFromQuestion = (row: QuestionItem): TypedEditorHydratedState => {
  const kind = normalizeQuestionKind(row.type);

  if (kind === 'SINGLE' || kind === 'MULTI') {
    return {
      draft: {
        ...buildObjectiveDraft(kind, row.optionsJson, row.answerJson),
        prompt: row.stem,
      },
    };
  }

  if (kind === 'JUDGE') {
    return {
      draft: {
        ...createEmptyTypedEditorDraft('JUDGE'),
        prompt: row.stem,
        judgeAnswer: parseJudgeAnswer(row.answerJson),
      },
    };
  }

  if (kind === 'FILL') {
    return {
      draft: buildFillDraft(row),
    };
  }

  return {
    draft: {
      ...createEmptyTypedEditorDraft(kind),
      prompt: row.stem,
      subjectiveAnswer: parseSubjectiveAnswer(row.answerJson),
    },
  };
};

export const createDefaultObjectiveOptions = (count = 4) =>
  Array.from({ length: count }, (_, index) => ({
    ...createOption(''),
    key: makeOptionKey(index),
  }));

export const splitTypedEditorAnswerLines = (value: string) =>
  value
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(Boolean);

export const extractTypedEditorBlankIndexes = extractBlankIndexes;

export const parseQuestionAnswerArray = parseJsonArray;
