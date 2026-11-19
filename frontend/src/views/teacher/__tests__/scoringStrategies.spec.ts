import { describe, expect, it } from 'vitest';
import {
  buildScoringSummary,
  scoringStrategyOf,
  type ScoreLineItem,
} from '../components/scoringStrategies';

describe('三域评分策略与适配器（TD2/TD3）', () => {
  it('考试按 isCorrect 判定主观题', () => {
    const strategy = scoringStrategyOf('EXAM');
    const manual = strategy.adapt({ questionId: 7, isCorrect: null, score: 8, questionScore: 10 });
    const auto = strategy.adapt({ questionId: 1, isCorrect: true, score: 2, autoScore: 2, questionScore: 2 });
    expect(manual.kind).toBe('MANUAL');
    expect(manual.key).toBe('7');
    expect(manual.maxScore).toBe(10);
    expect(manual.scoreSource).toBe('TEACHER');
    expect(auto.kind).toBe('AUTO');
    expect(auto.scoreSource).toBe('AUTO');
  });

  it('作业按 scoreSource 分类，并归一化 question/answer 结构', () => {
    const strategy = scoringStrategyOf('HOMEWORK');
    const manual = strategy.adapt({
      question: { id: 11, type: 'SUBJECTIVE', score: 20 },
      answer: { id: 101, score: 12, scoreSource: 'TEACHER', teacherComment: '不错' },
    });
    const accepted = strategy.adapt({
      question: { id: 12, type: 'SINGLE_CHOICE', score: 5 },
      answer: { id: 102, score: 5, scoreSource: 'AUTO_ACCEPTED' },
    });
    const suggested = strategy.adapt({
      question: { id: 13, type: 'SUBJECTIVE', score: 10 },
      answer: { id: 103, suggestedScore: 6, scoreSource: 'SUGGESTED' },
    });
    expect(manual).toMatchObject({ key: '101', kind: 'MANUAL', maxScore: 20, teacherComment: '不错' });
    expect(accepted.kind).toBe('AUTO');
    expect(suggested.kind).toBe('REFERENCE');
  });

  it('实验按 scoreSource 分类，满分取 maxScore ?? stepScore', () => {
    const strategy = scoringStrategyOf('LAB');
    const manual = strategy.adapt({
      answerId: 9,
      score: 7,
      scoreSource: 'TEACHER',
      stepScore: 10,
      questionType: 'SHORT_ANSWER',
    });
    const auto = strategy.adapt({
      id: 10,
      score: 5,
      scoreSource: 'AUTO_ACCEPTED',
      maxScore: 5,
      autoJudgeDetail: 'single choice matched',
    });
    expect(manual).toMatchObject({ key: '9', kind: 'MANUAL', maxScore: 10, questionType: 'SHORT_ANSWER' });
    expect(auto.kind).toBe('AUTO');
    expect(auto.judgeDetail).toBe('single choice matched');
  });

  it('三域只读状态矩阵一致', () => {
    expect(scoringStrategyOf('EXAM').isReadOnly('GRADED')).toBe(true);
    expect(scoringStrategyOf('EXAM').isReadOnly('IN_PROGRESS')).toBe(true);
    expect(scoringStrategyOf('EXAM').isReadOnly('SUBMITTED')).toBe(false);
    expect(scoringStrategyOf('HOMEWORK').isReadOnly('SAVED')).toBe(true);
    expect(scoringStrategyOf('HOMEWORK').isReadOnly('GRADED')).toBe(true);
    expect(scoringStrategyOf('HOMEWORK').isReadOnly('SUBMITTED')).toBe(false);
    expect(scoringStrategyOf('LAB').isReadOnly('SAVED')).toBe(true);
    expect(scoringStrategyOf('LAB').isReadOnly('GRADED')).toBe(true);
    expect(scoringStrategyOf('LAB').isReadOnly('SUBMITTED')).toBe(false);
  });

  it('统一汇总：自动分 + 人工分、参考分不计入、进度按已标记主观题', () => {
    const items: ScoreLineItem[] = [
      { key: '1', kind: 'AUTO', questionType: 'SINGLE_CHOICE', score: 5, autoScore: 5, suggestedScore: null, scoreSource: 'AUTO', judgeDetail: null, maxScore: 5, teacherComment: '' },
      { key: '2', kind: 'MANUAL', questionType: 'SUBJECTIVE', score: 8, autoScore: null, suggestedScore: 6, scoreSource: 'TEACHER', judgeDetail: null, maxScore: 10, teacherComment: '' },
      { key: '3', kind: 'MANUAL', questionType: 'SUBJECTIVE', score: null, autoScore: null, suggestedScore: 4, scoreSource: 'TEACHER', judgeDetail: null, maxScore: 10, teacherComment: '' },
      { key: '4', kind: 'REFERENCE', questionType: 'SUBJECTIVE', score: null, autoScore: null, suggestedScore: 3, scoreSource: 'SUGGESTED', judgeDetail: null, maxScore: 10, teacherComment: '' },
    ];
    const summary = buildScoringSummary('EXAM', items, { scores: { '2': 8, '3': 0 }, marked: ['2'] });
    expect(summary).toEqual({
      autoScore: 5,
      manualScore: 8,
      totalScore: 13,
      completed: 1,
      total: 2,
      percent: 50,
      hasManual: true,
      noManualText: '本卷无主观题，系统已完成判分',
    });
  });

  it('无主观题时进度为 0 且使用域文案', () => {
    const items: ScoreLineItem[] = [
      { key: '1', kind: 'AUTO', questionType: 'SINGLE_CHOICE', score: 2, autoScore: 2, suggestedScore: null, scoreSource: 'AUTO', judgeDetail: null, maxScore: 2, teacherComment: '' },
    ];
    const exam = buildScoringSummary('EXAM', items, { scores: {}, marked: [] });
    const lab = buildScoringSummary('LAB', items, { scores: {}, marked: [] });
    expect(exam.total).toBe(0);
    expect(exam.percent).toBe(0);
    expect(exam.hasManual).toBe(false);
    expect(lab.noManualText).toBe('本卷无需人工评分，系统已完成判分');
  });
});
