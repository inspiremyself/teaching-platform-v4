import type { AxiosRequestConfig } from 'axios'
import request from '@/utils/request'
import type { ExamListItem, ExamDetail, ExamQuestionView, CreateExamPayload, UpdateExamPayload, SubmitExamPayload, ExamSubmissionListItem, ExamSubmissionDetail, GradeExamPayload } from '@/types/exam'

type MutationResult = Record<string, unknown>
type StartExamResult = { deadlineAt?: string; remainingSeconds?: number }

/** 后端详情按角色返回 type / questionType 等别名，统一填充主字段供页面读取 */
const normalizeExamQuestion = (question: ExamQuestionView): ExamQuestionView => ({
  ...question,
  questionType: question.questionType ?? question.type,
  questionScore: question.questionScore ?? question.score,
  studentAnswer: question.studentAnswer ?? question.answer,
})

const normalizeExamDetail = (detail: ExamDetail): ExamDetail => ({
  ...detail,
  questions: (detail.questions ?? []).map(normalizeExamQuestion),
})

export const listExams = () => request.get<ExamListItem[]>('/exams')
export const getExamDetail = (id: string | number) =>
  request.get<ExamDetail>(`/exams/${id}`).then(normalizeExamDetail)
export const createExam = (data: CreateExamPayload) => request.post<MutationResult>('/exams', data)
export const updateExam = (id: string | number, data: UpdateExamPayload) => request.put<MutationResult>(`/exams/${id}`, data)
export const changeExamStatus = (id: string | number, status: string) => request.post<MutationResult>(`/teacher/exams/${id}/status`, { status })
export const startExam = (id: string | number) => request.post<StartExamResult>(`/exams/${id}/start`)
export const submitExam = (id: string | number, data: SubmitExamPayload, config?: AxiosRequestConfig) => request.post<MutationResult>(`/exams/${id}/submit`, data, config)
export const saveExamDraft = (id: string | number, data: SubmitExamPayload) => request.put<MutationResult>(`/exams/${id}/draft`, data)
export const getExamResults = (id: string | number) => request.get<ExamSubmissionListItem[]>(`/teacher/exams/${id}/results`)
export const getExamSubmissionDetail = (id: string | number) => request.get<ExamSubmissionDetail>(`/teacher/exam-submissions/${id}`)
export const gradeExamSubmission = (id: string | number, data: GradeExamPayload) => request.post<MutationResult>(`/teacher/exam-submissions/${id}/grade`, data)
export const releaseExamScores = (id: string | number) => request.post<MutationResult>(`/teacher/exams/${id}/release-scores`)
