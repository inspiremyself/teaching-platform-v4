import request from '@/utils/request';
import type {
  ChangeLabStatusPayload,
  ConfirmLabStepScorePayload,
  CopyLabPayload,
  CreateLabPayload,
  CreateLabStepPayload,
  GradeLabReportPayload,
  LabAnswerImageMeta,
  LabItem,
  LabItemAnswerWall,
  LabReportDetail,
  LabReportItem,
  LabReportQuery,
  LabSubmissionOverview,
  ReturnLabReportResult,
  StudentLabSubmissionItem,
  TeacherLabReportView,
  LabStepItem,
  SaveLabAnswerPayload,
  SaveLabAnswerResult,
  SaveTeacherLabBlankAcceptedAnswersPayload,
  SaveTeacherLabBlankAcceptedAnswersResult,
  StudentLabDetail,
  StudentLabListItem,
  SubmitLabPayload,
  SubmitLabResult,
  TeacherLabBlankAnswerDistribution,
  TeacherLabBlankItem,
  TeacherLabReportStats,
  UpdateLabPayload,
  UpdateLabStepPayload,
} from '@/types/lab';
import type { PageResult } from '@/types/common';

export const listTeacherLabs = () => request.get<LabItem[]>('/teacher/labs');

export const createTeacherLab = (data: CreateLabPayload) => request.post<LabItem>('/teacher/labs', data);

export const copyTeacherLab = (sourceLabId: number | string, data: CopyLabPayload) =>
  request.post<LabItem>(`/teacher/labs/${sourceLabId}/copy`, data);

export const updateTeacherLab = (id: number | string, data: UpdateLabPayload) => request.put<LabItem>(`/teacher/labs/${id}`, data);

export const changeTeacherLabStatus = (id: number | string, data: ChangeLabStatusPayload) =>
  request.put<LabItem>(`/teacher/labs/${id}/status`, data);

export const listTeacherLabSteps = (labId: number | string) =>
  request.get<LabStepItem[]>(`/teacher/labs/${labId}/steps`);

export const listTeacherLabBlankItems = (labId: number | string) =>
  request.get<TeacherLabBlankItem[]>(`/teacher/labs/${labId}/blank-items`);

export const getTeacherLabBlankAnswerDistribution = (labId: number | string, itemId: number | string) =>
  request.get<TeacherLabBlankAnswerDistribution>(`/teacher/labs/${labId}/blank-items/${itemId}/answer-distribution`);

export const saveTeacherLabBlankAcceptedAnswers = (labId: number | string, data: SaveTeacherLabBlankAcceptedAnswersPayload) =>
  request.post<SaveTeacherLabBlankAcceptedAnswersResult>(`/teacher/labs/${labId}/blank-items/accepted-answers`, data);

export const createTeacherLabStep = (labId: number | string, data: CreateLabStepPayload) =>
  request.post<LabStepItem>(`/teacher/labs/${labId}/steps`, data);

export const updateTeacherLabStep = (stepId: number | string, data: UpdateLabStepPayload) =>
  request.put<LabStepItem>(`/teacher/lab-steps/${stepId}`, data);

export const deleteTeacherLabStep = (stepId: number | string) =>
  request.delete<void>(`/teacher/lab-steps/${stepId}`);

/** 教师端实验报告队列（分页 + 筛选），供队列页使用 */
export const listTeacherLabReports = (params?: LabReportQuery) =>
  request.get<PageResult<LabReportItem>>('/teacher/lab-reports', { params });

/** 教师端实验报告导航队列（全量、无分页），供详情页 / 批量批改页构建上一份下一份队列 */
export const listTeacherLabReportQueue = () => request.get<LabReportItem[]>('/teacher/lab-reports/queue');

/** 教师端实验报告全局统计（全量口径，不受筛选影响） */
export const getTeacherLabReportStats = () => request.get<TeacherLabReportStats>('/teacher/lab-reports/stats');

export const getTeacherLabReportDetail = (reportId: number | string) =>
  request.get<LabReportDetail>(`/teacher/lab-reports/${reportId}`);

export const getTeacherLabReportView = (labId: number | string, studentId: number | string) =>
  request.get<TeacherLabReportView>(`/teacher/labs/${labId}/report-view/${studentId}`);

export const getTeacherLabSubmissionOverview = (labId: number | string) =>
  request.get<LabSubmissionOverview>(`/teacher/labs/${labId}/submission-overview`);

export const listTeacherStudentLabSubmissions = (studentId: number | string) =>
  request.get<StudentLabSubmissionItem[]>(`/teacher/students/${studentId}/lab-submissions`);

export const getTeacherLabItemAnswers = (labId: number | string, itemId: number | string) =>
  request.get<LabItemAnswerWall>(`/teacher/labs/${labId}/items/${itemId}/answers`);

export const gradeTeacherLabReport = (reportId: number | string, data: GradeLabReportPayload) =>
  request.post<void>(`/teacher/lab-reports/${reportId}/grade`, data);

export const returnTeacherLabReport = (reportId: number | string) =>
  request.post<ReturnLabReportResult>(`/teacher/lab-reports/${reportId}/return`);

export const confirmTeacherLabStepScore = (reportId: number | string, data: ConfirmLabStepScorePayload) =>
  request.post<void>(`/teacher/lab-reports/${reportId}/confirm-step-score`, data);

export const releaseTeacherLabScores = (labId: number | string) =>
  request.post<void>(`/teacher/labs/${labId}/release-scores`);

export const listStudentLabs = () => request.get<StudentLabListItem[]>('/student/labs');

export const getStudentLabDetail = (labId: number | string) => request.get<StudentLabDetail>(`/student/labs/${labId}`);

export const saveStudentLabAnswer = (labId: number | string, stepId: number | string, data: SaveLabAnswerPayload) =>
  request.post<SaveLabAnswerResult>(`/student/labs/${labId}/answers/${stepId}`, data);

export const uploadStudentLabAnswerImage = (labId: number | string, stepId: number | string, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<LabAnswerImageMeta>(`/student/labs/${labId}/answers/${stepId}/images`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const submitStudentLab = (labId: number | string, data: SubmitLabPayload) =>
  request.post<SubmitLabResult>(`/student/labs/${labId}/submit`, data);
