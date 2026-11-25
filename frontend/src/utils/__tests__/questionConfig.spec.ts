import { describe, expect, it } from 'vitest';
import type { ConfiguredQuestionItem } from '@/types/question-config';
import {
  mapOptionsForList,
  mergeConfiguredQuestions,
  planTotalScore,
  resequenceConfiguredQuestions,
} from '@/utils/questionConfig';

const makeItem = (overrides: Partial<ConfiguredQuestionItem> = {}): ConfiguredQuestionItem => ({
  localId: 'local',
  sourceType: 'BANK',
  questionType: 'SINGLE_CHOICE',
  stem: '题干',
  sortOrder: 1,
  score: 5,
  ...overrides,
});

describe('resequenceConfiguredQuestions', () => {
  it('按数组顺序重排 sortOrder 并保留其余字段，不修改入参', () => {
    const input = [
      makeItem({ localId: 'c', stem: 'C', sortOrder: 3 }),
      makeItem({ localId: 'a', stem: 'A', sortOrder: 1 }),
      makeItem({ localId: 'b', stem: 'B', sortOrder: 2 }),
    ];

    const output = resequenceConfiguredQuestions(input);

    expect(output.map(item => item.sortOrder)).toEqual([1, 2, 3]);
    expect(output.map(item => item.localId)).toEqual(['c', 'a', 'b']);
    expect(output.map(item => item.stem)).toEqual(['C', 'A', 'B']);
    expect(input.map(item => item.sortOrder)).toEqual([3, 1, 2]);
  });
});

describe('mergeConfiguredQuestions', () => {
  const inlineA = makeItem({ localId: 'a', sourceType: 'INLINE', questionBankId: null, score: 3 });
  const bankQ1 = makeItem({ localId: 'q1', questionBankId: 1, score: 10 });
  const bankQ2 = makeItem({ localId: 'q2', questionBankId: 2, score: 4 });
  const inlineB = makeItem({ localId: 'b', sourceType: 'INLINE', questionBankId: null, score: 6 });

  it('includeInline 为 true 时保留已有 INLINE 并复用已有 BANK 分数', () => {
    const merged = mergeConfiguredQuestions(
      [inlineA, bankQ1],
      [makeItem({ localId: 'incoming-q1', questionBankId: 1, score: 1 }), bankQ2, inlineB],
      { includeInline: true },
    );

    expect(merged.map(item => item.localId)).toEqual(['a', 'q1', 'q2', 'b']);
    expect(merged.map(item => item.sortOrder)).toEqual([1, 2, 3, 4]);
    expect(merged[1].score).toBe(10);
  });

  it('includeInline 为 false 时只保留带 questionBankId 的入参题目', () => {
    const merged = mergeConfiguredQuestions(
      [inlineA, bankQ1],
      [makeItem({ localId: 'incoming-q1', questionBankId: 1, score: 1 }), bankQ2, inlineB],
      { includeInline: false },
    );

    expect(merged.map(item => item.localId)).toEqual(['q1', 'q2']);
    expect(merged.map(item => item.sortOrder)).toEqual([1, 2]);
    expect(merged[0].score).toBe(10);
  });

  it('incoming 为空时按 includeInline 决定保留范围', () => {
    const withInline = mergeConfiguredQuestions([inlineA, bankQ1], [], { includeInline: true });
    expect(withInline.map(item => item.localId)).toEqual(['a']);
    expect(withInline[0].sortOrder).toBe(1);

    const withoutInline = mergeConfiguredQuestions([inlineA, bankQ1], [], { includeInline: false });
    expect(withoutInline).toEqual([]);
  });
});

describe('planTotalScore', () => {
  it('按 selector 求和，空值按 0，字符串数字按数值', () => {
    const items = [{ score: 5 }, { score: '7' }, { score: null }, { score: undefined }, {}] as Array<{
      score?: number | string | null;
    }>;
    expect(planTotalScore(items, item => item.score)).toBe(12);
  });

  it('支持 stepScore 与 questionScore 回退 score', () => {
    expect(planTotalScore([{ stepScore: 2 }, { stepScore: 3 }], item => item.stepScore)).toBe(5);
    expect(
      planTotalScore(
        [{ questionScore: 8, score: 3 }, { questionScore: null, score: 4 }],
        item => item.questionScore ?? item.score,
      ),
    ).toBe(12);
  });

  it('空数组返回 0', () => {
    expect(planTotalScore([], item => item)).toBe(0);
  });
});

describe('mapOptionsForList', () => {
  it('对象数组取 key/content', () => {
    const json = JSON.stringify([
      { key: 'A', content: '选项一' },
      { key: 'B', content: '选项二' },
    ]);
    expect(mapOptionsForList(json)).toEqual([
      { key: 'A', label: '选项一' },
      { key: 'B', label: '选项二' },
    ]);
  });

  it('仅 label 的对象回退 label 并按序生成 key', () => {
    expect(mapOptionsForList(JSON.stringify([{ label: '仅标签' }]))).toEqual([{ key: 'A', label: '仅标签' }]);
  });

  it('字符串数组生成 A/B 键', () => {
    expect(mapOptionsForList(JSON.stringify(['甲', '乙']))).toEqual([
      { key: 'A', label: '甲' },
      { key: 'B', label: '乙' },
    ]);
  });

  it('空值、非法 JSON、非数组 JSON 均返回空数组', () => {
    expect(mapOptionsForList(null)).toEqual([]);
    expect(mapOptionsForList(undefined)).toEqual([]);
    expect(mapOptionsForList('')).toEqual([]);
    expect(mapOptionsForList('not-json')).toEqual([]);
    expect(mapOptionsForList('{"a":1}')).toEqual([]);
  });
});
