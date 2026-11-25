import { describe, expect, it } from 'vitest';
import {
  displaySuggestedScore,
  formatJudgeDetail,
  maxScoreOf,
  questionTypeLabel,
  resolvedInitialScore,
} from '../components/scoreDisplay';

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

describe('判分说明与题型中文化（TD7/TD8）', () => {
  it('单选与判断题映射为中文', () => {
    expect(formatJudgeDetail('single choice matched')).toBe('答案匹配，系统自动给分');
    expect(formatJudgeDetail('single choice mismatch')).toBe('答案不匹配，系统判定 0 分');
    expect(formatJudgeDetail('true/false matched')).toBe('判断正确，系统自动给分');
    expect(formatJudgeDetail('true/false mismatch')).toBe('判断错误，系统判定 0 分');
  });

  it('多选只展示规则说明，不泄漏答案集合', () => {
    expect(
      formatJudgeDetail('multiple choice mode=ALL_CORRECT, expected=[A,B], actual=[A]'),
    ).toBe('多选题按当前规则自动判定');
  });

  it('填空多段按空号拼接', () => {
    expect(formatJudgeDetail('blank 1: matched; blank 2: mismatch')).toBe(
      '第 1 空匹配、第 2 空不匹配',
    );
  });

  it('参考分依据与自动判题异常映射为中文', () => {
    expect(formatJudgeDetail('matched=[关键词]; length=20/50')).toBe(
      '系统按命中关键词与作答长度生成参考分',
    );
    expect(formatJudgeDetail('scoring engine unavailable')).toBe('自动判题暂不可用，请人工评分');
    expect(formatJudgeDetail('missing rule config')).toBe('自动判题暂不可用，请人工评分');
    expect(formatJudgeDetail('unsupported question type')).toBe('自动判题暂不可用，请人工评分');
  });

  it('空值与未知串回退为中文兜底', () => {
    expect(formatJudgeDetail(null)).toBe('系统自动判定');
    expect(formatJudgeDetail('')).toBe('系统自动判定');
    expect(formatJudgeDetail('something unexpected')).toBe('系统自动判定');
  });

  it('questionTypeLabel 覆盖三域题型与未知值', () => {
    expect(questionTypeLabel('SINGLE_CHOICE')).toBe('单选题');
    expect(questionTypeLabel('MULTIPLE_CHOICE')).toBe('多选题');
    expect(questionTypeLabel('TRUE_FALSE')).toBe('判断题');
    expect(questionTypeLabel('FILL_BLANK')).toBe('填空题');
    expect(questionTypeLabel('SUBJECTIVE')).toBe('主观题');
    expect(questionTypeLabel('TEXT')).toBe('文本题');
    expect(questionTypeLabel('SHORT_ANSWER')).toBe('简答题');
    expect(questionTypeLabel('CODE')).toBe('编程题');
    expect(questionTypeLabel('UNKNOWN')).toBe('其他题型');
    expect(questionTypeLabel(null)).toBe('其他题型');
  });
});
