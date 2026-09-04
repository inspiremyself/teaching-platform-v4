import type { StudentBlankMeta, StudentLabStepItem } from '@/types/lab';
import {
  createEmptyDraftForStep,
  normalizeStudentAnswerQuestionType,
  type LabAnswerBlankDraft,
  type LabAnswerDraft,
  type LabAnswerJudgeValue,
} from './types';

const parseJsonValue = (value?: string | null) => {
  if (!value?.trim()) {
    return null;
  }

  try {
    return JSON.parse(value) as unknown;
  } catch {
    return null;
  }
};

const normalizeChoiceKey = (value: unknown) => {
  const normalized = String(value ?? '').trim().toUpperCase();
  return normalized || null;
};

const normalizeMultipleChoiceKeys = (values: unknown[]) => {
  const uniqueKeys = new Set<string>();

  values.forEach((value) => {
    const key = normalizeChoiceKey(value);
    if (key) {
      uniqueKeys.add(key);
    }
  });

  return [...uniqueKeys].sort((left, right) => left.localeCompare(right));
};

const normalizeJudgeValue = (value: unknown): LabAnswerJudgeValue | null => {
  const normalized = String(value ?? '').trim().toUpperCase();

  if (['TRUE', 'T', '正确', 'YES', 'Y', '1', 'A'].includes(normalized)) {
    return 'TRUE';
  }

  if (['FALSE', 'F', '错误', 'NO', 'N', '0', 'B'].includes(normalized)) {
    return 'FALSE';
  }

  return null;
};

const normalizeFillBlanks = (blanks: LabAnswerBlankDraft[]) =>
  blanks
    .map(blank => ({
      index: Number(blank.index) || 0,
      answer: String(blank.answer ?? ''),
    }))
    .filter(blank => blank.index > 0)
    .sort((left, right) => left.index - right.index);

const splitLegacyAnswerText = (value: string) =>
  value
    .split(/[\n,，;；]+/)
    .map(part => part.trim())
    .filter(Boolean);

const buildBlankSeed = (metas?: StudentBlankMeta[], answers: string[] = []): LabAnswerBlankDraft[] => {
  const indexes = metas?.length
    ? metas.slice().sort((left, right) => left.index - right.index).map(blank => blank.index)
    : answers.map((_, index) => index + 1);

  return indexes.map((index, position) => ({
    index,
    answer: answers[position] ?? '',
  }));
};

const canonicalizeDraft = (draft: LabAnswerDraft): LabAnswerDraft => {
  if (draft.kind === 'single') {
    return {
      kind: 'single',
      selectedKey: normalizeChoiceKey(draft.selectedKey),
    };
  }

  if (draft.kind === 'multiple') {
    return {
      kind: 'multiple',
      selectedKeys: normalizeMultipleChoiceKeys(draft.selectedKeys),
    };
  }

  if (draft.kind === 'judge') {
    return {
      kind: 'judge',
      value: normalizeJudgeValue(draft.value),
    };
  }

  if (draft.kind === 'fill') {
    return {
      kind: 'fill',
      blanks: normalizeFillBlanks(draft.blanks),
    };
  }

  if (draft.kind === 'code') {
    return {
      kind: 'code',
      code: String(draft.code ?? ''),
      language: draft.language?.trim() || null,
    };
  }

  return {
    kind: 'text',
    text: String(draft.text ?? ''),
    images: (draft.images ?? []).map(image => ({
      path: String(image.path ?? ''),
      name: String(image.name ?? ''),
      contentType: String(image.contentType ?? ''),
      size: Number(image.size ?? 0),
      compressed: image.compressed ?? false,
      originalSize: image.originalSize,
    })),
  };
};

