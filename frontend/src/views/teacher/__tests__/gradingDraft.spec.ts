import { describe, expect, it } from 'vitest';
import {
  commitDraft,
  createGradingDraft,
  hasDraftChanges,
  setDraftComment,
  setDraftScore,
} from '../components/gradingDraft';

const items = [
  { key: '1', score: null as number | null, teacherComment: '' },
  { key: '2', score: 5 as number | null, teacherComment: '已有评语' },
];

describe('共享草稿状态（TD5）', () => {
  it('未评分保留 null，不被收敛为 0', () => {
    const state = createGradingDraft(items);
    expect(state.scores['1']).toBeNull();
    expect(state.scores['2']).toBe(5);
    expect(state.marked).toEqual([]);
    expect(hasDraftChanges(state)).toBe(false);
  });

  it('显式 0 计入标记，清空分数撤销标记', () => {
    const state = createGradingDraft(items);
    setDraftScore(state, '1', 0);
    expect(state.marked).toEqual(['1']);
    expect(state.scores['1']).toBe(0);
    expect(hasDraftChanges(state)).toBe(true);
    setDraftScore(state, '1', null);
    expect(state.marked).toEqual([]);
    expect(hasDraftChanges(state)).toBe(false);
  });

  it('评语单独修改也构成草稿', () => {
    const state = createGradingDraft(items);
    setDraftComment(state, '2', '新评语');
    expect(hasDraftChanges(state)).toBe(true);
    commitDraft(state);
    expect(hasDraftChanges(state)).toBe(false);
    expect(state.baselineMarked).toEqual(state.marked);
  });

  it('markAll 把已落库分数写入基线，不误报草稿', () => {
    const state = createGradingDraft(items, { markAll: true });
    expect(state.marked).toEqual(['2']);
    expect(state.baselineMarked).toEqual(['2']);
    expect(hasDraftChanges(state)).toBe(false);
    setDraftScore(state, '1', 8);
    expect(hasDraftChanges(state)).toBe(true);
  });
});
