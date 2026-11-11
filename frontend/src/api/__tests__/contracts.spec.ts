import { beforeEach, describe, expect, it, vi } from 'vitest';

const calls = vi.hoisted(() => ({
  urls: [] as string[],
  bodies: [] as unknown[],
  getResponse: {} as Record<string, unknown>,
}));

vi.mock('@/utils/request', () => {
  const record = (url: string, body?: unknown) => {
    calls.urls.push(url);
    if (body !== undefined) {
      calls.bodies.push(body);
    }
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
import { createExam, getExamDetail, listExams, updateExam } from '@/api/exams';
import { addHomeworkQuestionFromBank, createHomework, listTeacherHomeworks, updateHomework } from '@/api/homeworks';
import { createTeacherLab, updateTeacherLab } from '@/api/labs';
import { uploadMaterial } from '@/api/materials';
import { createQuestion, listQuestions, updateQuestion } from '@/api/questions';
import type { CreateSubjectPayload, UpdateSubjectPayload } from '@/types/admin';
import type { CreateExamPayload, UpdateExamPayload } from '@/types/exam';
import type { AddHomeworkQuestionFromBankRequest, CreateHomeworkRequest, UpdateHomeworkRequest } from '@/types/homework';
import type { CreateLabPayload, UpdateLabPayload } from '@/types/lab';
import type { UploadMaterialPayload } from '@/types/material';
import type { CreateQuestionPayload, SaveQuestionPayload } from '@/types/question';
import {
  changePlatformSubjectStatus,
  createPlatformSubject,
  deletePlatformCourseware,
  grantCoursewareSchools,
  listPlatformContentSubjects,
  listPlatformCoursewares,
  publishCourseware,
  revokeCoursewareSchool,
  unpublishCourseware,
  updatePlatformSubject,
} from '@/api/platformContent';
import {
  getTeacherCoursewareVisibility,
  listTeacherCoursewareSections,
  listTeacherCoursewares,
  listTeacherTextbookSections,
  listTeacherTextbooks,
} from '@/api/teacherContent';
import type { CreatePlatformSubjectPayload, UpdatePlatformSubjectPayload } from '@/types/platformContent';

describe('api 端点契约', () => {
  beforeEach(() => {
    calls.urls.length = 0;
    calls.bodies.length = 0;
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
    await createQuestion({} as CreateQuestionPayload);
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

  it('5 处任务 create/update URL 不变且 subjectId 原样透传', async () => {
    const subjectId = 7;
    await createExam({ subjectId } as CreateExamPayload);
    await updateExam(12, { subjectId } as UpdateExamPayload);
    await createHomework({ subjectId } as CreateHomeworkRequest);
    await updateHomework(3, { subjectId } as CreateHomeworkRequest);
    await createTeacherLab({ subjectId } as CreateLabPayload);
    await updateTeacherLab(4, { subjectId } as UpdateLabPayload);
    await createQuestion({ subjectId } as CreateQuestionPayload);
    await updateQuestion(9, { subjectId } as SaveQuestionPayload);
    await uploadMaterial({
      title: '讲义', category: 'COURSE', description: '', visibility: 'ALL', subjectId,
      file: new Blob(['x']),
    } as unknown as UploadMaterialPayload);
    expect(calls.urls).toEqual([
      '/exams', '/exams/12',
      '/teacher/homeworks', '/teacher/homeworks/3',
      '/teacher/labs', '/teacher/labs/4',
      '/questions', '/questions/9',
      '/materials',
    ]);
    expect(calls.bodies.slice(0, 8)).toEqual(
      Array.from({ length: 8 }, () => expect.objectContaining({ subjectId })),
    );
    expect((calls.bodies[8] as FormData).get('subjectId')).toBe('7');
  });

  it('编辑态省略 subjectId 时原样透传、不注入默认值', async () => {
    await updateExam(12, {} as UpdateExamPayload);
    await updateHomework(3, {} as UpdateHomeworkRequest);
    await updateTeacherLab(4, {} as UpdateLabPayload);
    await updateQuestion(9, {} as SaveQuestionPayload);
    expect(calls.urls).toEqual(['/exams/12', '/teacher/homeworks/3', '/teacher/labs/4', '/questions/9']);
    expect(calls.bodies).toEqual([{}, {}, {}, {}]);
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

  it('平台内容端点路径', async () => {
    await listPlatformContentSubjects();
    await createPlatformSubject({} as CreatePlatformSubjectPayload);
    await updatePlatformSubject(3, {} as UpdatePlatformSubjectPayload);
    await changePlatformSubjectStatus(3, 'ACTIVE');
    await listPlatformCoursewares();
    await publishCourseware(5);
    await unpublishCourseware(5);
    await grantCoursewareSchools(5, { schoolIds: [1] });
    await revokeCoursewareSchool(5, 1);
    await deletePlatformCourseware(5);
    expect(calls.urls).toEqual([
      '/admin/platform-content/subjects',
      '/admin/platform-content/subjects',
      '/admin/platform-content/subjects/3',
      '/admin/platform-content/subjects/3/status',
      '/admin/platform-content/coursewares',
      '/admin/platform-content/coursewares/5/publish',
      '/admin/platform-content/coursewares/5/unpublish',
      '/admin/platform-content/coursewares/5/grants',
      '/admin/platform-content/coursewares/5/grants/1',
      '/admin/platform-content/coursewares/5',
    ]);
  });

  it('教师内容端点路径', async () => {
    await listTeacherCoursewares({ filterType: 'ALL' });
    await listTeacherCoursewareSections(5);
    await getTeacherCoursewareVisibility(5);
    await listTeacherTextbooks();
    await listTeacherTextbookSections(3);
    expect(calls.urls).toEqual([
      '/teacher/content/coursewares',
      '/teacher/content/coursewares/5/sections',
      '/teacher/content/coursewares/5/visibility',
      '/teacher/content/textbooks',
      '/teacher/content/textbooks/3/sections',
    ]);
  });
});