const hydrateFromPayloadObject = (item: StudentLabStepItem, payload: unknown): LabAnswerDraft | null => {
  const questionType = normalizeStudentAnswerQuestionType(item.questionType);
  const record = payload && typeof payload === 'object' && !Array.isArray(payload)
    ? (payload as Record<string, unknown>)
    : null;

  if (!record) {
    return null;
  }

  if (questionType === 'SINGLE_CHOICE') {
    return canonicalizeDraft({
      kind: 'single',
      selectedKey: normalizeChoiceKey(record.selectedKey ?? record.value ?? record.answer ?? record.answerText),
    });
  }

  if (questionType === 'MULTIPLE_CHOICE') {
    const selectedKeys = Array.isArray(record.selectedKeys)
      ? record.selectedKeys
      : Array.isArray(record.answers)
        ? record.answers
        : Array.isArray(record.values)
          ? record.values
          : typeof record.answerText === 'string'
            ? splitLegacyAnswerText(record.answerText)
            : [];

    return canonicalizeDraft({
      kind: 'multiple',
      selectedKeys: normalizeMultipleChoiceKeys(selectedKeys),
    });
  }

  if (questionType === 'TRUE_FALSE') {
    return canonicalizeDraft({
      kind: 'judge',
      value: normalizeJudgeValue(record.value ?? record.selectedKey ?? record.answer ?? record.answerText),
    });
  }

  if (questionType === 'FILL_BLANK') {
    const rawBlanks = Array.isArray(record.blanks) ? record.blanks : [];
    const blanks = rawBlanks
      .map((blank, index) => {
        if (!blank || typeof blank !== 'object') {
          return null;
        }

        const blankRecord = blank as Record<string, unknown>;
        return {
          index: Number(blankRecord.index ?? index + 1),
          answer: String(blankRecord.answer ?? blankRecord.value ?? ''),
        } satisfies LabAnswerBlankDraft;
      })
      .filter((blank): blank is LabAnswerBlankDraft => Boolean(blank && blank.index > 0));

    return canonicalizeDraft({
      kind: 'fill',
      blanks: blanks.length ? blanks : buildBlankSeed(item.blanks),
    });
  }

  if (questionType === 'CODE') {
    return canonicalizeDraft({
      kind: 'code',
      code: String(record.code ?? record.text ?? record.answer ?? record.answerText ?? ''),
      language: String(record.language ?? item.editorLanguage ?? '').trim() || null,
    });
  }

  return canonicalizeDraft({
    kind: 'text',
    text: String(record.text ?? record.answer ?? record.answerText ?? record.value ?? ''),
    images: Array.isArray(record.images)
      ? record.images.map((image) => {
          if (!image || typeof image !== 'object') {
            return null;
          }
          const imageRecord = image as Record<string, unknown>;
          return {
            path: String(imageRecord.path ?? ''),
            name: String(imageRecord.name ?? ''),
            contentType: String(imageRecord.contentType ?? ''),
            size: Number(imageRecord.size ?? 0),
            compressed: Boolean(imageRecord.compressed),
            originalSize: imageRecord.originalSize == null ? undefined : Number(imageRecord.originalSize),
          };
        }).filter((image): image is NonNullable<typeof image> => Boolean(image?.path))
      : [],
  });
};

export const hydrateDraftFromAnswerText = (item: StudentLabStepItem, answerText?: string | null): LabAnswerDraft => {
  const value = String(answerText ?? '');
  const questionType = normalizeStudentAnswerQuestionType(item.questionType);

  if (!value.trim()) {
    return createEmptyDraftForStep(item);
  }

  if (questionType === 'SINGLE_CHOICE') {
    const firstKey = splitLegacyAnswerText(value)[0] ?? value;
    return canonicalizeDraft({ kind: 'single', selectedKey: firstKey });
  }

  if (questionType === 'MULTIPLE_CHOICE') {
    return canonicalizeDraft({ kind: 'multiple', selectedKeys: splitLegacyAnswerText(value) });
  }

  if (questionType === 'TRUE_FALSE') {
    return canonicalizeDraft({ kind: 'judge', value: normalizeJudgeValue(value) });
  }

  if (questionType === 'FILL_BLANK') {
    const answers = splitLegacyAnswerText(value);
    return canonicalizeDraft({
      kind: 'fill',
      blanks: buildBlankSeed(item.blanks, answers),
    });
  }

  if (questionType === 'CODE') {
    return canonicalizeDraft({
      kind: 'code',
      code: value,
      language: item.editorLanguage?.trim() || null,
    });
  }

  return canonicalizeDraft({ kind: 'text', text: value, images: [] });
};

export const hydrateDraftFromStep = (item: StudentLabStepItem): LabAnswerDraft => {
  const payload = parseJsonValue(item.answerPayloadJson);
  const hydratedFromPayload = hydrateFromPayloadObject(item, payload);

  if (hydratedFromPayload) {
    return hydratedFromPayload;
  }

  if (item.answerText?.trim()) {
    return hydrateDraftFromAnswerText(item, item.answerText);
  }

  return createEmptyDraftForStep(item);
};

export const buildAnswerPayloadJson = (draft: LabAnswerDraft): string => {
  return JSON.stringify(canonicalizeDraft(draft));
};

export const buildCompatibilityAnswerText = (draft: LabAnswerDraft): string => {
  const normalizedDraft = canonicalizeDraft(draft);

  if (normalizedDraft.kind === 'single') {
    return normalizedDraft.selectedKey ?? '';
  }

  if (normalizedDraft.kind === 'multiple') {
    return normalizedDraft.selectedKeys.join(',');
  }

  if (normalizedDraft.kind === 'judge') {
    return normalizedDraft.value ?? '';
  }

  if (normalizedDraft.kind === 'fill') {
    return normalizedDraft.blanks
      .slice()
      .sort((left, right) => left.index - right.index)
      .map(blank => blank.answer.trim())
      .join(',');
  }

  if (normalizedDraft.kind === 'code') {
    return normalizedDraft.code;
  }

  return normalizedDraft.text;
};

export const buildLegacyEditorValue = (draft: LabAnswerDraft): string => {
  const normalizedDraft = canonicalizeDraft(draft);

  if (normalizedDraft.kind === 'judge') {
    if (normalizedDraft.value === 'TRUE') return 'true';
    if (normalizedDraft.value === 'FALSE') return 'false';
    return '';
  }

  return buildCompatibilityAnswerText(normalizedDraft);
};
