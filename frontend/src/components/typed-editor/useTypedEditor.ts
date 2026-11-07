import { computed, reactive, watch } from 'vue';
import { createEmptyTypedEditorDraft, DEFAULT_TYPED_EDITOR_VALIDATION_CONTEXT, OBJECTIVE_TYPED_EDITOR_KINDS } from './constants';
import {
  createDefaultObjectiveOptions,
  extractTypedEditorBlankIndexes,
  hydrateTypedEditorFromQuestion,
  splitTypedEditorAnswerLines,
} from './hydrators/fromQuestion';
import type { QuestionItem } from '@/types/question';
import type { TypedEditorBlankDraft, TypedEditorDraft, TypedEditorKind, TypedEditorValidationContext } from './types';

interface UseTypedEditorOptions {
  validationContext?: TypedEditorValidationContext;
}

export const useTypedEditor = (options: UseTypedEditorOptions = {}) => {
  const validationContext = {
    ...DEFAULT_TYPED_EDITOR_VALIDATION_CONTEXT,
    ...options.validationContext,
    subjectiveRequiredLabel: {
      ...DEFAULT_TYPED_EDITOR_VALIDATION_CONTEXT.subjectiveRequiredLabel,
      ...options.validationContext?.subjectiveRequiredLabel,
    },
  } satisfies TypedEditorValidationContext;

  const draft = reactive<TypedEditorDraft>(createEmptyTypedEditorDraft());

  let optionIdSeed = 0;

  const createObjectiveOption = (content = '') => ({
    id: `editor-option-${Date.now()}-${optionIdSeed++}`,
    key: '',
    content,
  });

  const isObjectiveKind = computed(() => OBJECTIVE_TYPED_EDITOR_KINDS.includes(draft.kind));

  const normalizeObjectiveOptions = () => {
    draft.objectiveOptions = draft.objectiveOptions.map((option, index) => ({
      ...option,
      key: String.fromCharCode(65 + index) ?? `O${index + 1}`,
    }));
  };

  const ensureObjectiveDefaults = (count = 4) => {
    if (draft.objectiveOptions.length) {
      normalizeObjectiveOptions();
      return;
    }

    draft.objectiveOptions = createDefaultObjectiveOptions(count).map(option => ({
      ...createObjectiveOption(option.content),
      key: option.key,
    }));
  };

  const replaceDraft = (nextDraft: TypedEditorDraft) => {
    draft.kind = nextDraft.kind;
    draft.prompt = nextDraft.prompt;
    draft.objectiveOptions = nextDraft.objectiveOptions.map(option => ({ ...option }));
    draft.selectedObjectiveOptionIds = [...nextDraft.selectedObjectiveOptionIds];
    draft.judgeAnswer = nextDraft.judgeAnswer;
    draft.fillBlanks = nextDraft.fillBlanks.map(blank => ({ ...blank }));
    draft.subjectiveAnswer = nextDraft.subjectiveAnswer;
  };

  const reset = (kind: TypedEditorKind = 'SINGLE') => {
    replaceDraft(createEmptyTypedEditorDraft(kind));
    if (kind === 'SINGLE' || kind === 'MULTI') {
      ensureObjectiveDefaults();
    }
  };

  const syncFillBlanks = () => {
    const previous = draft.fillBlanks;
    const matches = Array.from(draft.prompt.matchAll(/【填空(\d+)】/g));

    if (!matches.length) {
      draft.fillBlanks = [];
      return;
    }

    let normalizedPrompt = '';
    let cursor = 0;
    const nextBlanks: TypedEditorBlankDraft[] = matches.map((match, occurrenceIndex) => {
      const originalIndex = Number(match[1]);
      const nextIndex = occurrenceIndex + 1;
      const positional = previous[occurrenceIndex];
      const answersText =
        positional && positional.index === originalIndex
          ? positional.answersText
          : previous.find(blank => blank.index === originalIndex)?.answersText ?? '';

      normalizedPrompt += draft.prompt.slice(cursor, match.index) + `【填空${nextIndex}】`;
      cursor = (match.index ?? 0) + match[0].length;

      return { index: nextIndex, token: `【填空${nextIndex}】`, answersText };
    });

    normalizedPrompt += draft.prompt.slice(cursor);
    draft.prompt = normalizedPrompt;
    draft.fillBlanks = nextBlanks;
  };

  const removeBlank = (blankIndex: number) => {
    const token = `【填空${blankIndex}】`;
    draft.prompt = draft.prompt.replace(token, '');
    syncFillBlanks();
  };

  const setKind = (kind: TypedEditorKind) => {
    draft.kind = kind;

    if (kind === 'SINGLE' || kind === 'MULTI') {
      ensureObjectiveDefaults();
      if (kind === 'SINGLE' && draft.selectedObjectiveOptionIds.length > 1) {
        draft.selectedObjectiveOptionIds = [];
        return true;
      }
      return false;
    }

    if (kind === 'FILL') {
      syncFillBlanks();
    }

    return false;
  };

  const hydrateFromQuestion = (row: QuestionItem) => {
    replaceDraft(hydrateTypedEditorFromQuestion(row).draft);

    if (draft.kind === 'SINGLE' || draft.kind === 'MULTI') {
      ensureObjectiveDefaults();
    }
  };

  const addObjectiveOption = () => {
    draft.objectiveOptions.push(createObjectiveOption(''));
    normalizeObjectiveOptions();
  };

  const removeObjectiveOption = (optionId: string) => {
    if (draft.objectiveOptions.length <= 2) {
      return;
    }

    draft.objectiveOptions = draft.objectiveOptions.filter(option => option.id !== optionId);
    draft.selectedObjectiveOptionIds = draft.selectedObjectiveOptionIds.filter(id => id !== optionId);
    normalizeObjectiveOptions();
  };

  const toggleObjectiveAnswer = (optionId: string) => {
    if (draft.kind === 'SINGLE') {
      draft.selectedObjectiveOptionIds = [optionId];
      return;
    }

    if (draft.selectedObjectiveOptionIds.includes(optionId)) {
      draft.selectedObjectiveOptionIds = draft.selectedObjectiveOptionIds.filter(id => id !== optionId);
      return;
    }

    draft.selectedObjectiveOptionIds = [...draft.selectedObjectiveOptionIds, optionId];
  };

  const insertBlankToken = (selection?: { start: number; end: number }) => {
    const existingIndexes = extractTypedEditorBlankIndexes(draft.prompt);
    const nextIndex = existingIndexes.length ? Math.max(...existingIndexes) + 1 : 1;
    const token = `【填空${nextIndex}】`;
    const start = selection?.start ?? draft.prompt.length;
    const end = selection?.end ?? draft.prompt.length;

    draft.prompt = `${draft.prompt.slice(0, start)}${token}${draft.prompt.slice(end)}`;
    syncFillBlanks();

    return {
      token,
      nextCursor: start + token.length,
    };
  };

  const validationMessages = computed(() => {
    const messages: string[] = [];

    if (!draft.prompt.trim()) {
      messages.push(`请填写${validationContext.promptLabel ?? '题干内容'}`);
    }

    if (isObjectiveKind.value) {
      const filledOptions = draft.objectiveOptions.filter(option => option.content.trim());
      if (filledOptions.length < 2) {
        messages.push('客观题至少需要 2 个有效选项');
      }
      if (filledOptions.length !== draft.objectiveOptions.length) {
        messages.push('请补全所有选项内容');
      }
      if (!draft.selectedObjectiveOptionIds.length) {
        messages.push(draft.kind === 'SINGLE' ? '请设置单选题正确答案' : '请至少选择一个多选题正确答案');
      }
    }

    if (draft.kind === 'JUDGE' && !draft.judgeAnswer) {
      messages.push('请设置判断题正确答案');
    }

    if (draft.kind === 'FILL') {
      if (!draft.fillBlanks.length) {
        messages.push('填空题至少需要插入一个填空位');
      }
      const incompleteBlank = draft.fillBlanks.find(blank => splitTypedEditorAnswerLines(blank.answersText).length === 0);
      if (incompleteBlank) {
        messages.push(validationContext.fillAnswerLabel?.(incompleteBlank.index) ?? `请填写空 ${incompleteBlank.index} 的正确答案`);
      }
    }

    if ((draft.kind === 'SHORT' || draft.kind === 'TEXT' || draft.kind === 'CODE') && !draft.subjectiveAnswer.trim()) {
      messages.push(validationContext.subjectiveRequiredLabel?.[draft.kind] ?? '请填写参考答案');
    }

    return messages;
  });

  const modeSummary = computed(() => {
    if (draft.kind === 'FILL') {
      return `填空位 ${draft.fillBlanks.length} 个`;
    }
    if (draft.kind === 'JUDGE') {
      return draft.judgeAnswer ? '已设置判断答案' : '待设置判断答案';
    }
    if (isObjectiveKind.value) {
      return `${draft.objectiveOptions.length} 个选项 / ${draft.selectedObjectiveOptionIds.length} 个正确答案`;
    }
    return draft.subjectiveAnswer.trim() ? '已填写参考答案' : '待填写参考答案';
  });

  watch(
    () => draft.prompt,
    () => {
      if (draft.kind === 'FILL') {
        syncFillBlanks();
      }
    },
  );

  reset();

  return {
    draft,
    isObjectiveKind,
    validationMessages,
    modeSummary,
    ensureObjectiveDefaults,
    reset,
    setKind,
    hydrateFromQuestion,
    addObjectiveOption,
    removeObjectiveOption,
    toggleObjectiveAnswer,
    insertBlankToken,
    removeBlank,
    syncFillBlanks,
  };
};
