import type { LabAnswerImageMeta, LabReportAnswerItem } from '@/types/lab';

export const extractReportImages = (item: LabReportAnswerItem): LabAnswerImageMeta[] => {
  if (item.images?.length) {
    return item.images;
  }
  if (!item.answerPayloadJson) {
    return [];
  }
  try {
    const payload = JSON.parse(item.answerPayloadJson) as { images?: LabAnswerImageMeta[] };
    return Array.isArray(payload.images) ? payload.images : [];
  } catch {
    return [];
  }
};
