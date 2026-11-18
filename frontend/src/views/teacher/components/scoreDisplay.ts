/**
 * 三域批改页共享的评分展示工具（考试 / 作业 / 实验统一口径）。
 *
 * scoreSource 双轨现网值都需覆盖：lab 落库前将 RECOMMENDED 映射为 SUGGESTED，
 * exam/homework 保留原始枚举名；映射表见技术方案 2026-11-17 TD10。
 */

export interface ScoreDisplayItem {
  score?: number | null
  autoScore?: number | null
  suggestedScore?: number | null
  maxScore?: number | null
  stepScore?: number | null
}

export const scoreSourceLabel = (source?: string | null) => {
  if (source === 'AUTO') return '自动评分';
  if (source === 'AUTO_ACCEPTED') return '已采纳自动分';
  if (source === 'SUGGESTED' || source === 'RECOMMENDED') return '建议评分';
  if (source === 'TEACHER') return '教师评分';
  return '未标注';
};

export const scoreSourceTagType = (source?: string | null): 'success' | 'warning' | 'info' | 'default' => {
  if (source === 'AUTO' || source === 'AUTO_ACCEPTED') return 'success';
  if (source === 'SUGGESTED' || source === 'RECOMMENDED') return 'warning';
  if (source === 'TEACHER') return 'info';
  return 'default';
};

export const displaySuggestedScore = (item: ScoreDisplayItem) => {
  if (item.autoScore !== null && item.autoScore !== undefined) return `自动 ${item.autoScore}`;
  if (item.suggestedScore !== null && item.suggestedScore !== undefined) return `建议 ${item.suggestedScore}`;
  return '暂无';
};

export const resolvedInitialScore = (item: ScoreDisplayItem) => item.score ?? item.autoScore ?? item.suggestedScore ?? 0;

export const maxScoreOf = (item: ScoreDisplayItem) => item.maxScore ?? item.stepScore ?? 100;
