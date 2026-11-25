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

export const displaySuggestedScore = (item: ScoreDisplayItem) => {
  if (item.autoScore !== null && item.autoScore !== undefined) return `自动 ${item.autoScore}`;
  if (item.suggestedScore !== null && item.suggestedScore !== undefined) return `建议 ${item.suggestedScore}`;
  return '暂无';
};

export const resolvedInitialScore = (item: ScoreDisplayItem) => item.score ?? item.autoScore ?? item.suggestedScore ?? 0;

export const maxScoreOf = (item: ScoreDisplayItem) => item.maxScore ?? item.stepScore ?? 100;

export type ScoreSourceLike =
  | 'AUTO'
  | 'AUTO_ACCEPTED'
  | 'SUGGESTED'
  | 'RECOMMENDED'
  | 'TEACHER'
  | string
  | null
  | undefined;

const JUDGE_DETAIL_FALLBACK = '系统自动判定';

export function formatJudgeDetail(raw?: string | null): string {
  if (!raw) return JUDGE_DETAIL_FALLBACK;
  const text = String(raw).trim();
  if (!text) return JUDGE_DETAIL_FALLBACK;

  const blanks = Array.from(text.matchAll(/blank\s+(\d+)\s*:\s*(matched|mismatch)/gi));
  if (blanks.length) {
    return blanks
      .map(match => `第 ${match[1]} 空${match[2]!.toLowerCase() === 'matched' ? '匹配' : '不匹配'}`)
      .join('、');
  }

  const lower = text.toLowerCase();
  if (
    lower.includes('scoring engine unavailable') ||
    lower.includes('missing rule config') ||
    lower.includes('unsupported question type')
  ) {
    return '自动判题暂不可用，请人工评分';
  }
  if (lower.startsWith('single choice matched')) return '答案匹配，系统自动给分';
  if (lower.startsWith('single choice mismatch')) return '答案不匹配，系统判定 0 分';
  if (lower.startsWith('true/false matched')) return '判断正确，系统自动给分';
  if (lower.startsWith('true/false mismatch')) return '判断错误，系统判定 0 分';
  if (lower.startsWith('multiple choice')) return '多选题按当前规则自动判定';
  if (lower.startsWith('matched=[')) return '系统按命中关键词与作答长度生成参考分';
  return JUDGE_DETAIL_FALLBACK;
}

const QUESTION_TYPE_LABELS: Record<string, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SUBJECTIVE: '主观题',
  TEXT: '文本题',
  SHORT_ANSWER: '简答题',
  CODE: '编程题',
};

export function questionTypeLabel(type?: string | null): string {
  if (!type) return '其他题型';
  return QUESTION_TYPE_LABELS[type] ?? '其他题型';
}

export {
  buildScoringSummary,
  scoringStrategyOf,
} from './scoringStrategies';
export type {
  ScoreLineItem,
  ScoreLineKind,
  ScoringDomain,
  ScoringSummary,
} from './scoringStrategies';
export {
  commitDraft,
  createGradingDraft,
  hasDraftChanges,
  setDraftComment,
  setDraftScore,
} from './gradingDraft';
export type {
  DraftItem,
  GradeItem,
  GradingDraftState,
} from './gradingDraft';
