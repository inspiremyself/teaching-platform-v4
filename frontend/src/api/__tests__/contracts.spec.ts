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

import {
  changeSubjectStatus,
  createSubject,
  enablePlatformSubject,
  listPlatformSubjects,
  listSubjects,
  updateSubject,
} from '@/api/admin';
import { createExam, getExamDetail, listExams } from '@/api/exams';
import { addHomeworkQuestionFromBank, listTeacherHomeworks } from '@/api/homeworks';
import { createQuestion, listQuestions, updateQuestion } from '@/api/questions';
import type { CreateSubjectPayload, UpdateSubjectPayload } from '@/types/admin';
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

  it('学科端点路径不变', async () => {
    await listSubjects();
    await createSubject({} as CreateSubjectPayload);
    await updateSubject(9, {} as UpdateSubjectPayload);
    await changeSubjectStatus(9, 'ACTIVE');
    expect(calls.urls).toEqual([
      '/admin/subjects',
      '/admin/subjects',
      '/admin/subjects/9',
      '/admin/subjects/9/status',
    ]);
  });

  it('平台学科启用端点路径', async () => {
    await listPlatformSubjects();
    await enablePlatformSubject(7);
    expect(calls.urls).toEqual(['/admin/subjects/platform', '/admin/subjects/platform/7/enable']);
  });

  it('学科映射字段与自建行 null 原样解包', async () => {
    calls.getResponse = {
      id: 3,
      name: '数学',
      code: 'MATH',
      status: 'ACTIVE',
      assignmentCount: 0,
      globalSubjectId: 7,
      globalSubjectCode: 'MATH',
    };
    const mapped = await createSubject({} as CreateSubjectPayload);
    expect(mapped.globalSubjectId).toBe(7);
    expect(mapped.globalSubjectCode).toBe('MATH');

    calls.getResponse = {
      id: 4,
      name: '软件体系结构',
      code: 'SE-ARCH',
      status: 'ACTIVE',
      assignmentCount: 0,
      globalSubjectId: null,
      globalSubjectCode: null,
    };
    const local = await createSubject({} as CreateSubjectPayload);
    expect(local.globalSubjectId).toBeNull();
    expect(local.globalSubjectCode).toBeNull();
    expect(local).toMatchObject({
      id: 4,
      name: '软件体系结构',
      code: 'SE-ARCH',
      status: 'ACTIVE',
      assignmentCount: 0,
    });
  });
});
