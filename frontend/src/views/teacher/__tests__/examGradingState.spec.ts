import { describe, expect, it } from 'vitest';
import type { ExamAnswerDetail, ExamSubmissionDetail } from '@/types/exam';
import {
  buildGradePayload,
  canSubmitGrade,
  createGradingState,
  gradingProgress,
  hasManualGrading,
  hasUnsavedChanges,
  isMarked,
  isReadOnlySubmission,
  setQuestionScore,
} from '../exams/examGradingState';

const subjectiveAnswer = (questionId: number, score: number | null = null): ExamAnswerDetail => ({
  questionId,
  questionType: 'SHORT',
  stem: '简述题题干',
  optionsJson: null,
  standardAnswer: '参考答案',
  studentAnswer: '学生答案',
  isCorrect: null,
  score,
  teacherComment: null,
  questionScore: 10,
});

const objectiveAnswer = (questionId: number, correct: boolean): ExamAnswerDetail => ({
  questionId,
  questionType: 'SINGLE_CHOICE',
  stem: '单选题题干',
  optionsJson: null,
  standardAnswer: 'A',
  studentAnswer: correct ? 'A' : 'B',
  isCorrect: correct,
  score: correct ? 2 : 0,
  teacherComment: null,
  questionScore: 2,
});

const submissionDetail = (
  status: ExamSubmissionDetail['status'],
  answers: ExamAnswerDetail[],
): ExamSubmissionDetail => ({
  submissionId: 1,
  studentId: 20260001,
  studentName: '演示学生',
  studentUsername: '20260001',
  status,
  autoScore: 4,
  manualScore: null,
  totalScore: null,
  answers,
});

describe('考试批改评分状态（TD1–TD20）', () => {
  it('默认占位 0 不算已评分', () => {
    const snapshot = createGradingState(submissionDetail('SUBMITTED', [subjectiveAnswer(1)]));
    expect(snapshot.gradeMap[1].score).toBeNull();
    expect(gradingProgress(snapshot)).toEqual({ total: 1, completed: 0, percent: 0 });
    expect(canSubmitGrade(snapshot)).toBe(false);
    expect(hasUnsavedChanges(snapshot)).toBe(false);
  });

  it('显式输入 0 算已评分并可提交', () => {
    const snapshot = createGradingState(submissionDetail('SUBMITTED', [subjectiveAnswer(1)]));
    setQuestionScore(snapshot, 1, 0);
    expect(isMarked(snapshot, 1)).toBe(true);
    expect(gradingProgress(snapshot).percent).toBe(100);
    expect(canSubmitGrade(snapshot)).toBe(true);
    expect(buildGradePayload(snapshot).answers).toEqual([
      { questionId: 1, score: 0, teacherComment: undefined },
    ]);
    expect(hasUnsavedChanges(snapshot)).toBe(true);
  });

  it('GRADED 答卷进度为 y/y 且只读', () => {
    const snapshot = createGradingState(
      submissionDetail('GRADED', [subjectiveAnswer(1, 5), subjectiveAnswer(2, 7)]),
    );
    expect(gradingProgress(snapshot)).toEqual({ total: 2, completed: 2, percent: 100 });
    expect(isReadOnlySubmission('GRADED')).toBe(true);
    expect(canSubmitGrade(snapshot)).toBe(false);
    expect(hasUnsavedChanges(snapshot)).toBe(false);
  });

  it('无主观题不返回 100% 进度', () => {
    const snapshot = createGradingState(
      submissionDetail('SUBMITTED', [objectiveAnswer(1, true), objectiveAnswer(2, false)]),
    );
    expect(hasManualGrading(snapshot)).toBe(false);
    expect(gradingProgress(snapshot).total).toBe(0);
    expect(gradingProgress(snapshot).percent).toBe(0);
    expect(canSubmitGrade(snapshot)).toBe(true);
  });
});
