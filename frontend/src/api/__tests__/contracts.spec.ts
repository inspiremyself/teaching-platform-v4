import { beforeEach, describe, expect, it, vi } from 'vitest';

const calls = vi.hoisted(() => ({
  urls: [] as string[],
  getResponse: {} as Record<string, unknown>,
}));

vi.mock('@/utils/request', () => {
  const record = (url: string) => {
    calls.urls.push(url);
    return Promise.resolve(calls.getResponse);
  };
  return {
    default: { get: record, post: record, put: record, delete: record },
  };
});

import { createExam, getExamDetail, listExams } from '@/api/exams';
import { addHomeworkQuestionFromBank, listTeacherHomeworks } from '@/api/homeworks';
import { createQuestion, listQuestions, updateQuestion } from '@/api/questions';
import type { CreateExamPayload } from '@/types/exam';
import type { AddHomeworkQuestionFromBankRequest } from '@/types/homework';
import type { SaveQuestionPayload } from '@/types/question';

describe('api 端点契约', () => {
  beforeEach(() => {
    calls.urls.length = 0;
    calls.getResponse = {};
  });

  it('考试端点路径', async () => {
    await listExams();
    await getExamDetail(12);
    await createExam({} as CreateExamPayload);
    expect(calls.urls).toEqual(['/exams', '/exams/12', '/exams']);
  });

  it('考试详情 adapter 统一填充别名字段', async () => {
    calls.getResponse = {
      id: 12,
      questions: [{ questionId: 9, type: 'SINGLE', score: 5, answer: 'A' }],
    };
    const detail = await getExamDetail(12);
    expect(detail.questions[0]).toMatchObject({
      questionType: 'SINGLE',
      questionScore: 5,
      studentAnswer: 'A',
    });
  });

  it('作业端点路径', async () => {
    await listTeacherHomeworks();
    await addHomeworkQuestionFromBank(3, {} as AddHomeworkQuestionFromBankRequest);
    expect(calls.urls).toEqual(['/teacher/homeworks', '/teacher/homeworks/3/questions/bank']);
  });

  it('题库端点路径', async () => {
    await listQuestions();
    await createQuestion({} as SaveQuestionPayload);
    await updateQuestion(9, {} as SaveQuestionPayload);
    expect(calls.urls).toEqual(['/questions', '/questions', '/questions/9']);
  });
});
