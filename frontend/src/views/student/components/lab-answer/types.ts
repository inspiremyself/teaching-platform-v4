import type { LabQuestionType, StudentLabStepItem } from '@/types/lab';

export type LabAnswerJudgeValue = 'TRUE' | 'FALSE';

export interface LabAnswerBlankDraft {
  index: number;
  answer: string;
}

export type LabAnswerDraft =
  | { kind: 'single'; selectedKey: string | null }
  | { kind: 'multiple'; selectedKeys: string[] }
  | { kind: 'judge'; value: LabAnswerJudgeValue | null }
  | { kind: 'fill'; blanks: LabAnswerBlankDraft[] }
  | { kind: 'text'; text: string; images: LabAnswerImageDraftMeta[] }
  | { kind: 'code'; code: string; language: string | null };

export interface LabAnswerImageDraftMeta {
  path: string;
  name: string;
  contentType: string;
  size: number;
  compressed?: boolean;
  originalSize?: number;
}

export type StudentAnswerQuestionType =
  | 'TEXT'
  | 'SHORT_ANSWER'
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'FILL_BLANK'
  | 'CODE';

export const normalizeStudentAnswerQuestionType = (value?: LabQuestionType | null): StudentAnswerQuestionType => {
  const normalized = String(value ?? '').trim().toUpperCase();

  if (normalized === 'SINGLE') return 'SINGLE_CHOICE';
  if (normalized === 'SINGLE_CHOICE') return 'SINGLE_CHOICE';
  if (normalized === 'MULTI') return 'MULTIPLE_CHOICE';
  if (normalized === 'MULTIPLE') return 'MULTIPLE_CHOICE';
  if (normalized === 'MULTIPLE_CHOICE') return 'MULTIPLE_CHOICE';
  if (normalized === 'JUDGE') return 'TRUE_FALSE';
  if (normalized === 'TRUE_FALSE') return 'TRUE_FALSE';
  if (normalized === 'FILL') return 'FILL_BLANK';
  if (normalized === 'FILL_BLANK') return 'FILL_BLANK';
  if (normalized === 'CODE') return 'CODE';
  if (normalized === 'SHORT') return 'SHORT_ANSWER';
  if (normalized === 'TEXT') return 'TEXT';
  return 'SHORT_ANSWER';
};

export const createEmptyDraftForStep = (item: StudentLabStepItem): LabAnswerDraft => {
  const questionType = normalizeStudentAnswerQuestionType(item.questionType);

  if (questionType === 'SINGLE_CHOICE') {
    return { kind: 'single', selectedKey: null };
  }

  if (questionType === 'MULTIPLE_CHOICE') {
    return { kind: 'multiple', selectedKeys: [] };
  }

  if (questionType === 'TRUE_FALSE') {
    return { kind: 'judge', value: null };
  }

  if (questionType === 'FILL_BLANK') {
    return {
      kind: 'fill',
      blanks: (item.blanks ?? [])
        .slice()
        .sort((left, right) => left.index - right.index)
        .map(blank => ({ index: blank.index, answer: '' })),
    };
  }

  if (questionType === 'CODE') {
    return {
      kind: 'code',
      code: '',
      language: item.editorLanguage?.trim() || null,
    };
  }

  return { kind: 'text', text: '', images: [] };
};

export const isDraftAnswered = (draft: LabAnswerDraft): boolean => {
  if (draft.kind === 'single') {
    return Boolean(draft.selectedKey?.trim());
  }

  if (draft.kind === 'multiple') {
    return draft.selectedKeys.some(key => key.trim());
  }

  if (draft.kind === 'judge') {
    return draft.value !== null;
  }

  if (draft.kind === 'fill') {
    return draft.blanks.some(blank => blank.answer.trim());
  }

  if (draft.kind === 'code') {
    return Boolean(draft.code.trim());
  }

  return Boolean(draft.text.trim()) || draft.images.some(image => image.path);
};

export const cloneLabAnswerDraft = (draft: LabAnswerDraft): LabAnswerDraft => {
  if (draft.kind === 'single') {
    return { kind: 'single', selectedKey: draft.selectedKey };
  }

  if (draft.kind === 'multiple') {
    return { kind: 'multiple', selectedKeys: [...draft.selectedKeys] };
  }

  if (draft.kind === 'judge') {
    return { kind: 'judge', value: draft.value };
  }

  if (draft.kind === 'fill') {
    return {
      kind: 'fill',
      blanks: draft.blanks.map(blank => ({ ...blank })),
    };
  }

  if (draft.kind === 'code') {
    return {
      kind: 'code',
      code: draft.code,
      language: draft.language,
    };
  }

  return { kind: 'text', text: draft.text, images: draft.images.map(image => ({ ...image })) };
};
