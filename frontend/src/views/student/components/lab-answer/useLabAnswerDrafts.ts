import { reactive } from 'vue';
import type { StudentLabStepItem } from '@/types/lab';
import {
  buildAnswerPayloadJson,
  buildCompatibilityAnswerText,
  hydrateDraftFromStep,
} from './answerPayload';
import { cloneLabAnswerDraft, type LabAnswerDraft } from './types';

export const useLabAnswerDrafts = () => {
  const drafts = reactive<Record<number, LabAnswerDraft>>({});

  const ensureDraft = (item: StudentLabStepItem) => {
    if (!drafts[item.id]) {
      drafts[item.id] = hydrateDraftFromStep(item);
    }

    return drafts[item.id];
  };

  const getDraft = (stepId: number) => drafts[stepId];

  const patchDraft = (stepId: number, nextDraft: LabAnswerDraft | ((draft: LabAnswerDraft) => LabAnswerDraft)) => {
    const currentDraft = drafts[stepId];
    if (!currentDraft) {
      return;
    }

    const resolvedDraft = typeof nextDraft === 'function'
      ? nextDraft(cloneLabAnswerDraft(currentDraft))
      : nextDraft;

    drafts[stepId] = cloneLabAnswerDraft(resolvedDraft);
  };

  const buildSavePayload = (stepId: number) => {
    const draft = drafts[stepId];
    if (!draft) {
      return {
        answerText: '',
        answerPayloadJson: JSON.stringify({ kind: 'text', text: '', images: [] }),
      };
    }

    return {
      answerText: buildCompatibilityAnswerText(draft),
      answerPayloadJson: buildAnswerPayloadJson(draft),
    };
  };

  const hydrateAllDrafts = (items: StudentLabStepItem[]) => {
    Object.keys(drafts).forEach((key) => {
      delete drafts[Number(key)];
    });

    items.forEach((item) => {
      drafts[item.id] = hydrateDraftFromStep(item);
    });
  };

  return {
    ensureDraft,
    getDraft,
    patchDraft,
    buildSavePayload,
    hydrateAllDrafts,
  };
};
