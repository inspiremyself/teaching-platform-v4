import { parseQuestionAnswerArray } from '@/components/typed-editor/hydrators/fromQuestion';
import type { ConfiguredQuestionItem } from '@/types/question-config';

export interface QuestionOptionForList {
  key: string;
  label: string;
}

export function resequenceConfiguredQuestions(items: ConfiguredQuestionItem[]): ConfiguredQuestionItem[] {
  return items.map((item, index) => ({ ...item, sortOrder: index + 1 }));
}

export function mergeConfiguredQuestions(
  current: ConfiguredQuestionItem[],
  incoming: ConfiguredQuestionItem[],
  options: { includeInline: boolean },
): ConfiguredQuestionItem[] {
  const existingByBankId = new Map(
    current.filter(item => item.questionBankId).map(item => [item.questionBankId, item]),
  );
  const merged = options.includeInline
    ? [
        ...current.filter(item => item.sourceType === 'INLINE'),
        ...incoming.map(item =>
          item.questionBankId ? existingByBankId.get(item.questionBankId) ?? item : item,
        ),
      ]
    : incoming
        .filter(item => item.questionBankId)
        .map(item => existingByBankId.get(item.questionBankId) ?? item);

  return resequenceConfiguredQuestions(merged);
}

export function planTotalScore<T>(
  items: T[],
  scoreOf: (item: T) => number | string | null | undefined,
): number {
  return items.reduce((sum, item) => sum + (Number(scoreOf(item)) || 0), 0);
}

export function mapOptionsForList(optionsJson?: string | null): QuestionOptionForList[] {
  return parseQuestionAnswerArray(optionsJson ?? null).map((option, index) => {
    if (option && typeof option === 'object') {
      const record = option as Record<string, unknown>;
      return {
        key: String(record.key ?? String.fromCharCode(65 + index)),
        label: String(record.content ?? record.label ?? record.text ?? ''),
      };
    }

    return {
      key: String.fromCharCode(65 + index),
      label: String(option ?? ''),
    };
  });
}
