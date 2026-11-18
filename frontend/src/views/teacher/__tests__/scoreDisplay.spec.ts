import { describe, expect, it } from 'vitest';
import {
  displaySuggestedScore,
  maxScoreOf,
  resolvedInitialScore,
  scoreSourceLabel,
  scoreSourceTagType,
} from '../components/scoreDisplay';

describe('scoreSource 统一文案映射（TD10）', () => {
  it('覆盖双轨现网值与默认分支', () => {
    expect(scoreSourceLabel('AUTO')).toBe('自动评分');
    expect(scoreSourceLabel('AUTO_ACCEPTED')).toBe('已采纳自动分');
    expect(scoreSourceLabel('SUGGESTED')).toBe('建议评分');
    expect(scoreSourceLabel('RECOMMENDED')).toBe('建议评分');
    expect(scoreSourceLabel('TEACHER')).toBe('教师评分');
    expect(scoreSourceLabel(null)).toBe('未标注');
    expect(scoreSourceLabel(undefined)).toBe('未标注');
    expect(scoreSourceLabel('UNKNOWN')).toBe('未标注');
  });

  it('标签类型与语义一致', () => {
    expect(scoreSourceTagType('AUTO')).toBe('success');
    expect(scoreSourceTagType('AUTO_ACCEPTED')).toBe('success');
    expect(scoreSourceTagType('SUGGESTED')).toBe('warning');
    expect(scoreSourceTagType('RECOMMENDED')).toBe('warning');
    expect(scoreSourceTagType('TEACHER')).toBe('info');
    expect(scoreSourceTagType(null)).toBe('default');
    expect(scoreSourceTagType(undefined)).toBe('default');
  });
});

describe('评分展示函数（TD11 抽共享）', () => {
  it('displaySuggestedScore 优先自动分，其次建议分', () => {
    expect(displaySuggestedScore({ autoScore: 8, suggestedScore: 5 })).toBe('自动 8');
    expect(displaySuggestedScore({ autoScore: null, suggestedScore: 5 })).toBe('建议 5');
    expect(displaySuggestedScore({})).toBe('暂无');
  });

  it('resolvedInitialScore 按 score → autoScore → suggestedScore → 0 兜底', () => {
    expect(resolvedInitialScore({ score: 9, autoScore: 7, suggestedScore: 5 })).toBe(9);
    expect(resolvedInitialScore({ autoScore: 7, suggestedScore: 5 })).toBe(7);
    expect(resolvedInitialScore({ suggestedScore: 5 })).toBe(5);
    expect(resolvedInitialScore({})).toBe(0);
  });

  it('maxScoreOf 按 maxScore → stepScore → 100 兜底', () => {
    expect(maxScoreOf({ maxScore: 20, stepScore: 10 })).toBe(20);
    expect(maxScoreOf({ stepScore: 10 })).toBe(10);
    expect(maxScoreOf({})).toBe(100);
  });
});
