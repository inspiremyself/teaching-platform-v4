import request from '@/utils/request';
import type {
  ChangeLabStatusPayload,
  ConfirmLabStepScorePayload,
  CreateLabPayload,
  CreateLabStepPayload,
  GradeLabReportPayload,
  LabAnswerImageMeta,
  LabItem,
  LabReportDetail,
  LabReportItem,
  LabReportQuery,
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
  UpdateLabPayload,
  UpdateLabStepPayload,
} from '@/types/lab';

export const listTeacherLabs = () => request.get<LabItem[]>('/teacher/labs');

export const createTeacherLab = (data: CreateLabPayload) => request.post<LabItem>('/teacher/labs', data);

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

export const listTeacherLabReports = (params?: LabReportQuery) =>
  request.get<LabReportItem[]>('/teacher/lab-reports', { params });

export const getTeacherLabReportDetail = (reportId: number | string) =>
  request.get<LabReportDetail>(`/teacher/lab-reports/${reportId}`);

export const getTeacherLabReportView = (labId: number | string, studentId: number | string) =>
  request.get<TeacherLabReportView>(`/teacher/labs/${labId}/report-view/${studentId}`);

export const gradeTeacherLabReport = (reportId: number | string, data: GradeLabReportPayload) =>
  request.post<void>(`/teacher/lab-reports/${reportId}/grade`, data);

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
