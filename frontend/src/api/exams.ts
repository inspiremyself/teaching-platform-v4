import request from '@/utils/request'
import type { ExamListItem, ExamDetail, CreateExamPayload, UpdateExamPayload, SubmitExamPayload, ExamSubmissionListItem, ExamSubmissionDetail, GradeExamPayload, ConfirmExamAnswerPayload } from '@/types/exam'

export const listExams = () => request.get<ExamListItem[]>('/exams')
export const getExamDetail = (id: string | number) => request.get<ExamDetail>(`/exams/${id}`)
export const createExam = (data: CreateExamPayload) => request.post('/exams', data)
export const updateExam = (id: string | number, data: UpdateExamPayload) => request.put(`/exams/${id}`, data)
export const changeExamStatus = (id: string | number, status: string) => request.post(`/teacher/exams/${id}/status`, { status })
export const startExam = (id: string | number) => request.post(`/exams/${id}/start`)
export const submitExam = (id: string | number, data: SubmitExamPayload) => request.post(`/exams/${id}/submit`, data)
export const saveExamDraft = (id: string | number, data: SubmitExamPayload) => request.put(`/exams/${id}/draft`, data)
export const getExamResults = (id: string | number) => request.get<ExamSubmissionListItem[]>(`/teacher/exams/${id}/results`)
export const getExamSubmissionDetail = (id: string | number) => request.get<ExamSubmissionDetail>(`/teacher/exam-submissions/${id}`)
export const gradeExamSubmission = (id: string | number, data: GradeExamPayload) => request.post(`/teacher/exam-submissions/${id}/grade`, data)
export const releaseExamScores = (id: string | number) => request.post(`/teacher/exams/${id}/release-scores`)
export const confirmExamAnswer = (id: string | number, data: ConfirmExamAnswerPayload) => request.post(`/teacher/exam-submissions/${id}/confirm-answer`, data)
